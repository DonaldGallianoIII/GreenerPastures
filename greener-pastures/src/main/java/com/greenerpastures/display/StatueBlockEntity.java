package com.greenerpastures.display;

import com.greenerpastures.core.GpLog;
import com.greenerpastures.pasture.breeding.GpComponents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The <b>Specimen Statue</b>'s state (spec §2): the written disk (server-side NBT only - a player's
 * full mon data never leaves the server), the cosmetics-only {@link RenderSpec}, and the
 * {@link StatueTransform} pose. Zero lifecycle - no entity exists anywhere; the client BER re-renders
 * from the synced spec whenever the chunk is visible. Persistence is trivial by design.
 */
public class StatueBlockEntity extends BlockEntity
        implements net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity {

    /** §2.2: the disguise state the camouflage baked model reads (null when undisguised). */
    @Override
    public Object getRenderAttachmentData() {
        return getDisguise();
    }


    private ItemStack disk = ItemStack.EMPTY;   // never synced - see toInitialChunkDataNbt
    private UUID owner;
    private String name = "";   // player-given name (Display Suite v2 §2.1); blank → coord default
    private String disguiseId = "";   // §2.2: block registry id to render the plinth AS; blank = undisguised
    private UUID inserter;
    private RenderSpec spec = RenderSpec.EMPTY;
    private StatueTransform transform = StatueTransform.DEFAULT;
    private StatueTransform.Axis nudgeAxis = StatueTransform.Axis.X;

    public StatueBlockEntity(BlockPos pos, BlockState state) {
        super(DisplaySuite.SPECIMEN_STATUE_BE, pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    /** The placer UUID - used to deregister from the owner's {@link ExhibitStore} directory on break. */
    public UUID getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    /** Set the display name (blank clears to the coord default). The RENAME net action mirrors it into the
     *  owner's {@link ExhibitStore} directory. */
    public void setName(String name) {
        this.name = name == null ? "" : name.strip();
        markDirty();
    }

    /** The registry id of the block the plinth renders AS (§2.2), or "" when undisguised. */
    public String getDisguiseId() {
        return disguiseId;
    }

    /** The disguise as a {@link net.minecraft.block.BlockState} (default state), or null when undisguised. */
    public net.minecraft.block.BlockState getDisguise() {
        if (disguiseId.isBlank()) return null;
        net.minecraft.block.Block b = net.minecraft.registry.Registries.BLOCK.get(net.minecraft.util.Identifier.of(disguiseId));
        return b == net.minecraft.block.Blocks.AIR ? null : b.getDefaultState();
    }

    /** Disguise the plinth as {@code state}'s block (null → reveal). Persists + re-syncs. */
    public void setDisguise(net.minecraft.block.BlockState state) {
        this.disguiseId = state == null ? "" : net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
        markDirty();
        if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    public RenderSpec renderSpec() {
        return spec;
    }

    public StatueTransform transform() {
        return transform;
    }

    public boolean hasStatue() {
        return !spec.isEmpty();
    }

    // ── player interactions (StatueBlock routes here, server side only) ──

    /** Right-click with a written specimen disk: the plinth takes the disk, the statue appears. */
    public void tryInsert(ServerPlayerEntity player, ItemStack held) {
        if (!(world instanceof ServerWorld sw)) return;
        if (hasStatue()) {
            refuse(player, "This plinth is occupied - take the current statue first.");
            return;
        }
        NbtCompound specimen = held.get(GpComponents.SPECIMEN);
        if (specimen == null) {
            refuse(player, "This disk is blank - archive a party mon from the Notebook's Specimens tab.");
            return;
        }
        RenderSpec extracted = CobblemonProjector.renderSpec(sw, specimen);
        if (extracted.isEmpty()) {
            refuse(player, "The specimen would not decompress - disk kept.");
            return;
        }
        disk = held.copyWithCount(1);
        held.decrement(1);
        inserter = player.getUuid();
        spec = extracted;
        transform = StatueTransform.DEFAULT;
        sync();
        player.sendMessage(Text.literal("§a[Greener Pastures]§r " + cap(spec.species())
                + (spec.shiny() ? " ✨" : "") + " set in stone. Right-click: rotate · sneak: scale"
                + " · stick: nudge · shears: reclaim the disk."), false);
        GpLog.i("display", "statue_insert", "pos", pos.toShortString(), "dim", dim(),
                "by", player.getUuid().toString(), "species", spec.species(), "shiny", spec.shiny());
    }

    /** Shears: statue vanishes, the WRITTEN disk returns exactly as inserted (spec §0). */
    public void tryEject(ServerPlayerEntity player) {
        if (!hasStatue()) {
            refuse(player, "This plinth is empty.");
            return;
        }
        if (!ExhibitRules.canTake(player.getUuid(), inserter, owner)) {
            refuse(player, "Only the donor or the plinth's owner can take this disk.");
            return;
        }
        player.getInventory().offerOrDrop(disk);
        clearStatue();
        sync();
        player.sendMessage(Text.literal("§a[Greener Pastures]§r Disk returned."), false);
        GpLog.i("display", "statue_eject", "pos", pos.toShortString(), "dim", dim(),
                "by", player.getUuid().toString());
    }

    /** The v1 adjustment surface (bindings to feel-pass with Deuce, spec §5 Q2). */
    public enum Adjust { ROTATE, SCALE, NUDGE, AXIS }

    public void tryAdjust(ServerPlayerEntity player, Adjust kind) {
        if (!hasStatue()) {
            refuse(player, "This plinth is empty.");
            return;
        }
        if (!ExhibitRules.canTake(player.getUuid(), inserter, owner)) {
            refuse(player, "Only the donor or the plinth's owner can adjust this statue.");
            return;
        }
        String detail;
        switch (kind) {
            case ROTATE -> {
                transform = transform.rotated();
                detail = transform.yawDegrees() + "°";
            }
            case SCALE -> {
                transform = transform.scaleCycled(DisplaySuite.STATUE_MAX_SCALE);
                detail = transform.scale() + "×";
            }
            case NUDGE -> {
                transform = nudgeSawtooth();
                detail = nudgeAxis + " " + String.format(java.util.Locale.ROOT, "%.4f", offsetOn(nudgeAxis));
            }
            case AXIS -> {
                nudgeAxis = StatueTransform.Axis.values()[(nudgeAxis.ordinal() + 1) % 3];
                detail = "nudging " + nudgeAxis + " now";
            }
            default -> detail = "?";
        }
        sync();
        player.sendMessage(Text.literal("§7[Greener Pastures]§r " + detail), true);   // action bar, not chat spam
        GpLog.d("display", "statue_adjust", "pos", pos.toShortString(), "dim", dim(),
                "by", player.getUuid().toString(), "kind", kind.name(),
                "yaw", transform.yawDegrees(), "scale", transform.scale(),
                "x", transform.offsetX(), "y", transform.offsetY(), "z", transform.offsetZ());
    }

    /** One stick tap walks the current axis in 1/16 steps; at the clamp it snaps to the far end, so a
     *  single button covers the whole range (v1-crude, spec §2 - feel-pass replaces this if it grates). */
    private StatueTransform nudgeSawtooth() {
        StatueTransform nudged = transform.nudged(nudgeAxis, 1);
        if (nudged.equals(transform)) {   // clamped: wrap to the far end
            double max = nudgeAxis == StatueTransform.Axis.Y
                    ? StatueTransform.MAX_VERTICAL : StatueTransform.MAX_HORIZONTAL;
            return switch (nudgeAxis) {
                case X -> new StatueTransform(transform.yawStep(), -max, transform.offsetY(), transform.offsetZ(), transform.scaleIndex());
                case Y -> new StatueTransform(transform.yawStep(), transform.offsetX(), -max, transform.offsetZ(), transform.scaleIndex());
                case Z -> new StatueTransform(transform.yawStep(), transform.offsetX(), transform.offsetY(), -max, transform.scaleIndex());
            };
        }
        return nudged;
    }

    private double offsetOn(StatueTransform.Axis axis) {
        return switch (axis) {
            case X -> transform.offsetX();
            case Y -> transform.offsetY();
            case Z -> transform.offsetZ();
        };
    }

    /** Block broken: the disk pops as an item; the statue was only ever a rendering. */
    public void onBroken() {
        if (world != null && !disk.isEmpty()) {
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, disk);
            GpLog.i("display", "statue_broken", "pos", pos.toShortString(), "dim", dim());
        }
        clearStatue();
    }

    private void clearStatue() {
        disk = ItemStack.EMPTY;
        inserter = null;
        spec = RenderSpec.EMPTY;
        transform = StatueTransform.DEFAULT;
    }

    // ── persistence + sync: the disk stays server-side; clients get cosmetics + pose only (spec §2) ──

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (!disk.isEmpty()) nbt.put("Disk", disk.encode(registries));
        if (owner != null) nbt.putUuid("Owner", owner);
        if (!name.isBlank()) nbt.putString("Name", name);
        if (!disguiseId.isBlank()) nbt.putString("Disguise", disguiseId);
        if (inserter != null) nbt.putUuid("Inserter", inserter);
        nbt.putInt("NudgeAxis", nudgeAxis.ordinal());
        writeClientNbt(nbt);
    }

    /** The complete client-visible surface: {@link RenderSpec#KEYS} + the pose. Nothing else. */
    private void writeClientNbt(NbtCompound nbt) {
        NbtCompound specNbt = new NbtCompound();
        for (Map.Entry<String, String> entry : spec.toMap().entrySet()) {
            specNbt.putString(entry.getKey(), entry.getValue());
        }
        nbt.put("Spec", specNbt);
        nbt.putInt("Yaw", transform.yawStep());
        nbt.putDouble("OffX", transform.offsetX());
        nbt.putDouble("OffY", transform.offsetY());
        nbt.putDouble("OffZ", transform.offsetZ());
        nbt.putInt("Scale", transform.scaleIndex());
        if (!disguiseId.isBlank()) nbt.putString("Disguise", disguiseId);   // §2.2: sync to the camouflage model
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("Disk")) {
            disk = ItemStack.fromNbt(registries, nbt.getCompound("Disk")).orElse(ItemStack.EMPTY);
        }
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");
        name = nbt.getString("Name");
        String prevDisguise = disguiseId;
        disguiseId = nbt.getString("Disguise");
        // Client re-mesh on a disguise change - the wrapped plinth baked model only re-reads its render
        // attachment on a chunk rebuild (the BER handles the mon, but not the block model swap).
        if (world != null && world.isClient && !prevDisguise.equals(disguiseId)) {
            world.updateListeners(pos, getCachedState(), getCachedState(), net.minecraft.block.Block.NOTIFY_ALL);
        }
        if (nbt.containsUuid("Inserter")) inserter = nbt.getUuid("Inserter");
        if (nbt.contains("NudgeAxis")) {
            nudgeAxis = StatueTransform.Axis.values()[Math.floorMod(nbt.getInt("NudgeAxis"), 3)];
        }
        Map<String, String> map = new LinkedHashMap<>();
        NbtCompound specNbt = nbt.getCompound("Spec");
        for (String key : RenderSpec.KEYS) {
            if (specNbt.contains(key)) map.put(key, specNbt.getString(key));
        }
        spec = RenderSpec.fromMap(map);
        transform = new StatueTransform(nbt.getInt("Yaw"), nbt.getDouble("OffX"),
                nbt.getDouble("OffY"), nbt.getDouble("OffZ"), nbt.getInt("Scale"));
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        writeClientNbt(nbt);   // spec + pose ONLY - never the disk (IVs/OT/moves stay server-side)
        return nbt;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    private void sync() {
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 0);
        }
    }

    private void refuse(ServerPlayerEntity player, String reason) {
        player.sendMessage(Text.literal("§c[Greener Pastures]§r " + reason), false);
        GpLog.d("display", "statue_refused", "pos", pos.toShortString(), "dim", dim(),
                "by", player.getUuid().toString(), "reason", reason);
    }

    private String dim() {
        return world == null ? "?" : world.getRegistryKey().getValue().toString();
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "?" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
