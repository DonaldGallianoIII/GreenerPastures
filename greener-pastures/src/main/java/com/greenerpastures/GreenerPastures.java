package com.greenerpastures;

import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.biobank.BioBank;
import com.greenerpastures.buff.BuffSystem;
import com.greenerpastures.buff.DaemonBuffs;
import com.greenerpastures.goal.GoalCommand;
import com.greenerpastures.notify.NotifySystem;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.drops.Harvester;
import com.greenerpastures.economy.DarkEconomy;
import com.greenerpastures.pasture.breeding.BetterPasture;
import com.greenerpastures.pasture.keeper.PastureKeeper;
import com.greenerpastures.ritual.RitualSystem;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (dedicated-server + singleplayer) entrypoint for Greener Pastures — A Data Science Mod.
 *
 * <p>Modules live under {@code com.greenerpastures}:
 * <ul>
 *   <li>{@code pasture/}   — PastureKeeper (no-wander + loot) and Better Pasture (multi-pair breeding)</li>
 *   <li>{@code egg/}       — egg odds/culler/finder, shiny highlighter, shiny-egg auto-collector</li>
 *   <li>{@code analytics/} — local event log, incremental aggregation, CSV/HTML export (the "data science")</li>
 *   <li>{@code core/}      — shared config, networking, keybinds</li>
 * </ul>
 * Server-authoritative features register here; client-only UI registers in
 * {@link com.greenerpastures.client.GreenerPasturesClient}.
 */
public final class GreenerPastures implements ModInitializer {
    public static final String MOD_ID = "greenerpastures";
    public static final Logger LOG = LoggerFactory.getLogger("Greener Pastures");

    @Override
    public void onInitialize() {
        GpLog.init();   // observability seam — open the live debug log before anything else (OBSERVABILITY.md)
        LOG.info("Greener Pastures (A Data Science Mod) — common init");

        // analytics/ — open the local event log before any emitter runs
        Analytics.init();
        NotifySystem.init();    // player notifications observe the analytics event stream
        GoalCommand.init();     // /gp goal — set + track a breeding hunt (track-only, non-destructive)


        // pasture/
        PastureKeeper.init();   // per-pasture no-wander toggle + loot collector
        BetterPasture.init();   // opt-in multi-pair breeding (Cobbreeding bridge)

        // biobank/ — AE2-style egg storage block (eggs as data, bucketed by species)
        BioBank.init();

        // economy/ — dark economy min-slice: Renderer block (eggs → Data) + Daemon item + per-player Data
        DarkEconomy.init();

        // buff/ — load the Daemon "root" buff rules + start the per-second grant/drain loop
        BuffSystem.init();
        DaemonBuffs.init();

        // ritual/ — load the custom-drop config (type-drops + gacha rituals) the Harvester reads
        RitualSystem.init();

        // drops/ — passive-drops Harvester block (rolls tethered mons' drop tables → its own chest)
        Harvester.init();
    }
}
