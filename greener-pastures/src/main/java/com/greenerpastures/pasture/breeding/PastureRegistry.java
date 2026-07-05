package com.greenerpastures.pasture.breeding;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * World-saved registry of {@link PastureData}, indexed <b>dimension → position</b>. The per-dimension
 * nesting makes the breeder's per-tick {@link #inWorld} an O(1) sub-map lookup - no map rebuild, no key
 * parsing on the hot path (perf-audit H1). The on-disk format stays the flat {@code dim|pos.asLong()}
 * key map, so existing saves load unchanged (parsed once on load, flattened once on save).
 */
public final class PastureRegistry extends PersistentState {
    private static final String ID = "greenerpastures_pastures";

    private final Map<String, Map<BlockPos, PastureData>> byDim = new HashMap<>();

    /** Cache the dim-key string per world so the per-tick {@link #inWorld}/{@link #get} lookups don't allocate a
     *  fresh Identifier string every call (perf-audit; identity-keyed, bounded by loaded-world count).
     *  WEAK keys (R3 #4): a strong static Map&lt;World,…&gt; pins every ServerWorld ever loaded - in singleplayer the
     *  integrated server restarts per world visited, so an evening of world-hopping leaked whole world graphs.
     *  Weak keys let GC reclaim closed worlds; a miss just recomputes one string. */
    private static final Map<World, String> DIM_KEY =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static String dimKey(World world) {
        return DIM_KEY.computeIfAbsent(world, w -> w.getRegistryKey().getValue().toString());
    }

    public PastureData get(World world, BlockPos pos) {
        Map<BlockPos, PastureData> m = byDim.get(dimKey(world));
        return m == null ? null : m.get(pos);
    }

    /** Lookup by the dim-key STRING (a {@code PastureSnapshot.dim}) - the Pastures-tab health pass reads
     *  registry-side state (owner/Kernel/tray) for pastures whose world/chunk may not be loaded. */
    public PastureData get(String dimKey, BlockPos pos) {
        Map<BlockPos, PastureData> m = byDim.get(dimKey);
        return m == null ? null : m.get(pos);
    }

    public PastureData getOrCreate(World world, BlockPos pos) {
        Map<BlockPos, PastureData> m = byDim.computeIfAbsent(dimKey(world), k -> new HashMap<>());
        PastureData d = m.get(pos);
        if (d == null) {
            d = new PastureData();
            d.upgrades.addListener(inv -> markDirty());
            m.put(pos, d);
            markDirty();
        }
        return d;
    }

    /** Drop a pasture record (e.g. when its block is gone) so it doesn't linger forever. */
    public void remove(World world, BlockPos pos) {
        Map<BlockPos, PastureData> m = byDim.get(dimKey(world));
        if (m != null && m.remove(pos) != null) markDirty();
    }

    /** Set a pasture's display name (creating the record if needed) and persist. */
    public void setName(World world, BlockPos pos, String name) {
        getOrCreate(world, pos).name = name;
        markDirty();
    }

    /**
     * Replace a pasture's pair assignments (tethering id -> bucket) and persist. Only positive bucket
     * numbers are kept; 0/negative means "unpaired" and is dropped.
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

    /** Every pasture record in the given world - a live O(1) sub-map view (empty if the dim has none). */
    public Map<BlockPos, PastureData> inWorld(World world) {
        return byDim.getOrDefault(dimKey(world), Collections.emptyMap());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound map = new NbtCompound();
        byDim.forEach((dim, m) -> m.forEach((pos, v) ->
                map.put(dim + "|" + pos.asLong(), v.writeNbt(new NbtCompound(), lookup))));
        nbt.put("pastures", map);
        return nbt;
    }

    private static PastureRegistry fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        PastureRegistry r = new PastureRegistry();
        NbtCompound map = nbt.getCompound("pastures");
        for (String k : map.getKeys()) {
            int i = k.indexOf('|');
            if (i <= 0) continue;
            try {
                String dim = k.substring(0, i);
                BlockPos pos = BlockPos.fromLong(Long.parseLong(k.substring(i + 1)));
                PastureData d = PastureData.fromNbt(map.getCompound(k), lookup);
                d.upgrades.addListener(inv -> r.markDirty());
                r.byDim.computeIfAbsent(dim, x -> new HashMap<>()).put(pos, d);
            } catch (NumberFormatException ignored) {
                // skip a malformed key
            }
        }
        return r;
    }

    private static final Type<PastureRegistry> TYPE = new Type<>(PastureRegistry::new, PastureRegistry::fromNbt, null);

    public static PastureRegistry get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
