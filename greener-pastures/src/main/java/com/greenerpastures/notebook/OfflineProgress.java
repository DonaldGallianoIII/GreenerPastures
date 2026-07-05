package com.greenerpastures.notebook;

import com.greenerpastures.core.GpLog;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The <b>online gate</b> for catch-up progress (Deuce, 2026-07-03): away-from-the-chunk time counts, but
 * OFFLINE time must not. In singleplayer that's automatic (world time freezes when the game closes), but on a
 * server the world ticks on - so we stamp each player's <b>logout world-time</b>, and on their next join shift
 * every owned pasture's catch-up anchors ({@link PastureData#lastHarvestTick} / {@link PastureData#lastBreedTick})
 * forward by the offline gap, clamped to now. The arithmetic credits exactly the ONLINE away time, even when it
 * straddles the logout (left the chunk at 3pm, logged off at 4pm, back tomorrow → credited that 3-4pm hour), and
 * a pasture that kept running while offline (chunk held loaded by another player) clamps to now - no double credit.
 */
public final class OfflineProgress extends PersistentState {
    private static final String ID = "greenerpastures_offline";

    private final Map<UUID, Long> logoutAt = new HashMap<>();

    public static void init() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            OfflineProgress op = get(server);
            op.logoutAt.put(handler.player.getUuid(), server.getOverworld().getTime());
            op.markDirty();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> applyGap(server, handler.player.getUuid())));
    }

    /** Shift the player's pasture anchors past their offline gap (apply-once: the stamp is then cleared). */
    private static void applyGap(MinecraftServer server, UUID player) {
        OfflineProgress op = get(server);
        Long logout = op.logoutAt.remove(player);
        op.markDirty();
        if (logout == null) return;
        long gap = server.getOverworld().getTime() - logout;
        if (gap <= 0) return;
        PastureRegistry reg = PastureRegistry.get(server);
        int shifted = 0;
        for (ServerWorld w : server.getWorlds()) {
            long wNow = w.getTime();
            for (Map.Entry<BlockPos, PastureData> e : reg.inWorld(w).entrySet()) {
                PastureData pd = e.getValue();
                if (!player.equals(pd.owner)) continue;
                if (pd.lastHarvestTick > 0) pd.lastHarvestTick = Math.min(wNow, pd.lastHarvestTick + gap);
                if (pd.lastBreedTick > 0) pd.lastBreedTick = Math.min(wNow, pd.lastBreedTick + gap);
                shifted++;
            }
        }
        if (shifted > 0) {
            reg.markDirty();
            GpLog.i("offline", "gap_applied", "player", player.toString(), "gapTicks", Long.toString(gap), "pastures", Integer.toString(shifted));
        }
    }

    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound m = new NbtCompound();
        logoutAt.forEach((u, t) -> m.putLong(u.toString(), t));
        nbt.put("logouts", m);
        return nbt;
    }

    private static OfflineProgress fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        OfflineProgress s = new OfflineProgress();
        NbtCompound m = nbt.getCompound("logouts");
        for (String k : m.getKeys()) {
            try { s.logoutAt.put(UUID.fromString(k), m.getLong(k)); } catch (IllegalArgumentException ignored) { }
        }
        return s;
    }

    private static final Type<OfflineProgress> TYPE = new Type<>(OfflineProgress::new, OfflineProgress::fromNbt, null);

    public static OfflineProgress get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
