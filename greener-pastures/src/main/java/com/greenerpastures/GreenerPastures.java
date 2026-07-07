package com.greenerpastures;

import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.buff.BuffSystem;
import com.greenerpastures.buff.DaemonBuffs;
import com.greenerpastures.goal.GoalCommand;
import com.greenerpastures.notify.NotifySystem;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.DaemonCommand;
import com.greenerpastures.economy.DarkEconomy;
import com.greenerpastures.economy.DataCommand;
import com.greenerpastures.economy.GpItems;
import com.greenerpastures.notebook.PastureHarvest;
import com.greenerpastures.notebook.net.NotebookNet;
import com.greenerpastures.pasture.breeding.AugmentCommand;
import com.greenerpastures.pasture.breeding.BreedCommand;
import com.greenerpastures.pasture.breeding.BetterPasture;
import com.greenerpastures.pasture.keeper.PastureKeeper;
import com.greenerpastures.ritual.RitualSystem;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (dedicated-server + singleplayer) entrypoint for Greener Pastures - A Data Science Mod.
 *
 * <p>Modules live under {@code com.greenerpastures}:
 * <ul>
 *   <li>{@code pasture/}   - PastureKeeper (no-wander + loot) and Better Pasture (multi-pair breeding)</li>
 *   <li>{@code egg/}       - egg odds/culler/finder, shiny highlighter, shiny-egg auto-collector</li>
 *   <li>{@code analytics/} - local event log, incremental aggregation, CSV/HTML export (the "data science")</li>
 *   <li>{@code core/}      - shared config, networking, keybinds</li>
 * </ul>
 * Server-authoritative features register here; client-only UI registers in
 * {@link com.greenerpastures.client.GreenerPasturesClient}.
 */
public final class GreenerPastures implements ModInitializer {
    public static final String MOD_ID = "greenerpastures";
    public static final Logger LOG = LoggerFactory.getLogger("Greener Pastures");

    @Override
    public void onInitialize() {
        GpLog.init();   // observability seam - open the live debug log before anything else (OBSERVABILITY.md)
        LOG.info("Greener Pastures (A Data Science Mod) - common init");

        // analytics/ - open the local event log before any emitter runs
        Analytics.init();
        NotifySystem.init();    // player notifications observe the analytics event stream
        GoalCommand.init();     // /gp goal - set + track a breeding hunt (track-only, non-destructive; ships)
        com.greenerpastures.core.PerfCommand.init();          // /gp perf - the built-in profiler (ships; it's a feature)
        if (GpLog.QA_MODE) {
            // Dev/QA-only commands - they bypass the GPU economy and rewrite cadences/balances, so a public
            // build must not carry them hot. One switch turns them (and DEBUG logging) back on for a test
            // instance: -Dgreenerpastures.qa=true (or env GP_QA=true).
            AugmentCommand.init();  // /gp augment - no-UI install path: set augment levels on a held Kernel
            DataCommand.init();     // /gp data - grant/set Data balance
            DaemonCommand.init();   // /gp daemon - compile a Daemon loadout without the console
            BreedCommand.init();    // /gp breed - override breeding cadence
            com.greenerpastures.arcade.ArcadeCommand.init();      // /gp coins - grant Game Corner Coins
            com.greenerpastures.notebook.HarvestCommand.init();   // /gp harvest - override the harvest-sweep cadence
            LOG.warn("Greener Pastures QA MODE - QA commands registered + DEBUG logging (do not ship a server like this)");
        }


        // pasture/
        PastureKeeper.init();   // per-pasture no-wander toggle + loot collector
        BetterPasture.init();   // opt-in multi-pair breeding (Cobbreeding bridge)

        // economy/ - dark economy min-slice: Renderer block (eggs → Data) + Daemon item + per-player Data
        DarkEconomy.init();
        GpItems.init();         // console-economy items: GPU reagent, Data-disk denominations, Notebook (art + registration; behaviour later)

        // buff/ - load the Daemon "root" buff rules + start the per-second grant/drain loop
        BuffSystem.init();
        DaemonBuffs.init();

        // ritual/ - load the custom-drop config (type-drops + gacha rituals) the Harvester reads
        RitualSystem.init();
        com.greenerpastures.ritual.UltraCompressedSnackRecipe.init();   // N poke snacks → one merged mega-bait (Deuce spec)
        com.greenerpastures.ritual.SnackRepelChargeRecipe.init();       // can + typed berries → charged Snack Repel (Overdrive pt.1)
        com.greenerpastures.glitch.Missingno.init();                     // the 1M-Data capstone: rotation ticker + battle refusal

        // notebook/ - block-free harvest: owned (Notebook-linked) pastures roll drops → owner's Notebook storage
        // (replaces the Harvester block for owned pastures; the block stands down for them to avoid double-dip).
        PastureHarvest.init();

        // notebook/ - console sync layer (C2S request → S2C status; per-tab payloads land with each tab)
        NotebookNet.init();

        // notebook/ - the online gate for catch-up progress: stamp logouts, shift pasture anchors past offline
        // gaps on join (away-in-other-chunks time counts; offline time doesn't - Deuce, 2026-07-03).
        com.greenerpastures.notebook.OfflineProgress.init();

        // core/ - the ship-with Field Guide: every player gets one on first join (#18).
        com.greenerpastures.core.FirstJoinGift.init();

        // Every GP recipe rides the recipe book from join #1 - the loop must never hide behind first-craft.
        com.greenerpastures.core.RecipeBookUnlock.init();

        // Session hygiene: statics survive across worlds in singleplayer (one JVM, integrated server restarts),
        // so wipe every per-session store on server start - a fresh world must never show the last world's stats
        // (Deuce hit exactly this: a brand-new world inheriting the old world's dashboard/goal numbers).
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            com.greenerpastures.notebook.EggLog.clearAll();               // dashboard totals + the void-log feed
            com.greenerpastures.goal.GoalStore.clearAll();                // active hunts + progress
            NotebookNet.resetSession();                                    // prefetch cooldowns + push gates
            com.greenerpastures.pasture.breeding.MultiPairBreeder.testIntervalTicks = 0L;   // QA cadence override
            com.greenerpastures.notebook.PastureHarvest.testIntervalTicks = 0L;             // QA harvest override
            com.greenerpastures.notify.Inbox.clearAll();                                     // console Inbox notes
            com.greenerpastures.core.GpProf.reset();                                          // fresh perf window per world
            com.greenerpastures.drops.RitualHarvest.resetSession();                            // spanning-ritual pasture snapshots
            com.greenerpastures.pasture.breeding.MultiPairBreeder.resetSession();               // full-pasture nag dedupe
        });

        // DISCONNECT pruning (perf-audit R3 #5): session maps stay bounded by ONLINE players on a 24/7 server.
        // EggLog = per-login dashboard stats (safe to drop). Goals + Inbox deliberately SURVIVE relogs - an
        // active hunt and unread away-notes are the feature; both are small and capped.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                com.greenerpastures.notebook.EggLog.forget(handler.getPlayer().getUuid()));
    }
}
