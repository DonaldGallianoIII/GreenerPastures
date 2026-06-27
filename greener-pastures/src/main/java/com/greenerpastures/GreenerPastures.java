package com.greenerpastures;

import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.egg.collector.ShinyEggCollector;
import com.greenerpastures.pasture.breeding.BetterPasture;
import com.greenerpastures.pasture.keeper.PastureKeeper;
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
        LOG.info("Greener Pastures (A Data Science Mod) — common init");

        // analytics/ — open the local event log before any emitter runs
        Analytics.init();

        // pasture/
        PastureKeeper.init();   // per-pasture no-wander toggle + loot collector
        BetterPasture.init();   // opt-in multi-pair breeding (Cobbreeding bridge)

        // egg/ (server-side block)
        ShinyEggCollector.init();   // shiny-egg auto-collector block
    }
}
