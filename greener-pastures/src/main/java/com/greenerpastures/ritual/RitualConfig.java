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
 * spent by hand. {@code rarityFactor} makes ritual pulls accrue THIS-many-times rarer than a typical staple
 * drop (default 3): per Harvester tick, per mon, the pull-proc = {@code (Harvester base proc + Drop Rate
 * augments/tethers) / rarityFactor}, and Drop Yield adds pulls-per-proc — so the SAME drop augments that speed
 * up staple drops also feed the gacha (and a richer/fuller pasture works the ritual faster). {@link #defaults()}
 * ships the RITUALS.md roster. {@link #load} is <b>fail-safe</b>: a missing
 * file is written from defaults; a corrupt file logs and falls back to defaults (it does NOT overwrite the
 * admin's file), and never crashes the server. Gson is loaded lazily (a holder) so the pure cores +
 * {@code defaults()} stay usable in the headless test JVM, which has no Gson on its runtime classpath.
 */
public record RitualConfig(boolean enabled, boolean autoPull, double rarityFactor,
                           TypeDropTable typeDrops, RitualBook rituals) {

    public RitualConfig {
        if (rarityFactor <= 0) rarityFactor = 3.0;   // rituals are 3× rarer than typical drops by default
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
        return new RitualConfig(true, true, 3.0, defaultTypeDrops(), defaultRituals());
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
                td("poison", "minecraft:nether_wart", 10, 1, 2),
                // Progression drops (Deuce, 2026-07-05): a mobless world has no ancient cities to loot — the
                // tether/daemon echo shards and the notebook/tether amethyst must be farmable from pastures.
                // Echo is deliberately the RAREST entry in the table (build a ghost farm, ~29/hr full mono).
                td("ghost", "minecraft:echo_shard", 3, 1, 1),
                td("dark", "minecraft:echo_shard", 2, 1, 1),
                td("fairy", "minecraft:amethyst_shard", 8, 1, 1),
                td("psychic", "minecraft:amethyst_shard", 5, 1, 1),
                td("rock", "minecraft:amethyst_shard", 5, 1, 1)
        ));
    }

    /** Rituals v2 — Deuce's HAND-DESIGNED hidden recipes (compositions are secret in-game until a player
     *  first assembles one; see RitualLedger). The launch book ships exactly one; more land here per design. */
    private static RitualBook defaultRituals() {
        return new RitualBook(true, List.of(
                // #1 — "Feast of the Blade": Kartana (the living katana) + Xerneas (the life-giver) + 8 Meowth
                // (the hoard) in ONE pasture → a chance per sweep at an enchanted golden apple. The ONLY
                // e-gapple source in the mod, deliberately locked behind two legendaries + a full retinue.
                ritual("feast_of_the_blade", "Feast of the Blade",
                        new Requirement(Map.of(), 0, List.of(),
                                Map.of("kartana", 1, "xerneas", 1, "meowth", 8)),
                        "minecraft:enchanted_golden_apple", 1, 2.0, 120, 60),
                // #2 — "Black Market": the classic Team Rocket lineup fences ILLICIT data. Sole farmable
                // source of the corruption orb (the Renderer breadcrumb at 1/2000 is the discovery hint).
                ritual("black_market", "Black Market",
                        new Requirement(Map.of(), 0, List.of(),
                                Map.of("koffing", 4, "ekans", 4, "meowth", 1)),
                        "greenerpastures:data_disk_rocket", 1, 2.5, 100, 50),
                // #3 — "Professor's Summit" (Deuce, 2026-07-04): every starter from every generation, at least
                // once, across the UNION of TWO pastures (27 starters > 16 slots — the span is mechanically
                // forced, not flavor). Starters are ultra-rare overworld spawns here, so the collection IS the
                // grind; the payout is the only farmable Rare Candy source.
                spanRitual("professors_summit", "Professor's Summit",
                        new Requirement(Map.of(), 0, List.of(), ALL_STARTERS),
                        "cobblemon:rare_candy", 1, 3.0, 80, 40, 2)
        ));
    }

    /** All 27 starters, Gens 1–9, one each — the Professor's Summit roster. */
    private static final Map<String, Integer> ALL_STARTERS = Map.ofEntries(
            e("bulbasaur"), e("charmander"), e("squirtle"),          // Kanto
            e("chikorita"), e("cyndaquil"), e("totodile"),           // Johto
            e("treecko"), e("torchic"), e("mudkip"),                 // Hoenn
            e("turtwig"), e("chimchar"), e("piplup"),                // Sinnoh
            e("snivy"), e("tepig"), e("oshawott"),                   // Unova
            e("chespin"), e("fennekin"), e("froakie"),               // Kalos
            e("rowlet"), e("litten"), e("popplio"),                  // Alola
            e("grookey"), e("scorbunny"), e("sobble"),               // Galar
            e("sprigatito"), e("fuecoco"), e("quaxly"));             // Paldea

    private static Map.Entry<String, Integer> e(String species) {
        return Map.entry(species, 1);
    }

    private static Ritual spanRitual(String id, String name, Requirement r, String item, int qty,
                                     double pct, int hard, int soft, int span) {
        return new Ritual(id, name, true, r, item, qty, pct, hard, soft, span);
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
