package com.greenerpastures.biobank;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-saved store of every player's {@link CompressionLedger}, keyed by player UUID - the same per-player
 * block-free shape as {@link BioBankStore}. Tiny data (species → long), so no encoded-NBT caching needed:
 * a full rewrite per save is a few dozen longs even for a serious presser.
 */
public final class CompressionStore extends PersistentState {
    private static final String ID = "greenerpastures_compression";

    private final Map<UUID, CompressionLedger> ledgers = new HashMap<>();
    /** The communal pool (Deuce, 2026-07-19): anyone donates, everyone's pastures benefit - a "more"
     *  multiplier the harvest multiplies ON TOP of each owner's personal ledger. */
    private CompressionLedger serverLedger = CompressionLedger.server();
    /** Bumped on every mutation - folded into the biobank push's rev gate so OTHER viewers' consoles pick
     *  up a communal tier change even though their own bank didn't move. Not persisted. */
    private long rev = 0;

    public long rev() { return rev; }

    /** The player's ledger, or null if they've never pressed anything. */
    public CompressionLedger get(UUID player) {
        return ledgers.get(player);
    }

    /** The communal server ledger (never null; empty until someone donates). */
    public CompressionLedger server() {
        return serverLedger;
    }

    /** Record a press for a player (created on first touch). */
    public void record(UUID player, String species, long eggs) {
        ledgers.computeIfAbsent(player, u -> new CompressionLedger()).record(species, eggs);
        rev++;
        markDirty();
    }

    /** Record a donation into the communal pool. */
    public void recordServer(String species, long eggs) {
        serverLedger.record(species, eggs);
        rev++;
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound map = new NbtCompound();
        ledgers.forEach((u, l) -> {
            NbtCompound c = new NbtCompound();
            l.snapshot().forEach(c::putLong);
            map.put(u.toString(), c);
        });
        nbt.put("ledgers", map);
        NbtCompound sv = new NbtCompound();
        serverLedger.snapshot().forEach(sv::putLong);
        nbt.put("server", sv);
        return nbt;
    }

    private static CompressionStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        CompressionStore s = new CompressionStore();
        NbtCompound sv = nbt.getCompound("server");
        Map<String, Long> svSnap = new HashMap<>();
        for (String sp : sv.getKeys()) svSnap.put(sp, sv.getLong(sp));
        s.serverLedger = CompressionLedger.serverFromSnapshot(svSnap);
        NbtCompound map = nbt.getCompound("ledgers");
        for (String k : map.getKeys()) {
            try {
                NbtCompound c = map.getCompound(k);
                Map<String, Long> snap = new HashMap<>();
                for (String sp : c.getKeys()) snap.put(sp, c.getLong(sp));
                s.ledgers.put(UUID.fromString(k), CompressionLedger.fromSnapshot(snap));
            } catch (Exception e) {
                com.greenerpastures.core.GpLog.w("compression", "load_skip_malformed", "key", k, "err", String.valueOf(e));
            }
        }
        return s;
    }

    private static final Type<CompressionStore> TYPE = new Type<>(CompressionStore::new, CompressionStore::fromNbt, null);

    public static CompressionStore get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
