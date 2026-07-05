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
                hinted("feast_of_the_blade", "Feast of the Blade",
                        new Requirement(Map.of(), 0, List.of(),
                                Map.of("kartana", 1, "xerneas", 1, "meowth", 8)),
                        "minecraft:enchanted_golden_apple", 1, 2.0, 120, 60,
                        "A living blade, a giver of life, and eight greedy mouths at the feast."),
                // #2 — "Black Market": the classic Team Rocket lineup fences ILLICIT data. Sole farmable
                // source of the corruption orb (the Renderer breadcrumb at 1/2000 is the discovery hint).
                hinted("black_market", "Black Market",
                        new Requirement(Map.of(), 0, List.of(),
                                Map.of("koffing", 4, "ekans", 4, "meowth", 1)),
                        "greenerpastures:data_disk_rocket", 1, 2.5, 100, 50,
                        "Prepare for trouble. Make it double — twice. The cat takes his cut."),
                // #3 — "Professor's Summit" (Deuce, 2026-07-04): every starter from every generation, at least
                // once, across the UNION of TWO pastures (27 starters > 16 slots — the span is mechanically
                // forced, not flavor). Starters are ultra-rare overworld spawns here, so the collection IS the
                // grind; the payout is the only farmable Rare Candy source.
                spanHinted("professors_summit", "Professor's Summit",
                        new Requirement(Map.of(), 0, List.of(), ALL_STARTERS),
                        "cobblemon:rare_candy", 1, 3.0, 80, 40, 2,
                        "Every journey's first friend, from every land — and one field cannot hold them all."),

                // ── Batch 2 (Deuce's design session, 2026-07-05) — hinted hidden rituals. Tier grid:
                // LOW 5%/15/30 (~3/hr) · MID 2.5%/40/80 (~1.5/hr) · HIGH 1.2%/80/160 (~0.7/hr) ·
                // APEX 0.4-0.5%/150-300 (multi-hour). All at the 1-pull/min sweep cadence.
                hinted("nether_star", "Nether Star",
                        req(Map.of("hydreigon", 1, "marowak", 3, "yamask", 4)),
                        "minecraft:nether_star", 1, 0.4, 400, 200,
                        "Three heads. Three skulls. The restless dead."),
                hinted("wither_skull", "Wither Skeleton Skull",
                        req(Map.of("duskull", 3, "houndoom", 4, "marowak", 1)),
                        "minecraft:wither_skeleton_skull", 1, 2.5, 80, 40,
                        "The hounds guard what the dead wear."),
                hinted("elytra", "Elytra",
                        new Requirement(Map.of(), 0, List.of(),
                                Map.of("ninjask", 1, "shedinja", 1),
                                List.of(new Requirement.GroupCount(List.of("heracross", "pinsir"), 6))),
                        "minecraft:elytra", 1, 1.2, 160, 80,
                        "What the swift one leaves behind, the sky claims."),
                hinted("totem_of_undying", "Totem of Undying",
                        req(Map.of("sableye", 4, "blissey", 1)),
                        "minecraft:totem_of_undying", 1, 1.2, 160, 80,
                        "The gem-eyed ones guard the egg of second chances."),
                hinted("shulker_shell", "Shulker Shell",
                        req(Map.of("shuckle", 8)),
                        "minecraft:shulker_shell", 2, 2.5, 80, 40,
                        "Say it out loud."),
                hinted("trident", "Trident",
                        req(Map.of("dhelmise", 1, "jellicent", 2, "frillish", 4)),
                        "minecraft:trident", 1, 1.2, 160, 80,
                        "Where sailors drown, their weapons linger."),
                hinted("heart_of_the_sea", "Heart of the Sea",
                        req(Map.of("luvdisc", 8, "clamperl", 2, "lapras", 1)),
                        "minecraft:heart_of_the_sea", 1, 1.2, 160, 80,
                        "The gentle sea keeps its heart hidden among lovers and pearls."),
                hinted("echo_chorus", "Echo Shard",
                        req(Map.of("noibat", 2, "zubat", 2, "loudred", 2, "exploud", 1, "kricketune", 1)),
                        "minecraft:echo_shard", 2, 2.5, 80, 40,
                        "What screams in the dark leaves pieces behind."),
                hinted("saddle", "Saddle",
                        req(Map.of("mudsdale", 1, "zebstrika", 2, "ponyta", 4)),
                        "minecraft:saddle", 1, 5.0, 30, 15,
                        "The herd provides for its rider."),
                hinted("name_tag", "Name Tag",
                        req(Map.of("smeargle", 1, "chatot", 4)),
                        "minecraft:name_tag", 1, 5.0, 30, 15,
                        "The painter takes commissions."),
                hinted("slime_court", "Slime Court",
                        req(Map.of("goomy", 4, "shellos", 2, "ditto", 1, "goodra", 1)),
                        "minecraft:slime_ball", 4, 3.5, 60, 30,
                        "The dragon of slime holds court."),
                hinted("ominous_bottle", "Ominous Bottle",
                        req(Map.of("absol", 4, "honchkrow", 1, "meowth:alolan", 2, "persian:alolan", 1, "zigzagoon:galarian", 2)),
                        "minecraft:ominous_bottle", 1, 2.5, 80, 40,
                        "Misfortune, bottled at the source."),
                poolRitual("pasture_band", "Music Disc",
                        req(Map.of("chatot", 1, "kricketune", 4, "chingling", 4)),
                        MUSIC_DISCS, 2.5, 80, 40,
                        "The pasture band takes requests."),
                spanHinted("fossil_communion", "Sniffer Egg",
                        new Requirement(Map.of(), 0, List.of(), ALL_FOSSILS),
                        "minecraft:sniffer_egg", 1, 0.5, 300, 150, 2,
                        "The ancients recognize their own.")
        ));
    }

    /** Batch-2 fossil roster: base forms only, no evolutions, no Galar chimeras (Deuce's spec). */
    private static final Map<String, Integer> ALL_FOSSILS = Map.ofEntries(
            e("omanyte"), e("kabuto"), e("aerodactyl"), e("lileep"), e("anorith"),
            e("cranidos"), e("shieldon"), e("tirtouga"), e("archen"), e("tyrunt"), e("amaura"));

    /** The pasture band's set list — one uniform roll per hit. */
    private static final List<String> MUSIC_DISCS = List.of(
            "minecraft:music_disc_13", "minecraft:music_disc_cat", "minecraft:music_disc_blocks",
            "minecraft:music_disc_chirp", "minecraft:music_disc_far", "minecraft:music_disc_mall",
            "minecraft:music_disc_mellohi", "minecraft:music_disc_stal", "minecraft:music_disc_strad",
            "minecraft:music_disc_ward", "minecraft:music_disc_11", "minecraft:music_disc_wait",
            "minecraft:music_disc_otherside", "minecraft:music_disc_5", "minecraft:music_disc_pigstep",
            "minecraft:music_disc_relic", "minecraft:music_disc_precipice", "minecraft:music_disc_creator");

    private static Requirement req(Map<String, Integer> species) {
        return new Requirement(Map.of(), 0, List.of(), species);
    }

    private static Ritual hinted(String id, String name, Requirement r, String item, int qty,
                                 double pct, int hard, int soft, String hint) {
        return new Ritual(id, name, true, r, item, qty, pct, hard, soft, 1, hint, List.of());
    }

    private static Ritual poolRitual(String id, String name, Requirement r, List<String> pool,
                                     double pct, int hard, int soft, String hint) {
        return new Ritual(id, name, true, r, pool.get(0), 1, pct, hard, soft, 1, hint, pool);
    }

    private static Ritual spanHinted(String id, String name, Requirement r, String item, int qty,
                                     double pct, int hard, int soft, int span, String hint) {
        return new Ritual(id, name, true, r, item, qty, pct, hard, soft, span, hint, List.of());
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
