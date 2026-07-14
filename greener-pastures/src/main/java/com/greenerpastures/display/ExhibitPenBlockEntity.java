package com.greenerpastures.display;

import com.greenerpastures.core.GpLog;
import com.greenerpastures.pasture.breeding.GpComponents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The <b>Exhibit Pen</b>'s state + projection lifecycle (spec §1). Holds up to
 * {@link DisplaySuite#EXHIBIT_SLOTS} written specimen disks in insertion order; each stored disk gets a
 * roaming {@code PokemonEntity} projection beside the block. The disks and their inserters persist in
 * NBT; the projections NEVER do - they are re-derived from the disks by the sweep, so a crash or unload
 * can only ever under-produce, never duplicate (the projection principle, spec §0).
 *
 * <p>Sweep (every {@value #SWEEP_TICKS} ticks): respawn a slot's projection if it went missing
 * (chunk cycled, /kill, despawn edge case), and walk home any resident that strayed past Cobblemon's
 * pasture wander distance. Belt-and-suspenders by design - the steady state is self-healing.
 */
public class ExhibitPenBlockEntity extends BlockEntity {

    private static final int SWEEP_TICKS = 40;

    /** One resident: the written disk exactly as inserted, who donated it, and (transient - never
     *  saved) the live projection's entity UUID. List order = insertion order. */
    static final class Resident {
        final ItemStack disk;
        final UUID inserter;
        UUID projection;   // null until the sweep/insert projects it

        Resident(ItemStack disk, UUID inserter) {
            this.disk = disk;
            this.inserter = inserter;
        }
    }

    private final List<Resident> residents = new ArrayList<>();
    private UUID owner;         // placer - may eject anything (spec §0 ownership)
    private int sweepClock;

    public ExhibitPenBlockEntity(BlockPos pos, BlockState state) {
        super(DisplaySuite.EXHIBIT_PEN_BE, pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    // ── player interactions (called by ExhibitPenBlock, server side only) ──

    /** Right-click with a specimen disk: gate through {@link ExhibitRules}, store ONE disk, project. */
    public void tryInsert(ServerPlayerEntity player, ItemStack held) {
        if (!(world instanceof ServerWorld sw)) return;
        NbtCompound specimen = held.get(GpComponents.SPECIMEN);
        boolean glitch = false;
        String species = "?";
        boolean shiny = false;
        if (specimen != null) {
            CobblemonProjector.DiskPeek peek = CobblemonProjector.peek(sw, specimen);
            if (!peek.loads()) {
                refuse(player, "The specimen would not decompress - disk kept.");
                return;
            }
            glitch = peek.glitch();
            species = peek.species();
            shiny = peek.shiny();
        }
        String err = ExhibitRules.insertRefusal(specimen != null, glitch, residents.size(), DisplaySuite.EXHIBIT_SLOTS);
        if (err != null) {
            refuse(player, err);
            return;
        }

        ItemStack stored = held.copyWithCount(1);
        held.decrement(1);
        Resident resident = new Resident(stored, player.getUuid());
        resident.projection = CobblemonProjector.project(sw, pos, specimen);
        residents.add(resident);
        markDirty();

        player.sendMessage(Text.literal("§a[Greener Pastures]§r " + cap(species) + (shiny ? " ✨" : "")
                + " joins the exhibit (" + residents.size() + "/" + DisplaySuite.EXHIBIT_SLOTS + ")."), false);
        GpLog.i("display", "exhibit_insert", "pos", pos.toShortString(), "dim", dim(),
                "slot", residents.size() - 1, "by", player.getUuid().toString(),
                "species", species, "shiny", shiny);
        if (resident.projection != null) {
            GpLog.d("display", "exhibit_project", "pos", pos.toShortString(), "dim", dim(),
                    "entity", resident.projection.toString(), "species", species);
        }
    }

    /** Sneak-right-click, empty hand: eject the newest disk the requester may take (spec §1). */
    public void tryEject(ServerPlayerEntity player) {
        List<UUID> inserters = residents.stream().map(r -> r.inserter).toList();
        int index = ExhibitRules.ejectIndex(inserters, player.getUuid(), owner);
        String err = ExhibitRules.ejectRefusal(residents.size(), index >= 0);
        if (err != null) {
            refuse(player, err);
            return;
        }
        Resident resident = residents.remove(index);
        discardProjection(resident);
        player.getInventory().offerOrDrop(resident.disk);
        markDirty();
        player.sendMessage(Text.literal("§a[Greener Pastures]§r Disk returned ("
                + residents.size() + "/" + DisplaySuite.EXHIBIT_SLOTS + " remain)."), false);
        GpLog.i("display", "exhibit_eject", "pos", pos.toShortString(), "dim", dim(),
                "slot", index, "by", player.getUuid().toString());
    }

    /** Block broken/replaced: every projection dies instantly, every disk pops as an item (spec §1). */
    public void onBroken() {
        for (Resident resident : residents) discardProjection(resident);
        if (world != null) {
            for (Resident resident : residents) {
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, resident.disk);
            }
        }
        GpLog.i("display", "exhibit_broken", "pos", pos.toShortString(), "dim", dim(),
                "disks", residents.size());
        residents.clear();
    }

    // ── the sweep ──

    public static void serverTick(World world, BlockPos pos, BlockState state, ExhibitPenBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) return;
        if (++be.sweepClock < SWEEP_TICKS) return;
        be.sweepClock = 0;
        be.sweep(sw);
    }

    private void sweep(ServerWorld sw) {
        double leash = CobblemonProjector.wanderDistance();
        Vec3d home = Vec3d.ofBottomCenter(pos.up());
        for (Resident resident : residents) {
            Entity entity = resident.projection == null ? null : sw.getEntity(resident.projection);
            if (entity == null || entity.isRemoved() || !CobblemonProjector.isProjection(entity)) {
                NbtCompound specimen = resident.disk.get(GpComponents.SPECIMEN);
                if (specimen == null) continue;   // never happens (gated at insert); refuse to guess
                resident.projection = CobblemonProjector.project(sw, pos, specimen);
                if (resident.projection != null) {
                    GpLog.d("display", "exhibit_project", "pos", pos.toShortString(), "dim", dim(),
                            "entity", resident.projection.toString(), "reason", "sweep_respawn");
                }
            } else if (entity.squaredDistanceTo(home) > leash * leash) {
                entity.refreshPositionAndAngles(home.x, home.y, home.z, entity.getYaw(), 0f);
                GpLog.d("display", "exhibit_leash", "pos", pos.toShortString(), "dim", dim(),
                        "entity", entity.getUuidAsString());
            }
        }
    }

    private void discardProjection(Resident resident) {
        if (resident.projection == null || !(world instanceof ServerWorld sw)) return;
        Entity entity = sw.getEntity(resident.projection);
        if (entity != null && CobblemonProjector.isProjection(entity)) {
            entity.discard();
            GpLog.d("display", "exhibit_discard", "pos", pos.toShortString(), "dim", dim(),
                    "entity", resident.projection.toString());
        }
        resident.projection = null;
    }

    // ── persistence: disks + inserters + owner; projections NEVER (spec §0) ──

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (owner != null) nbt.putUuid("Owner", owner);
        NbtList list = new NbtList();
        for (Resident resident : residents) {
            NbtCompound entry = new NbtCompound();
            entry.put("Disk", resident.disk.encode(registries));
            entry.putUuid("By", resident.inserter);
            list.add(entry);
        }
        nbt.put("Residents", list);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        residents.clear();
        for (NbtElement element : nbt.getList("Residents", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            ItemStack disk = ItemStack.fromNbt(registries, entry.getCompound("Disk")).orElse(ItemStack.EMPTY);
            if (disk.isEmpty() || !entry.containsUuid("By")) continue;
            residents.add(new Resident(disk, entry.getUuid("By")));
        }
        // projections intentionally not restored - the next sweep re-derives them from the disks
    }

    private void refuse(ServerPlayerEntity player, String reason) {
        player.sendMessage(Text.literal("§c[Greener Pastures]§r " + reason), false);
        GpLog.d("display", "exhibit_refused", "pos", pos.toShortString(), "dim", dim(),
                "by", player.getUuid().toString(), "reason", reason);
    }

    private String dim() {
        return world == null ? "?" : world.getRegistryKey().getValue().toString();
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "?" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
