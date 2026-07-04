package com.greenerpastures.notebook;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.drops.DropsBridge;
import com.greenerpastures.economy.AugmentFunction;
import com.greenerpastures.economy.DataStore;
import com.greenerpastures.economy.TetherRuntime;
import com.greenerpastures.pasture.breeding.CobbreedingBridge;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The <b>block-free harvest</b> — the network tick that replaces the Harvester block (Deuce, 2026-06-30:
 * "block free now"). Once an IRL minute it sweeps every <b>owned</b> pasture (a player linked it via the
 * pasture screen — see {@code NOTEBOOK_CONSOLE_SPEC.md} §2) and deposits each tethered mon's rolled drops
 * STRAIGHT into the owner's Notebook ({@link NotebookStore}), draining the owner's drop-tethers on this clock
 * exactly as the block did. Reuses {@link DropsBridge} for the faithful Cobblemon roll, so behaviour is
 * unchanged — only the trigger (owned-pasture sweep, not block adjacency) and the sink (Notebook, not a chest).
 *
 * <p>No double-charge: a tether is billed on exactly one clock. The config-driven custom-drop layers run on
 * this tick too ({@code RitualHarvest} — 3b done): type-drops per typed mon + composition-gated gacha rituals
 * with per-pasture persistent pity ({@link PastureData#ritualState}).
 */
public final class PastureHarvest {
    private PastureHarvest() {}

    private static final int INTERVAL = 1200;        // one harvest sweep per IRL minute (mirrors the block)
    private static final double BASE_PROC = 0.03;    // 3% per tethered mon per minute (mirrors the block)

    /** QA/testing override ({@code /gp harvest interval <seconds>}): when &gt; 0, the sweep runs on this fixed tick
     *  interval instead of the 1-min clock — for watching drop rates live. In-memory + volatile (reset on server
     *  start), so a fast test rate can never ship in a world save. NB: proc chances are PER SWEEP, so a faster
     *  sweep means proportionally more rolls per minute — great for testing, silly for balance. */
    public static volatile long testIntervalTicks = 0L;

    /** Offline-progress ceiling: how far back a catch-up may reach, in ticks (12h of world time). Keeps a
     *  months-old save from rolling millions of sweeps, and bounds the idle yield like any respectable idle game. */
    private static final long MAX_CATCHUP_TICKS = 12L * 60L * 60L * 20L;
    private static final Set<AugmentFunction> DROP_FUNCTIONS =
            EnumSet.of(AugmentFunction.DROP_RATE, AugmentFunction.DROP_YIELD);

    private static final Random RNG = new Random();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(PastureHarvest::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        // Per-PASTURE scheduling (scan every second, sweep each pasture when ITS interval is due) — instead of a
        // global modulo — so a reloading chunk's catch-up fires within a second of arrival, lined up with the
        // breeder's instant catch-up (Deuce, 2026-07-03), not up to a minute later on the boundary tick.
        if (world.getTime() % 20L != 0L) return;
        long interval = testIntervalTicks > 0 ? testIntervalTicks : INTERVAL;
        if (!CobbreedingBridge.isAvailable()) return;
        MinecraftServer server = world.getServer();
        PastureRegistry reg = PastureRegistry.get(server);
        Map<BlockPos, PastureData> pastures = reg.inWorld(world);
        if (pastures.isEmpty()) return;
        NotebookStore store = NotebookStore.get(server);
        DataStore data = DataStore.get(server);
        boolean dirty = false;   // one registry markDirty per scan, not per pasture (perf-audit R3 tick #2)

        try (var scan = com.greenerpastures.core.GpProf.begin("harvest.scan")) {
        for (Map.Entry<BlockPos, PastureData> e : pastures.entrySet()) {
            PastureData pd = e.getValue();
            if (pd.owner == null) continue;                 // only linked/owned pastures collect into a Notebook
            BlockPos pos = e.getKey();
            if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;   // don't force-load idle chunks (perf-audit — mirrors the breeder)
            if (pd.lastHarvestTick > 0 && world.getTime() - pd.lastHarvestTick < interval) continue;  // this pasture isn't due yet
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity pasture)) continue;
            try (var sweepSpan = com.greenerpastures.core.GpProf.begin("harvest.pasture")) {
                // tether-amplified drop plan — the Kernel's base drop mods × any FED drop tether, on this clock
                long balance = data.balanceOf(pd.owner);
                TetherRuntime.Resolution res = TetherRuntime.resolveFor(
                        pd.baseAugmentLevels(), pd.slottedTethers(), balance, DROP_FUNCTIONS);
                double proc = BASE_PROC + res.effective().dropRateFraction();
                int yield = res.effective().dropYieldBonus();

                // OFFLINE CATCH-UP: sweeps this pasture missed while its chunk was unloaded (the loop above skips
                // unloaded chunks, so lastHarvestTick freezes). The roster physically can't change while unloaded,
                // so rolling the missed sweeps NOW with the current mons/Kernel is exact — capped at 12h so an
                // ancient save doesn't roll millions. sweeps=1 is the normal loaded-chunk case.
                long now = world.getTime();
                int sweeps = 1;
                if (pd.lastHarvestTick > 0 && now > pd.lastHarvestTick) {
                    long gap = Math.min(now - pd.lastHarvestTick, MAX_CATCHUP_TICKS);
                    sweeps = (int) Math.max(1, gap / interval);
                }
                pd.lastHarvestTick = now;
                dirty = true;

                // Snapshot the roster ONCE for all sweeps — it can't change mid-catch-up (the chunk was
                // unloaded), and a 12h catch-up is up to 720 sweeps in this one tick (perf-audit R3 tick #1).
                java.util.List<PokemonPastureBlockEntity.Tethering> roster =
                        new java.util.ArrayList<>(pasture.getTetheredPokemon());
                Map<String, Integer> harvested = new java.util.LinkedHashMap<>();
                int productive = 0;
                for (int i = 0; i < sweeps; i++) {
                    Map<String, Integer> one = DropsBridge.harvest(roster, RNG, proc, yield);
                    if (!one.isEmpty()) productive++;
                    one.forEach((id, n) -> harvested.merge(id, n, Integer::sum));
                }
                // Rituals on the network tick (3b): type-drops per mon + composition-gated gacha pulls with
                // persistent pity — banked exactly once per sweep, so catch-up pays the missed pulls too.
                Map<String, Integer> ritual = com.greenerpastures.drops.RitualHarvest.roll(
                        com.greenerpastures.drops.CompositionReader.read(pasture), pd, RNG, sweeps);
                ritual.forEach((id, n) -> harvested.merge(id, n, Integer::sum));
                long stored = 0;
                for (Map.Entry<String, Integer> d : harvested.entrySet()) {
                    stored += store.deposit(pd.owner, d.getKey(), d.getValue());
                }
                int mons = roster.size();
                if (mons > 0) {
                    // the rate-watching line: EVERY sweep, even a dry one — proc% + yield in effect, mons swept,
                    // what landed. Together with DropsBridge's per-proc lines this is the full drop audit trail.
                    GpLog.d("notebook_harvest", "sweep", "pos", pos.toShortString(),
                            "mons", mons, "proc_pct", String.format("%.2f", proc * 100.0),
                            "yield", yield, "sweeps", sweeps, "stored", stored,
                            "items", harvested.isEmpty() ? "-" : harvested.toString());
                }
                if (sweeps > 1 && stored > 0) {              // the away-deposit note → the console's Inbox (not chat)
                    com.greenerpastures.notify.Inbox.push(pd.owner, "⛏",
                            (pd.name.isEmpty() ? pos.toShortString() : pd.name)
                            + " caught up " + sweeps + " sweeps while away → +" + stored + " items");
                }
                if (stored > 0 && res.drain() > 0) {         // drain drop-tethers per PRODUCTIVE sweep (catch-up pays like live play)
                    data.tryDebit(pd.owner, res.drain() * Math.max(1, productive));
                    GpLog.d("tether", "drain", "pos", pos.toShortString(),
                            "data", res.drain() * Math.max(1, productive), "owner", pd.owner.toString(), "src", "notebook_harvest");
                }
            } catch (Throwable t) {
                // a Cobblemon API edge must never crash the world tick (mirrors the breeder/Renderer/Harvester guards)
                GpLog.w("notebook_harvest", "skip", "pos", pos.toShortString(), "err", String.valueOf(t));
            }
        }
        }
        if (dirty) reg.markDirty();
    }
}
