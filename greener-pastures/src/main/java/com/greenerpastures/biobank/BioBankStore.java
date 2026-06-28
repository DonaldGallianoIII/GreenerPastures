package com.greenerpastures.biobank;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * World-saved store of every {@link BioBankData}, keyed by dimension + block position (mirrors
 * {@code PastureRegistry}). The eggs live here — in the per-world save — rather than in the block
 * entity's chunk NBT, so a BioBank can hold many eggs without bloating chunk saves.
 */
public final class BioBankStore extends PersistentState {
    private static final String ID = "greenerpastures_biobanks";

    private final Map<String, BioBankData> data = new HashMap<>();

    private static String key(World world, BlockPos pos) {
        return world.getRegistryKey().getValue() + "|" + pos.asLong();
    }

    public BioBankData get(World world, BlockPos pos) {
        return data.get(key(world, pos));
    }

    public BioBankData getOrCreate(World world, BlockPos pos) {
        return data.computeIfAbsent(key(world, pos), k -> { markDirty(); return new BioBankData(); });
    }

    public void remove(World world, BlockPos pos) {
        if (data.remove(key(world, pos)) != null) markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound map = new NbtCompound();
        data.forEach((k, v) -> map.put(k, v.writeNbt(new NbtCompound(), lookup)));
        nbt.put("banks", map);
        return nbt;
    }

    private static BioBankStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        BioBankStore s = new BioBankStore();
        NbtCompound map = nbt.getCompound("banks");
        for (String k : map.getKeys()) s.data.put(k, BioBankData.fromNbt(map.getCompound(k), lookup));
        return s;
    }

    private static final Type<BioBankStore> TYPE = new Type<>(BioBankStore::new, BioBankStore::fromNbt, null);

    public static BioBankStore get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
