package com.greenerpastures.ritual;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.greenerpastures.core.GpLog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The admin-editable config for the whole custom-drop system: a master {@code enabled} toggle, the Tier-1
 * {@link TypeDropTable}, and the Tier-2 {@link RitualBook}. Serialized as JSON so a server admin can re-map
 * which types / species produce which items, change counts / odds / pity, disable individual rituals or
 * type-drops, or switch the whole system off — all without touching code or a rebuild.
 *
 * <p>{@code autoPull} rolls banked ritual pulls automatically (the interim while there's no gacha GUI — so
 * rituals are fully playable now); set it false once the manual-pull screen exists to let pulls bank and be
 * spent by hand. {@link #defaults()} ships the RITUALS.md roster. {@link #load} is <b>fail-safe</b>: a missing
 * file is written from defaults; a corrupt file logs and falls back to defaults (it does NOT overwrite the
 * admin's file), and never crashes the server. Gson is loaded lazily (a holder) so the pure cores +
 * {@code defaults()} stay usable in the headless test JVM, which has no Gson on its runtime classpath.
 */
public record RitualConfig(boolean enabled, boolean autoPull, TypeDropTable typeDrops, RitualBook rituals) {

    public RitualConfig {
        if (typeDrops == null) typeDrops = new TypeDropTable(false, List.of());
        if (rituals == null) rituals = new RitualBook(false, List.of());
    }

    /** Master-gated views the runtime reads, so the single master toggle disables BOTH tiers at once. */
    public TypeDropTable activeTypeDrops() {
        return enabled ? typeDrops : new TypeDropTable(false, typeDrops.drops());
    }

    public RitualBook activeRituals() {
        return enabled ? rituals : new RitualBook(false, rituals.rituals());
    }

    // ── persistence (fail-safe; Gson lazily loaded) ─────────────────────────
    private static final class GsonHolder {
        static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }

    public static RitualConfig load(Path file) {
        try {
            if (Files.exists(file)) {
                RitualConfig c = GsonHolder.GSON.fromJson(Files.readString(file), RitualConfig.class);
                if (c != null) return c;
                GpLog.w("ritual", "config_empty", "file", file.toString());
            }
        } catch (Throwable t) {
            // corrupt / unreadable → run on defaults but DON'T overwrite the admin's file
            GpLog.w("ritual", "config_bad", "file", file.toString(), "err", String.valueOf(t));
            return defaults();
        }
        RitualConfig def = defaults();
        def.save(file);   // first run → drop the editable default file next to the others
        return def;
    }

    public void save(Path file) {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, GsonHolder.GSON.toJson(this));
        } catch (Throwable t) {
            GpLog.w("ritual", "config_save_fail", "file", file.toString(), "err", String.valueOf(t));
        }
    }

    // ── built-in defaults (the RITUALS.md roster — tuning, edit via the JSON) ─
    public static RitualConfig defaults() {
        return new RitualConfig(true, true, defaultTypeDrops(), defaultRituals());
    }

    private static TypeDropTable defaultTypeDrops() {
        return new TypeDropTable(true, List.of(
                td("ice", "minecraft:ice", 25, 1, 1),
                td("ice", "minecraft:packed_ice", 8, 1, 1),
                td("fire", "minecraft:blaze_rod", 12, 1, 1),
                td("ghost", "minecraft:ghast_tear", 6, 1, 1),
                td("rock", "minecraft:quartz", 20, 1, 2),
                td("ground", "minecraft:quartz", 15, 1, 2),
                td("psychic", "minecraft:lapis_lazuli", 18, 1, 3),
                td("fairy", "minecraft:lapis_lazuli", 12, 1, 2),
                td("grass", "minecraft:sugar_cane", 25, 1, 2),
                td("grass", "minecraft:beetroot", 12, 1, 2),
                td("poison", "minecraft:nether_wart", 10, 1, 2)
        ));
    }

    private static RitualBook defaultRituals() {
        return new RitualBook(true, List.of(
                ritual("nether_forge", "Nether Forge",
                        req(Map.of("fire", 1, "dark", 1, "ghost", 1), 0, List.of()),
                        "minecraft:netherite_scrap", 1, 5.0, 30, 0),
                ritual("forbidden_orchard", "Forbidden Orchard",
                        req(Map.of(), 5, List.of()),
                        "minecraft:enchanted_golden_apple", 1, 3.0, 40, 25),
                ritual("last_stand", "Last Stand",
                        req(Map.of("fairy", 1, "ghost", 1), 0, List.of("sableye")),
                        "minecraft:totem_of_undying", 1, 4.0, 35, 0),
                ritual("endless_sky", "Endless Sky",
                        req(Map.of("flying", 1, "dragon", 1, "ghost", 1), 0, List.of()),
                        "minecraft:elytra", 1, 2.0, 50, 30),
                ritual("tide_caller", "Tide Caller",
                        req(Map.of("water", 1, "ice", 1), 0, List.of("kyogre", "suicune", "lugia")),
                        "minecraft:trident", 1, 5.0, 30, 0),
                ritual("soul_convergence", "Soul Convergence",
                        req(Map.of("ghost", 3, "dark", 1), 0, List.of("darkrai", "giratina")),
                        "minecraft:nether_star", 1, 1.5, 60, 40),
                ritual("dragons_hoard", "Dragon's Hoard",
                        req(Map.of("dragon", 2, "flying", 1), 0, List.of("rayquaza", "dialga", "palkia")),
                        "minecraft:dragon_egg", 1, 1.0, 80, 50)
        ));
    }

    private static TypeDrop td(String type, String item, double pct, int min, int max) {
        return new TypeDrop(type, item, pct, min, max);
    }

    private static Requirement req(Map<String, Integer> types, int distinct, List<String> sig) {
        return new Requirement(types, distinct, sig);
    }

    private static Ritual ritual(String id, String name, Requirement r, String item, int qty,
                                 double pct, int hard, int soft) {
        return new Ritual(id, name, true, r, item, qty, pct, hard, soft);
    }
}
