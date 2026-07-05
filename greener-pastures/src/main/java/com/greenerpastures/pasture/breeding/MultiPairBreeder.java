package com.greenerpastures.pasture.breeding;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.analytics.Event;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.AugmentFunction;
import com.greenerpastures.goal.GoalTracker;
import com.greenerpastures.notebook.EggIngest;
import com.greenerpastures.notebook.EggLog;
import com.greenerpastures.economy.DataStore;
import com.greenerpastures.economy.EffectiveAugments;
import com.greenerpastures.economy.TetherRuntime;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The multi-pair breeding engine. Driven by a stable Fabric server-tick event (no mixin) over the
 * pasture records in {@link PastureRegistry}: every breeding interval, each compatible pair in a
 * pasture with a Pasture Upgrade slotted lays one egg in parallel - same per-egg rate as Cobbreeding,
 * just N pairs at once. Per-pasture timers are in-memory; a missed interval after a restart is harmless.
 *
 * <p>Pairing comes from the wand's Breeding Arrangement board ({@link PastureData#pairings}: tethering
 * id → bucket 1..maxPairs, two mons per bucket). If no pairing has been set, it falls back to
 * slot-adjacency so a freshly-upgraded pasture breeds with zero configuration.
 */
public final class MultiPairBreeder {
    private MultiPairBreeder() {}

    private static final int SCAN_INTERVAL = 20;

    /** QA/testing override (set via {@code /gp breed interval <seconds>}): when &gt; 0, every pasture breeds on this
     *  fixed tick interval, bypassing both Cobbreeding's configured time AND the 2.5-min speed floor (and the Speed
     *  augment). 0 = normal cadence. In-memory + volatile so a fast test rate resets on restart and can never ship
     *  baked into a world save. */
    public static volatile long testIntervalTicks = 0L;

    /** Catch-up ceiling for missed broods (12h of world time) - mirrors PastureHarvest's drop catch-up. */
    private static final long MAX_CATCHUP_TICKS = 12L * 60L * 60L * 20L;

    /** The augment functions the breeder actually applies to the egg - and therefore the only tethers it pays
     *  burn for: Shiny (proc), Speed (cadence), IV Floor (perfect IVs) and EV (EV head-start). Drop Rate/Yield
     *  belong to the Harvester, Enrichment to the Renderer: each consumer drains its own set on its own clock
     *  ({@link TetherRuntime#resolveFor}), so a tether is never double-charged. */
    private static final Set<AugmentFunction> BREEDING_FUNCTIONS =
            EnumSet.of(AugmentFunction.SHINY, AugmentFunction.SPEED,
                    AugmentFunction.IV_FLOOR, AugmentFunction.EV, AugmentFunction.HATCH);

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(MultiPairBreeder::onWorldTick);
    }

    /** Pastures already nagged about being full this session - one Inbox note per fill episode, not per brood. */
    private static final Set<Long> fullNotified = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** SP-statics hygiene - clear with the other session stores. */
    public static void resetSession() { fullNotified.clear(); }

    /** The silent-stop fix (review u6): breeding pausing on a full bank/tray/queue was a log line only. */
    private static void notifyFull(PastureData pd, BlockPos pos) {
        if (pd.owner == null || !fullNotified.add(pos.asLong())) return;
        com.greenerpastures.notify.Inbox.push(pd.owner, "⚠",
                (pd.name.isEmpty() ? pos.toShortString() : pd.name)
                + " is FULL - egg production paused. Pull from the BioBank or empty the tray to resume.");
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
        java.util.List<BlockPos> orphans = null;
        try (var scan = com.greenerpastures.core.GpProf.begin("breeder.scan")) {
        for (Map.Entry<BlockPos, PastureData> entry : pastures.entrySet()) {
            BlockPos pos = entry.getKey();
            try {
                PastureData pd = entry.getValue();
                if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                BlockEntity be = world.getBlockEntity(pos);
                if (!(be instanceof PokemonPastureBlockEntity pasture)) {
                    // chunk loaded but the pasture block is GONE (TNT / creeper / command / piston - none
                    // fire PlayerBlockBreakEvents). Reclaim its items + record AFTER the loop (removing from
                    // the live sub-map mid-iteration would CME). (re-audit H2)
                    (orphans != null ? orphans : (orphans = new java.util.ArrayList<>())).add(pos);
                    continue;
                }
                // Self-heal the pairing board: a mon untethered/released while this pasture is LOADED leaves a
                // stale UUID in pd.pairings - harmless to breeding (pairs resolve against the live roster) but
                // it renders as a hex-labelled ghost parent in the console graph (BUG-011). The roster here is
                // authoritative (chunk loaded, BE present), so pruning is safe; never do this for unloaded pastures.
                if (!pd.pairings.isEmpty()) {
                    java.util.List<PokemonPastureBlockEntity.Tethering> live = pasture.getTetheredPokemon();
                    if (live != null) {
                        java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
                        for (PokemonPastureBlockEntity.Tethering t : new java.util.ArrayList<>(live)) ids.add(t.getTetheringId());
                        if (pd.pairings.keySet().removeIf(id -> !ids.contains(id))) {
                            dirty = true;
                            GpLog.d("breeder", "pairing_pruned", "pos", pos.toShortString(), "remaining", pd.pairings.size());
                        }
                    }
                }

                BreedingTier tier = pd.tier();
                if (tier == null || !CobbreedingBridge.isBreedingActivated(be.getCachedState())) {
                    // LOADED but not breeding (no kernel / toggle off): advance the anchor so flipping the
                    // toggle can't bank a fake "catch-up" burst (review: the pause exploit - off overnight,
                    // on at dawn, ~288 broods dumped). Real catch-up only accrues while the CHUNK is away.
                    if (pd.lastBreedTick > 0 && now > pd.lastBreedTick) pd.lastBreedTick = now;
                    continue;
                }

                // Only our configured pairs should breed - keep Cobbreeding's native ticker from laying
                // a rogue random egg by holding its timer open every scan (not just on our breed ticks).
                CobbreedingBridge.suppressNativeBreeding(pos, now);

                int moved = drainQueueToTray(pos, pd);                       // refill the tray as it's harvested

                int laid = 0;
                if (now >= pd.nextBreedTick) {
                    // Resolve the Kernel's base mods × slotted Soul Tethers against the operator's Data:
                    // fed → amplify + drain; starved → free base, no drain (TetherRuntime - all tested).
                    // Only the breeding-function tethers (shiny/speed) - the Harvester/Renderer drain theirs.
                    long balance = (pd.owner != null) ? DataStore.get(server).balanceOf(pd.owner) : 0L;
                    TetherRuntime.Resolution res = TetherRuntime.resolveFor(
                            pd.baseAugmentLevels(), pd.slottedTethers(), balance, BREEDING_FUNCTIONS);

                    long interval = testIntervalTicks > 0
                            ? testIntervalTicks   // QA override (/gp breed interval N) - fixed rate, floor bypassed
                            : speedAdjustedInterval(CobbreedingBridge.nextBreedingInterval(),
                                    res.effective().speedLevel(), tier.baseSpeedFactor());

                    // CATCH-UP: broods missed while this chunk was unloaded (the loop skips unloaded chunks, so
                    // lastBreedTick froze). Rolled now with the current pairs/Kernel - every egg still walks the
                    // full pipeline (shiny proc, Daemon graph, BioBank/void log, goals). Capped at 12h; offline
                    // gaps are gated out by OfflineProgress (online away-time counts, offline doesn't). broods=1
                    // is the normal loaded-chunk case.
                    int broods = 1;
                    if (pd.lastBreedTick > 0 && now > pd.lastBreedTick) {
                        long gap = Math.min(now - pd.lastBreedTick, MAX_CATCHUP_TICKS);
                        broods = (int) Math.max(1, gap / Math.max(1L, interval));
                    }
                    pd.lastBreedTick = now;

                    // Build the pair list ONCE for all broods - the roster/pairings can't change mid-catch-up
                    // (the chunk was unloaded), and a 12h catch-up is up to ~288 broods (perf-audit R3 tick #8).
                    java.util.List<java.util.List<PokemonPastureBlockEntity.Tethering>> pairs = buildPairs(pasture, tier, pd);
                    String mode = pd.pairings.isEmpty() ? "auto" : "buckets";
                    // Amplification is paid PER BROOD (review: the old lump debit was all-or-nothing, so a
                    // long catch-up amplified every egg then failed to pay - free shinies). Each productive
                    // brood debits its burn up front; the moment the wallet can't pay, the REST of the
                    // catch-up runs starved (free base mods) - exactly the fed/starved contract, per cycle.
                    TetherRuntime.Resolution starved = res.drain() > 0
                            ? TetherRuntime.resolveFor(pd.baseAugmentLevels(), pd.slottedTethers(), 0L, BREEDING_FUNCTIONS)
                            : res;
                    boolean fed = true;
                    long drained = 0L;
                    int productiveBroods = 0;
                    for (int b = 0; b < broods && !pairs.isEmpty(); b++) {
                        EffectiveAugments eff = fed ? res.effective() : starved.effective();
                        int got = breedPairs(world, pos, tier, pd, now, eff, pairs, mode);
                        if (got == 0 && laid == 0 && b > 2) break;   // sterile pasture - don't grind 288 no-op broods
                        if (got > 0) {
                            productiveBroods++;
                            if (fed && res.drain() > 0 && pd.owner != null) {
                                if (DataStore.get(server).tryDebit(pd.owner, res.drain())) drained += res.drain();
                                else fed = false;                    // broke mid-catch-up: rest of the burst is base-rate
                            }
                        }
                        laid += got;
                    }
                    pd.nextBreedTick = now + interval;

                    if (broods > 1 && laid > 0 && pd.owner != null) {        // the away-brood note → the console's Inbox
                        com.greenerpastures.notify.Inbox.push(pd.owner, "🥚",
                                (pd.name.isEmpty() ? pos.toShortString() : pd.name)
                                + " caught up " + broods + " broods while away → " + laid + " eggs");
                    }
                    if (drained > 0) {                                       // tethers earned their burn - paid per productive brood
                        GpLog.d("tether", "drain", "pos", pos.toShortString(),
                                "data", drained, "owner", pd.owner.toString(), "starvedMidway", !fed);
                    }
                    moved += drainQueueToTray(pos, pd);                      // top the tray straight up
                }

                if (moved > 0 || laid > 0) {
                    dirty = true;
                    CobbreedingBridge.refreshHasEgg(world, pos);
                }
            } catch (Throwable t) {
                // One misbehaving pasture (a tethering whose Pokémon failed to deserialize, a Cobblemon/
                // Cobbreeding API drift, etc.) must NEVER take down the world tick - skip it, keep serving
                // the rest. The bridge already self-disables on egg-build failure; this guards the rest.
                GpLog.w("breeder", "pasture_skip", "pos", pos.toShortString(), "err", String.valueOf(t));
            }
        }
        }
        if (orphans != null) {
            for (BlockPos op : orphans) {
                PastureData opd = reg.get(world, op);
                if (opd != null) BetterPasture.reclaim(world, op, opd, reg);   // non-player removal cleanup (H2)
            }
            dirty = true;
        }
        if (dirty) reg.markDirty();
    }

    /** Faster cadence = Speed augment (×1.5/×2/×3 by effective level) × the KERNEL's own tier perk
     *  ({@link BreedingTier#baseSpeedFactor()} - every kernel breeds faster, greener much faster), floored
     *  so breeding never runs absurdly fast (the floor protects the server). */
    private static long speedAdjustedInterval(long baseInterval, int speedLevel, double tierFactor) {
        double factor = switch (Math.max(0, Math.min(3, speedLevel))) {
            case 1 -> 1.5;
            case 2 -> 2.0;
            case 3 -> 3.0;
            default -> 1.0;
        };
        // 2.5-min floor SIGNED OFF for release (Deuce 2026-07-04): at the ~10-min default base it only
        // shaves the very top of the Greener+Speed III stack; servers configuring faster bases are
        // deliberately clamped. Revisit only if a future tier/augment pushes total factor past ~4.
        return Math.max(3000L, Math.round(baseInterval / (factor * Math.max(1.0, tierFactor))));
    }

    /** Snapshot the roster + resolve the configured pairs - hoisted out of the brood loop so a catch-up
     *  builds them once, not per brood. snapshot: getTetheredPokemon() hands back Cobblemon's LIVE backing
     *  list; iterating it directly while Cobblemon mutates it (tether / release / checkPokemon) risks a CME
     *  (re-audit M1). */
    private static List<List<PokemonPastureBlockEntity.Tethering>> buildPairs(
            PokemonPastureBlockEntity pasture, BreedingTier tier, PastureData pd) {
        List<PokemonPastureBlockEntity.Tethering> live = pasture.getTetheredPokemon();
        if (live == null || live.size() < 2) return List.of();
        List<PokemonPastureBlockEntity.Tethering> tethered = new ArrayList<>();
        for (PokemonPastureBlockEntity.Tethering t : new ArrayList<>(live)) {
            try {   // the glitch never breeds - a MissingNo frozen on Ditto would be a universal parent (review)
                if (com.greenerpastures.glitch.Missingno.isMissingno(t.getPokemon())) continue;
            } catch (Throwable ignored) { }
            tethered.add(t);
        }
        if (tethered.size() < 2) return List.of();
        int cap = pd.maxPairs();   // tier pairs + any WILD-corruption bonus
        return pd.pairings.isEmpty()
                ? adjacencyPairs(tethered, cap)
                : bucketPairs(tethered, pd, cap);
    }

    /** Lay one egg per compatible configured pair (up to the tier's cap) into the FIFO egg-queue.
     *  {@code eff} is the resolved effective augments (base × any fed breeding Tether) from onWorldTick - it
     *  shapes each egg's shiny proc, IV floor and EV floor. {@code pairs} comes prebuilt from
     *  {@link #buildPairs} (reused across catch-up broods). */
    private static int breedPairs(ServerWorld world, BlockPos pos, BreedingTier tier, PastureData pd, long now,
                                  EffectiveAugments eff,
                                  List<List<PokemonPastureBlockEntity.Tethering>> pairs, String mode) {
        int laid = 0;
        for (int i = 0; i < pairs.size(); i++) {
            EggShape shape = new EggShape(
                    eff.shinyProcChance(), eff.ivFloorCount(), pd.evSpread(),
                    NatureCatalog.byIndex(eff.natureIndex()),     // Nature selector → nature id (null = no lock)
                    BallCatalog.byIndex(eff.ballIndex()),         // Ball selector → ball id (null = no lock)
                    eff.forceHiddenAbility(),                     // Ability toggle → force the hidden ability
                    eff.teachEggMoves(),                          // Egg Moves toggle → teach species egg moves
                    eff.hatchLevel());                            // Hatch Haste → scale the egg's Cobbreeding TIMER
            CobbreedingBridge.BredEgg egg = CobbreedingBridge.buildEggForPair(pairs.get(i), shape);
            if (egg == null) continue;                              // incompatible pair, skip
            if (pd.owner != null) {                                 // LINKED → eggs as DATA into the owner's Notebook BioBank
                if (!EggIngest.ingest(world, pd.owner, egg.stack(), pd, pos, pairs.get(i).get(0).getTetheringId())
                        && !pd.eggQueue.offer(egg.stack())) {       // BioBank full → tray fallback → tray full → pause
                    GpLog.w("breeder", "queue_full", "pos", pos.toShortString(), "cap", pd.eggQueue.cap());
                    notifyFull(pd, pos);
                    break;
                }
            } else if (!pd.eggQueue.offer(egg.stack())) {           // unlinked → physical eggs in the tray (unchanged)
                GpLog.w("breeder", "queue_full", "pos", pos.toShortString(), "cap", pd.eggQueue.cap());
                notifyFull(pd, pos);
                break;
            }
            laid++;
            Analytics.record(world, Event.of("egg_laid")
                    .put("source", "multipair").put("tier", tier.name())
                    .put("pair", i + 1).put("mode", mode)
                    .put("shiny", egg.shiny()).put("proc_shiny", egg.procShiny())
                    .put("x", pos.getX()).put("y", pos.getY()).put("z", pos.getZ()));
            GoalTracker.recordLaid(world, pd.owner, egg);          // fold this egg into the owner's breeding goal
            EggLog.recordLaid(pd.owner, tier.name(), egg.shiny(), egg.procShiny(), now);   // dashboard totals + sparkline
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
