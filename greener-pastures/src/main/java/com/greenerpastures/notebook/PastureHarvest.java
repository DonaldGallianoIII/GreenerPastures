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
 * <p>No double-charge: the Harvester block stands down for owned pastures (it logs {@code owned_uses_notebook}),
 * so a tether is billed on exactly one clock. <b>Staple drops only for now</b> — the config-driven ritual /
 * type-drop pass is pending re-wire onto this tick (ritual pull-state must move from the block to
 * {@link PastureData}); tracked as Phase 3b in {@code NOTEBOOK_BUILD_PLAN.md}.
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
    private static final Set<AugmentFunction> DROP_FUNCTIONS =
            EnumSet.of(AugmentFunction.DROP_RATE, AugmentFunction.DROP_YIELD);

    private static final Random RNG = new Random();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(PastureHarvest::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        long interval = testIntervalTicks > 0 ? testIntervalTicks : INTERVAL;
        if (world.getTime() % interval != 0L) return;
        if (!CobbreedingBridge.isAvailable()) return;
        MinecraftServer server = world.getServer();
        PastureRegistry reg = PastureRegistry.get(server);
        Map<BlockPos, PastureData> pastures = reg.inWorld(world);
        if (pastures.isEmpty()) return;
        NotebookStore store = NotebookStore.get(server);
        DataStore data = DataStore.get(server);

        for (Map.Entry<BlockPos, PastureData> e : pastures.entrySet()) {
            PastureData pd = e.getValue();
            if (pd.owner == null) continue;                 // only linked/owned pastures collect into a Notebook
            BlockPos pos = e.getKey();
            if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;   // don't force-load idle chunks (perf-audit — mirrors the breeder)
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity pasture)) continue;
            try {
                // tether-amplified drop plan — the Kernel's base drop mods × any FED drop tether, on this clock
                long balance = data.balanceOf(pd.owner);
                TetherRuntime.Resolution res = TetherRuntime.resolveFor(
                        pd.baseAugmentLevels(), pd.slottedTethers(), balance, DROP_FUNCTIONS);
                double proc = BASE_PROC + res.effective().dropRateFraction();
                int yield = res.effective().dropYieldBonus();

                Map<String, Integer> harvested = DropsBridge.harvest(pasture, RNG, proc, yield);
                long stored = 0;
                for (Map.Entry<String, Integer> d : harvested.entrySet()) {
                    stored += store.deposit(pd.owner, d.getKey(), d.getValue());
                }
                int mons = pasture.getTetheredPokemon().size();
                if (mons > 0) {
                    // the rate-watching line: EVERY sweep, even a dry one — proc% + yield in effect, mons swept,
                    // what landed. Together with DropsBridge's per-proc lines this is the full drop audit trail.
                    GpLog.d("notebook_harvest", "sweep", "pos", pos.toShortString(),
                            "mons", mons, "proc_pct", String.format("%.2f", proc * 100.0),
                            "yield", yield, "stored", stored,
                            "items", harvested.isEmpty() ? "-" : harvested.toString());
                }
                if (stored > 0 && res.drain() > 0) {         // drain drop-tethers ONCE if the amplification produced output
                    data.tryDebit(pd.owner, res.drain());
                    GpLog.d("tether", "drain", "pos", pos.toShortString(),
                            "data", res.drain(), "owner", pd.owner.toString(), "src", "notebook_harvest");
                }
            } catch (Throwable t) {
                // a Cobblemon API edge must never crash the world tick (mirrors the breeder/Renderer/Harvester guards)
                GpLog.w("notebook_harvest", "skip", "pos", pos.toShortString(), "err", String.valueOf(t));
            }
        }
    }
}
