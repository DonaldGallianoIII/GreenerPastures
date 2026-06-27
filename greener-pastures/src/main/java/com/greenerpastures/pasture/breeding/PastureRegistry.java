package com.greenerpastures.pasture.breeding;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * World-saved registry of {@link PastureData} keyed by dimension + position. Pasture-owned, shared,
 * persistent. The wand GUI, breeding engine, fill-check, and dashboards all read/write through here.
 */
public final class PastureRegistry extends PersistentState {
    private static final String ID = "greenerpastures_pastures";

    private final Map<String, PastureData> data = new HashMap<>();

    private static String key(World world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "|" + pos.asLong();
    }

    public PastureData get(World world, BlockPos pos) {
        return data.get(key(world, pos));
    }

    public PastureData getOrCreate(World world, BlockPos pos) {
        String k = key(world, pos);
        PastureData d = data.get(k);
        if (d == null) {
            d = new PastureData();
            d.upgrades.addListener(inv -> markDirty());   // persist when upgrades change
            data.put(k, d);
            markDirty();
        }
        return d;
    }

    /** Set a pasture's display name (creating the record if needed) and persist. */
    public void setName(World world, BlockPos pos, String name) {
        getOrCreate(world, pos).name = name;
        markDirty();
    }

    /**
     * Replace a pasture's pair assignments (tethering id -> bucket) and persist. Only positive bucket
     * numbers are kept; 0/negative means "unpaired" and is dropped. The {@code pairings} map has no
     * change listener (unlike {@code upgrades}), so this is the path that marks the registry dirty.
     */
    public void setPairings(World world, BlockPos pos, Map<java.util.UUID, Integer> pairings) {
        PastureData d = getOrCreate(world, pos);
        d.pairings.clear();
        if (pairings != null) {
            pairings.forEach((id, bucket) -> {
                if (id != null && bucket != null && bucket > 0) d.pairings.put(id, bucket);
            });
        }
        markDirty();
    }

    /** Every pasture record in the given world (dimension), as position -> data. */
    public Map<BlockPos, PastureData> inWorld(World world) {
        String dim = world.getRegistryKey().getValue().toString();
        Map<BlockPos, PastureData> out = new HashMap<>();
        data.forEach((k, v) -> {
            int i = k.indexOf('|');
            if (i > 0 && k.substring(0, i).equals(dim)) {
                out.put(BlockPos.fromLong(Long.parseLong(k.substring(i + 1))), v);
            }
        });
        return out;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound map = new NbtCompound();
        data.forEach((k, v) -> map.put(k, v.writeNbt(new NbtCompound(), lookup)));
        nbt.put("pastures", map);
        return nbt;
    }

    private static PastureRegistry fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        PastureRegistry r = new PastureRegistry();
        NbtCompound map = nbt.getCompound("pastures");
        for (String k : map.getKeys()) {
            PastureData d = PastureData.fromNbt(map.getCompound(k), lookup);
            d.upgrades.addListener(inv -> r.markDirty());
            r.data.put(k, d);
        }
        return r;
    }

    private static final Type<PastureRegistry> TYPE = new Type<>(PastureRegistry::new, PastureRegistry::fromNbt, null);

    public static PastureRegistry get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
