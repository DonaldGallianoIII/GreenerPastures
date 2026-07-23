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
public class ExhibitPenBlockEntity extends BlockEntity
        implements net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity {

    /** §2.2: the disguise state the camouflage baked model reads (null when undisguised). */
    @Override
    public Object getRenderAttachmentData() {
        return getDisguise();
    }

    /** Sync ONLY the disguise to clients (residents/disks stay server-side) - the camouflage model needs it.
     *  A disguise change re-meshes the chunk via the block update in {@link #setDisguise}. */
    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        if (!getDisguiseId().isBlank()) nbt.putString("Disguise", getDisguiseId());
        return nbt;
    }


    private static final int SWEEP_TICKS = 40;

    /** One resident: the written disk exactly as inserted, who donated it, and (transient - never
     *  saved) the live projection's entity UUID. List order = insertion order. */
    static final class Resident {
        final ItemStack disk;
        final UUID inserter;
        UUID projection;   // null until the sweep/insert projects it

        // ── Phase B patrol config (§3): mode + authored path persist; the cursor is transient ──
        PatrolMode mode = PatrolMode.WANDER;
        PatrolPath path = PatrolPath.EMPTY;
        PatrolPath.Progress progress = PatrolPath.EMPTY.start();   // re-derived on reload/re-project

        Resident(ItemStack disk, UUID inserter) {
            this.disk = disk;
            this.inserter = inserter;
        }

        /** Re-arm the cursor to the head of the (possibly changed) path - called whenever mode/path edits. */
        void restartPatrol() {
            this.progress = path.start();
        }
    }

    private final List<Resident> residents = new ArrayList<>();
    private UUID owner;         // placer - may eject anything (spec §0 ownership)
    private String name = "";   // player-given name (Display Suite v2 §2.1); blank → coord default in the directory
    private String disguiseId = "";   // §2.2: block registry id to render AS; blank = undisguised
    private int sweepClock;

    public ExhibitPenBlockEntity(BlockPos pos, BlockState state) {
        super(DisplaySuite.EXHIBIT_PEN_BE, pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    /** The placer UUID (may be null on a legacy/command-placed block) - used to deregister from the owner's
     *  {@link ExhibitStore} directory on break. */
    public UUID getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    /** Species + shiny of each resident, for the Notebook display tab. Read from each disk's
     *  {@code SPECIMEN_SUMMARY} component; an unreadable disk contributes a {@code "?"} entry. */
    public List<com.greenerpastures.specimen.SpecimenSummary> residentSummaries() {
        List<com.greenerpastures.specimen.SpecimenSummary> out = new ArrayList<>();
        for (Resident r : residents) {
            com.greenerpastures.specimen.SpecimenSummary s =
                    r.disk.get(com.greenerpastures.pasture.breeding.GpComponents.SPECIMEN_SUMMARY);
            out.add(s != null ? s : new com.greenerpastures.specimen.SpecimenSummary("?", 0, false, ""));
        }
        return out;
    }

    // ── Phase B patrol config: read + edit surface for the Notebook display tab (§3) ──

    /** A resident's patrol config, flattened for the Notebook display tab. Waypoints are block-local. */
    public record PatrolView(int slot, String mode, int waypointCount,
                             boolean pingPong, int dwellTicks, double speed) {}

    /** One {@link PatrolView} per resident, in slot order - what the display tab renders per mon. */
    public List<PatrolView> patrolViews() {
        List<PatrolView> out = new ArrayList<>();
        for (int i = 0; i < residents.size(); i++) {
            Resident r = residents.get(i);
            out.add(new PatrolView(i, r.mode.name(), r.path.size(),
                    r.path.pingPong(), r.path.dwellTicks(), r.path.speed()));
        }
        return out;
    }

    /** Switch a resident's movement mode (WANDER/PATROL/STATIONARY); re-arms the cursor. No-op off-range. */
    public void setResidentMode(int slot, PatrolMode mode) {
        Resident r = residentAt(slot);
        if (r == null) return;
        r.mode = mode;
        r.restartPatrol();
        markDirty();
        GpLog.d("display", "patrol_mode", "pos", pos.toShortString(), "slot", slot, "mode", mode.name());
    }

    /** Append a block-local waypoint to a resident's path (capped inside {@link PatrolPath}). */
    public void addWaypoint(int slot, RelPos waypoint) {
        Resident r = residentAt(slot);
        if (r == null) return;
        List<RelPos> next = new ArrayList<>(r.path.waypoints());
        next.add(waypoint);
        rebuildPath(r, next, r.path.pingPong(), r.path.dwellTicks(), r.path.speed());
        GpLog.i("display", "patrol_set", "pos", pos.toShortString(), "slot", slot, "waypoints", r.path.size());
    }

    /** Drop one waypoint by index from a resident's path (no-op if either index is off-range). */
    public void removeWaypoint(int slot, int waypointIndex) {
        Resident r = residentAt(slot);
        if (r == null || waypointIndex < 0 || waypointIndex >= r.path.size()) return;
        List<RelPos> next = new ArrayList<>(r.path.waypoints());
        next.remove(waypointIndex);
        rebuildPath(r, next, r.path.pingPong(), r.path.dwellTicks(), r.path.speed());
        GpLog.i("display", "patrol_set", "pos", pos.toShortString(), "slot", slot, "waypoints", r.path.size());
    }

    /** Wipe a resident's whole path (keeps mode - an empty PATROL simply falls back to wander). */
    public void clearWaypoints(int slot) {
        Resident r = residentAt(slot);
        if (r == null) return;
        rebuildPath(r, List.of(), r.path.pingPong(), r.path.dwellTicks(), r.path.speed());
        GpLog.i("display", "patrol_set", "pos", pos.toShortString(), "slot", slot, "waypoints", 0);
    }

    /** Retune a resident's timing without touching its waypoints (loop↔ping-pong, dwell, speed). */
    public void setPatrolTiming(int slot, boolean pingPong, int dwellTicks, double speed) {
        Resident r = residentAt(slot);
        if (r == null) return;
        rebuildPath(r, r.path.waypoints(), pingPong, dwellTicks, speed);
        GpLog.d("display", "patrol_timing", "pos", pos.toShortString(), "slot", slot,
                "pingPong", pingPong, "dwell", r.path.dwellTicks(), "speed", r.path.speed());
    }

    private void rebuildPath(Resident r, List<RelPos> waypoints, boolean pingPong, int dwellTicks, double speed) {
        r.path = new PatrolPath(waypoints, pingPong, dwellTicks, speed);
        r.restartPatrol();
        markDirty();
    }

    private Resident residentAt(int slot) {
        return slot >= 0 && slot < residents.size() ? residents.get(slot) : null;
    }

    /** Set the block's display name (blank clears it back to the coord default). Persists; the RENAME net
     *  action mirrors this into the owner's {@link ExhibitStore} directory so the "My Exhibits" list matches. */
    public void setName(String name) {
        this.name = name == null ? "" : name.strip();
        markDirty();
    }

    /** The registry id of the block this pen renders AS (§2.2), or "" when undisguised. */
    public String getDisguiseId() {
        return disguiseId;
    }

    /** The disguise as a {@link net.minecraft.block.BlockState} (default state), or null when undisguised. */
    public net.minecraft.block.BlockState getDisguise() {
        if (disguiseId.isBlank()) return null;
        net.minecraft.block.Block b = net.minecraft.registry.Registries.BLOCK.get(net.minecraft.util.Identifier.of(disguiseId));
        return b == net.minecraft.block.Blocks.AIR ? null : b.getDefaultState();
    }

    /** Disguise the pen as {@code state}'s block (null → reveal). Persists + re-syncs so the swap shows. */
    public void setDisguise(net.minecraft.block.BlockState state) {
        this.disguiseId = state == null ? "" : net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
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
        be.drivePatrols(sw);                        // §3: every tick - only PATROL/STATIONARY residents do work
        if (++be.sweepClock < SWEEP_TICKS) return;
        be.sweepClock = 0;
        be.sweep(sw);
    }

    /** §3: walk each patrolling resident's projection one step along its {@link PatrolPath}. WANDER residents
     *  and un-projected slots are skipped; an empty PATROL path falls through to Cobblemon's own wander. */
    private void drivePatrols(ServerWorld sw) {
        Vec3d origin = Vec3d.ofBottomCenter(pos.up());
        for (int i = 0; i < residents.size(); i++) {
            Resident r = residents.get(i);
            if (r.mode == PatrolMode.WANDER || r.projection == null) continue;
            Entity e = sw.getEntity(r.projection);
            if (!(e instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity mon)
                    || e.isRemoved() || !CobblemonProjector.isProjection(e)) continue;
            PatrolPath effective = effectivePath(r);
            if (effective.isEmpty()) continue;      // PATROL with no waypoints yet - let it wander
            int before = r.progress.index();
            r.progress = PatrolDriver.drive(origin.x, origin.y, origin.z, mon, effective, r.progress);
            if (r.progress.index() != before) {
                GpLog.d("display", "patrol_step", "pos", pos.toShortString(), "dim", dim(),
                        "slot", i, "index", r.progress.index());
            }
        }
    }

    /** The path actually walked this tick: PATROL uses the authored path; STATIONARY collapses to a single
     *  hold-point (the first waypoint, or the spawn spot if none) so a greeter stands put. */
    private PatrolPath effectivePath(Resident r) {
        if (r.mode == PatrolMode.STATIONARY) {
            RelPos spot = r.path.isEmpty() ? new RelPos(0, 0, 0) : r.path.waypoints().get(0);
            return new PatrolPath(java.util.List.of(spot), false, 0, r.path.speed());
        }
        return r.path;   // PATROL
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
            } else if (resident.mode == PatrolMode.WANDER && entity.squaredDistanceTo(home) > leash * leash) {
                // Patrolling residents own their own bounds (author-clamped waypoints); only stock wander gets leashed home.
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
        if (!name.isBlank()) nbt.putString("Name", name);
        if (!disguiseId.isBlank()) nbt.putString("Disguise", disguiseId);
        NbtList list = new NbtList();
        for (Resident resident : residents) {
            NbtCompound entry = new NbtCompound();
            entry.put("Disk", resident.disk.encode(registries));
            entry.putUuid("By", resident.inserter);
            PatrolNbt.write(entry, resident.mode, resident.path);   // §3: mode + authored path
            list.add(entry);
        }
        nbt.put("Residents", list);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        name = nbt.getString("Name");
        String prevDisguise = disguiseId;
        disguiseId = nbt.getString("Disguise");
        // A disguise change arriving via the BE update packet needs a CLIENT re-mesh - the camouflage baked
        // model only re-reads the render attachment on a chunk rebuild (unlike the statue's live BER).
        if (world != null && world.isClient && !prevDisguise.equals(disguiseId)) {
            world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_ALL);
        }
        residents.clear();
        for (NbtElement element : nbt.getList("Residents", NbtElement.COMPOUND_TYPE)) {
            NbtCompound entry = (NbtCompound) element;
            ItemStack disk = ItemStack.fromNbt(registries, entry.getCompound("Disk")).orElse(ItemStack.EMPTY);
            if (disk.isEmpty() || !entry.containsUuid("By")) continue;
            Resident resident = new Resident(disk, entry.getUuid("By"));
            resident.mode = PatrolNbt.readMode(entry);          // §3: restore patrol config
            resident.path = PatrolNbt.readPath(entry);
            resident.restartPatrol();                           // cursor re-derives from head
            residents.add(resident);
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
