package com.greenerpastures.display;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-saved "My Exhibits" directory (Display Suite v2, §2.1) - every player's placed display blocks
 * ({@link ExhibitRegistry}), keyed by owner UUID. Same per-player, block-free shape as
 * {@code CompressionStore}. This is the authoritative record of WHERE a block is, independent of its
 * disguise (§2.2), so a hidden block is always findable in the Notebook.
 */
public final class ExhibitStore extends PersistentState {
    private static final String ID = "greenerpastures_exhibits";

    private final Map<UUID, ExhibitRegistry> byOwner = new HashMap<>();
    /** Bumped per mutation - gates the Notebook push so a placer's directory refreshes. Not persisted. */
    private long rev = 0;

    public long rev() { return rev; }

    /** The owner's directory (created empty on first touch, never null). */
    public ExhibitRegistry of(UUID owner) {
        return byOwner.computeIfAbsent(owner, u -> new ExhibitRegistry());
    }

    public void register(UUID owner, ExhibitEntry entry) {
        if (owner == null || entry == null) return;
        of(owner).register(entry);
        rev++;
        markDirty();
    }

    public void deregister(UUID owner, String dim, int x, int y, int z) {
        if (owner == null) return;
        ExhibitRegistry r = byOwner.get(owner);
        if (r != null && r.removeAt(dim, x, y, z) != null) { rev++; markDirty(); }
    }

    public void rename(UUID owner, String dim, int x, int y, int z, String name) {
        if (owner == null) return;
        ExhibitRegistry r = byOwner.get(owner);
        if (r != null && r.rename(dim, x, y, z, name) != null) { rev++; markDirty(); }
    }

    /** Build a directory entry for a block at {@code pos} in {@code world} - the dimension id is derived here. */
    public static ExhibitEntry entryFor(World world, BlockPos pos, String type, String name) {
        return new ExhibitEntry(world.getRegistryKey().getValue().toString(),
                pos.getX(), pos.getY(), pos.getZ(), type, name);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound owners = new NbtCompound();
        byOwner.forEach((u, reg) -> {
            NbtList list = new NbtList();
            for (ExhibitEntry e : reg.snapshot()) list.add(entryToNbt(e));
            if (!list.isEmpty()) owners.put(u.toString(), list);
        });
        nbt.put("owners", owners);
        return nbt;
    }

    private static ExhibitStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ExhibitStore s = new ExhibitStore();
        NbtCompound owners = nbt.getCompound("owners");
        for (String k : owners.getKeys()) {
            try {
                NbtList list = owners.getList(k, NbtElement.COMPOUND_TYPE);
                List<ExhibitEntry> entries = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) entries.add(entryFromNbt(list.getCompound(i)));
                s.byOwner.put(UUID.fromString(k), ExhibitRegistry.fromSnapshot(entries));
            } catch (Exception e) {
                com.greenerpastures.core.GpLog.w("display", "registry_load_skip", "key", k, "err", String.valueOf(e));
            }
        }
        return s;
    }

    private static NbtCompound entryToNbt(ExhibitEntry e) {
        NbtCompound c = new NbtCompound();
        c.putString("dim", e.dimension());
        c.putInt("x", e.x());
        c.putInt("y", e.y());
        c.putInt("z", e.z());
        c.putString("type", e.type());
        c.putString("name", e.name());
        return c;
    }

    private static ExhibitEntry entryFromNbt(NbtCompound c) {
        return new ExhibitEntry(c.getString("dim"), c.getInt("x"), c.getInt("y"), c.getInt("z"),
                c.getString("type"), c.getString("name"));
    }

    private static final Type<ExhibitStore> TYPE = new Type<>(ExhibitStore::new, ExhibitStore::fromNbt, null);

    public static ExhibitStore get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
