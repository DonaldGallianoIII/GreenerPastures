package com.greenerpastures.pasture.breeding;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.analytics.Event;
import com.greenerpastures.core.GpLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The multi-pair breeding engine. Driven by a stable Fabric server-tick event (no mixin) over the
 * pasture records in {@link PastureRegistry}: every breeding interval, each compatible pair in a
 * pasture with a Pasture Upgrade slotted lays one egg in parallel — same per-egg rate as Cobbreeding,
 * just N pairs at once. Per-pasture timers are in-memory; a missed interval after a restart is harmless.
 *
 * <p>Pairing comes from the wand's Breeding Arrangement board ({@link PastureData#pairings}: tethering
 * id → bucket 1..maxPairs, two mons per bucket). If no pairing has been set, it falls back to
 * slot-adjacency so a freshly-upgraded pasture breeds with zero configuration.
 */
public final class MultiPairBreeder {
    private MultiPairBreeder() {}

    private static final int SCAN_INTERVAL = 20;

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(MultiPairBreeder::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        if (!CobbreedingBridge.isAvailable()) return;
        if (world.getTime() % SCAN_INTERVAL != 0L) return;

        MinecraftServer server = world.getServer();
        PastureRegistry reg = PastureRegistry.get(server);
        Map<BlockPos, PastureData> pastures = reg.inWorld(world);
        if (pastures.isEmpty()) return;

        long now = world.getTime();
        boolean dirty = false;
        for (Map.Entry<BlockPos, PastureData> entry : pastures.entrySet()) {
            BlockPos pos = entry.getKey();
            try {
                PastureData pd = entry.getValue();
                BreedingTier tier = pd.tier();
                if (tier == null) continue;                                  // no Pasture Upgrade slotted

                if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockEntity be = world.getBlockEntity(pos);
                if (!(be instanceof PokemonPastureBlockEntity pasture)) continue;
                if (!CobbreedingBridge.isBreedingActivated(be.getCachedState())) continue;

                // Only our configured pairs should breed — keep Cobbreeding's native ticker from laying
                // a rogue random egg by holding its timer open every scan (not just on our breed ticks).
                CobbreedingBridge.suppressNativeBreeding(pos, now);

                int moved = drainQueueToTray(pos, pd);                       // refill the tray as it's harvested

                int laid = 0;
                if (now >= pd.nextBreedTick) {
                    laid = breedPairs(world, pos, pasture, tier, pd, now);   // enqueues into the FIFO
                    pd.nextBreedTick = now + CobbreedingBridge.nextBreedingInterval();
                    moved += drainQueueToTray(pos, pd);                      // top the tray straight up
                }

                if (moved > 0 || laid > 0) {
                    dirty = true;
                    CobbreedingBridge.refreshHasEgg(world, pos);
                }
            } catch (Throwable t) {
                // One misbehaving pasture (a tethering whose Pokémon failed to deserialize, a Cobblemon/
                // Cobbreeding API drift, etc.) must NEVER take down the world tick — skip it, keep serving
                // the rest. The bridge already self-disables on egg-build failure; this guards the rest.
                GpLog.w("breeder", "pasture_skip", "pos", pos.toShortString(), "err", String.valueOf(t));
            }
        }
        if (dirty) reg.markDirty();
    }

    /** Lay one egg per compatible configured pair (up to the tier's cap) into the FIFO egg-queue. */
    private static int breedPairs(ServerWorld world, BlockPos pos, PokemonPastureBlockEntity pasture,
                                  BreedingTier tier, PastureData pd, long now) {
        List<PokemonPastureBlockEntity.Tethering> tethered = pasture.getTetheredPokemon();
        if (tethered == null || tethered.size() < 2) return 0;

        List<List<PokemonPastureBlockEntity.Tethering>> pairs = pd.pairings.isEmpty()
                ? adjacencyPairs(tethered, tier.maxPairs)
                : bucketPairs(tethered, pd, tier.maxPairs);

        double proc = pd.shinyProcChance();   // shiny augment on the slotted upgrade (0 if none)
        String mode = pd.pairings.isEmpty() ? "auto" : "buckets";
        int laid = 0;
        for (int i = 0; i < pairs.size(); i++) {
            CobbreedingBridge.BredEgg egg = CobbreedingBridge.buildEggForPair(pairs.get(i), proc);
            if (egg == null) continue;                              // incompatible pair, skip
            if (!pd.eggQueue.offer(egg.stack())) {                  // FIFO full → pause (keep eggs aren't evicted)
                GpLog.w("breeder", "queue_full", "pos", pos.toShortString(), "cap", pd.eggQueue.cap());
                break;
            }
            laid++;
            Analytics.record(world, Event.of("egg_laid")
                    .put("source", "multipair").put("tier", tier.name())
                    .put("pair", i + 1).put("mode", mode)
                    .put("shiny", egg.shiny()).put("proc_shiny", egg.procShiny())
                    .put("x", pos.getX()).put("y", pos.getY()).put("z", pos.getZ()));
        }
        if (laid > 0) {
            GpLog.d("breeder", "brood", "pos", pos.toShortString(), "laid", laid, "queued", pd.eggQueue.size());
        }
        return laid;
    }

    /** Move queued eggs into the pasture's tray while it has empty slots; returns how many moved. */
    private static int drainQueueToTray(BlockPos pos, PastureData pd) {
        int moved = 0;
        ItemStack next;
        while ((next = pd.eggQueue.peek()) != null) {
            if (!CobbreedingBridge.addEgg(pos, next)) break;       // tray full → leave it queued (never lost)
            pd.eggQueue.poll();                                     // committed to the tray
            moved++;
        }
        if (moved > 0) GpLog.d("breeder", "drain", "pos", pos.toShortString(), "moved", moved, "queued", pd.eggQueue.size());
        return moved;
    }

    /** Zero-config default: pair tether slots (2k, 2k+1), up to the tier's cap. */
    private static List<List<PokemonPastureBlockEntity.Tethering>> adjacencyPairs(
            List<PokemonPastureBlockEntity.Tethering> tethered, int maxPairs) {
        int n = Math.min(maxPairs, tethered.size() / 2);
        List<List<PokemonPastureBlockEntity.Tethering>> out = new ArrayList<>(n);
        for (int p = 0; p < n; p++) out.add(List.of(tethered.get(2 * p), tethered.get(2 * p + 1)));
        return out;
    }

    /** Explicit pairing: group tethered mons by their assigned bucket (1..maxPairs); a pair needs two. */
    private static List<List<PokemonPastureBlockEntity.Tethering>> bucketPairs(
            List<PokemonPastureBlockEntity.Tethering> tethered, PastureData pd, int maxPairs) {
        Map<Integer, List<PokemonPastureBlockEntity.Tethering>> byBucket = new HashMap<>();
        for (PokemonPastureBlockEntity.Tethering t : tethered) {
            int b = pd.pairings.getOrDefault(t.getTetheringId(), 0);
            if (b >= 1 && b <= maxPairs) byBucket.computeIfAbsent(b, k -> new ArrayList<>()).add(t);
        }
        List<List<PokemonPastureBlockEntity.Tethering>> out = new ArrayList<>();
        for (int b = 1; b <= maxPairs; b++) {
            List<PokemonPastureBlockEntity.Tethering> members = byBucket.get(b);
            if (members != null && members.size() >= 2) out.add(List.of(members.get(0), members.get(1)));
        }
        return out;
    }
}
