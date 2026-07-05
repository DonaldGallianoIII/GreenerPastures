package com.greenerpastures.ritual;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Runtime holder for the loaded {@link RitualConfig} - the single point the Harvester reads the custom-drop
 * rules from. {@link #init()} loads (and on first run writes) {@code config/greenerpastures/rituals.json};
 * until then, and if loading ever fails, the built-in {@link RitualConfig#defaults()} are used so the feature
 * is never in a broken state. Hot-reloadable later via {@link #reload()}.
 */
public final class RitualSystem {
    private RitualSystem() {}

    private static volatile RitualConfig config = RitualConfig.defaults();

    public static void init() {
        reload();
        RitualConfig c = config;
        GreenerPastures.LOG.info("[rituals] {} - {} rituals, {} type-drops (auto-pull {})",
                c.enabled() ? "enabled" : "disabled",
                c.rituals().rituals().size(), c.typeDrops().drops().size(), c.autoPull());
    }

    public static void reload() {
        try {
            config = RitualConfig.load(configPath());
            // Rituals v2 migration: a file from the placeholder era (no hand-designed book) is replaced -
            // the flagship ritual's presence is the version marker. Admin edits to a v2 file are respected.
            if (config.rituals().byId("feast_of_the_blade") == null) {
                GreenerPastures.LOG.warn("[rituals] pre-v2 config detected - regenerating the hand-designed ritual book");
                config = RitualConfig.defaults();
                config.save(configPath());
            } else {
                // Newly-designed rituals must reach existing files without clobbering admin edits: merge any
                // DEFAULT ritual whose id is missing from the loaded book, preserving everything else.
                java.util.List<Ritual> merged = null;
                for (Ritual def : RitualConfig.defaults().rituals().rituals()) {
                    if (config.rituals().byId(def.id()) != null) continue;
                    if (merged == null) merged = new java.util.ArrayList<>(config.rituals().rituals());
                    merged.add(def);
                    GreenerPastures.LOG.info("[rituals] merged new hand-designed ritual '{}'", def.id());
                }
                if (merged != null) {
                    config = new RitualConfig(config.enabled(), config.autoPull(), config.rarityFactor(),
                            config.typeDrops(), new RitualBook(config.rituals().enabled(), merged));
                    config.save(configPath());
                }
                // Hint backfill (2026-07-05): rituals that predate the hint field sit in existing files with
                // hint "" - adopt the DEFAULT's riddle for same-id rituals, preserving every admin-tuned
                // number. An admin who WANTS a ritual hintless can set "hint": " " (non-empty, blank-ish).
                java.util.List<Ritual> backfilled = null;
                for (int i = 0; i < config.rituals().rituals().size(); i++) {
                    Ritual mine = config.rituals().rituals().get(i);
                    Ritual def = RitualConfig.defaults().rituals().byId(mine.id());
                    if (def == null || !mine.hint().isEmpty() || def.hint().isEmpty()) continue;
                    if (backfilled == null) backfilled = new java.util.ArrayList<>(config.rituals().rituals());
                    backfilled.set(i, new Ritual(mine.id(), mine.name(), mine.enabled(), mine.requirement(),
                            mine.outputItem(), mine.outputQty(), mine.baseChancePercent(), mine.hardPity(),
                            mine.softPityStart(), mine.pastureSpan(), def.hint(), mine.outputPool()));
                    GreenerPastures.LOG.info("[rituals] backfilled hint for '{}'", mine.id());
                }
                if (backfilled != null) {
                    config = new RitualConfig(config.enabled(), config.autoPull(), config.rarityFactor(),
                            config.typeDrops(), new RitualBook(config.rituals().enabled(), backfilled));
                    config.save(configPath());
                }
                // Same contract for type-drops: new defaults (echo/amethyst progression drops) append to an
                // existing file; admin-tuned rates for entries that already exist are never touched.
                TypeDropTable mergedDrops = config.typeDrops().mergeMissingDefaults(RitualConfig.defaults().typeDrops());
                if (mergedDrops != config.typeDrops()) {
                    int added = mergedDrops.drops().size() - config.typeDrops().drops().size();
                    GreenerPastures.LOG.info("[rituals] merged {} new default type-drop(s)", added);
                    config = new RitualConfig(config.enabled(), config.autoPull(), config.rarityFactor(),
                            mergedDrops, config.rituals());
                    config.save(configPath());
                }
            }
        } catch (Throwable t) {
            config = RitualConfig.defaults();
            GreenerPastures.LOG.warn("[rituals] config load failed; using defaults", t);
        }
    }

    public static RitualConfig config() {
        return config;
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("greenerpastures").resolve("rituals.json");
    }
}
