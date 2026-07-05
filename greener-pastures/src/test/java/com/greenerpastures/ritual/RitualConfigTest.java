package com.greenerpastures.ritual;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the config DEFAULTS + master gating. Deliberately never calls {@code load/save} so Gson
 * (lazy in {@link RitualConfig}) is never loaded — the test JVM has no Gson on its runtime classpath. JSON
 * round-trip is verified in-game (QA).
 */
class RitualConfigTest {

    @Test
    void defaultsAreWellFormed() {
        RitualConfig c = RitualConfig.defaults();
        assertTrue(c.enabled());
        assertTrue(c.rituals().enabled());
        assertFalse(c.rituals().rituals().isEmpty(), "ships a ritual roster");
        for (Ritual r : c.rituals().rituals()) {
            assertTrue(r.outputItem().contains(":"), r.id() + " output should be a namespaced item id");
            assertTrue(r.hardPity() >= 1, r.id() + " needs a hard pity");
            assertTrue(r.baseChancePercent() >= 0 && r.baseChancePercent() <= 100, r.id() + " chance in range");
        }
        assertTrue(c.typeDrops().enabled());
        assertFalse(c.typeDrops().drops().isEmpty(), "ships type-drops");
    }

    @Test
    void masterToggleGatesBothTiersButKeepsTheData() {
        RitualConfig def = RitualConfig.defaults();
        RitualConfig off = new RitualConfig(false, true, 3.0, def.typeDrops(), def.rituals());
        assertFalse(off.activeRituals().enabled(), "master off ⇒ rituals inert");
        assertFalse(off.activeTypeDrops().enabled(), "master off ⇒ type-drops inert");
        assertFalse(off.rituals().rituals().isEmpty(), "data preserved so flipping master back on restores it");
    }

    @Test
    void signatureGateStillWorks() {
        // The signature ("any of these species present") gate — kept for future hand-designed rituals.
        Ritual r = new Ritual("sig", "Sig", true,
                new Requirement(Map.of("fairy", 1, "ghost", 1), 0, List.of("sableye")),
                "minecraft:totem_of_undying", 1, 4.0, 35, 0);
        Composition noSableye = new Composition(Map.of("fairy", 1, "ghost", 1), Set.of("gengar"));
        Composition withSableye = new Composition(Map.of("fairy", 1, "ghost", 1), Set.of("sableye"));
        assertFalse(r.requirement().satisfiedBy(noSableye), "no Sableye ⇒ no totem");
        assertTrue(r.requirement().satisfiedBy(withSableye), "Sableye unlocks the totem ritual");
    }

    @Test
    void feastOfTheBladeNeedsTheExactRetinue() {
        // Deuce's first hand-designed hidden ritual: Kartana + Xerneas + 8 Meowth in ONE pasture.
        Ritual feast = RitualConfig.defaults().rituals().byId("feast_of_the_blade");
        assertNotNull(feast);
        assertTrue(feast.outputItem().equals("minecraft:enchanted_golden_apple"));

        Composition sevenMeowths = new Composition(Map.of(), Set.of(),
                Map.of("kartana", 1, "xerneas", 1, "meowth", 7));
        Composition exact = new Composition(Map.of(), Set.of(),
                Map.of("kartana", 1, "xerneas", 1, "meowth", 8));
        Composition noXerneas = new Composition(Map.of(), Set.of(),
                Map.of("kartana", 1, "meowth", 8));
        Composition overfilled = new Composition(Map.of(), Set.of(),
                Map.of("kartana", 2, "xerneas", 1, "meowth", 9, "ditto", 3));

        assertFalse(feast.requirement().satisfiedBy(sevenMeowths), "7 Meowth is not 8");
        assertFalse(feast.requirement().satisfiedBy(noXerneas), "no Xerneas ⇒ no feast");
        assertTrue(feast.requirement().satisfiedBy(exact), "the exact retinue forms the ritual");
        assertTrue(feast.requirement().satisfiedBy(overfilled), "minimums — extras don't break it");
    }

    @Test
    void professorsSummitSpansTwoPastures() {
        // Deuce's spanning ritual (2026-07-04): all 27 starters at least once across the UNION of two pastures.
        Ritual summit = RitualConfig.defaults().rituals().byId("professors_summit");
        assertNotNull(summit);
        assertTrue(summit.pastureSpan() == 2 && summit.outputItem().equals("cobblemon:rare_candy"));

        Map<String, Integer> gen1to5 = new java.util.HashMap<>();
        for (String sp : List.of("bulbasaur", "charmander", "squirtle", "chikorita", "cyndaquil", "totodile",
                "treecko", "torchic", "mudkip", "turtwig", "chimchar", "piplup", "snivy", "tepig", "oshawott"))
            gen1to5.put(sp, 1);
        Map<String, Integer> gen6to9 = new java.util.HashMap<>();
        for (String sp : List.of("chespin", "fennekin", "froakie", "rowlet", "litten", "popplio",
                "grookey", "scorbunny", "sobble", "sprigatito", "fuecoco", "quaxly"))
            gen6to9.put(sp, 1);
        Composition a = new Composition(Map.of(), Set.of(), gen1to5);   // 15 mons — fits a pasture
        Composition b = new Composition(Map.of(), Set.of(), gen6to9);   // 12 mons — fits a pasture

        assertFalse(summit.requirement().satisfiedBy(a), "one pasture alone can never hold 27 starters");
        assertFalse(summit.requirement().satisfiedBy(b));
        assertTrue(summit.requirement().satisfiedBy(Composition.union(a, b)), "the union completes the summit");

        Map<String, Integer> missingOne = new java.util.HashMap<>(gen6to9);
        missingOne.remove("quaxly");
        assertFalse(summit.requirement().satisfiedBy(Composition.union(a, new Composition(Map.of(), Set.of(), missingOne))),
                "26 of 27 is not a summit");
    }

    @Test
    void spanDefaultsToOneAndSingleCompRitualsIgnoreSpanning() {
        // Pre-span JSON deserializes with pastureSpan 0 → the record clamps to 1 (classic behavior unchanged).
        Ritual legacy = new Ritual("x", "X", true, new Requirement(Map.of(), 0, List.of(), Map.of("meowth", 1)),
                "minecraft:stone", 1, 5.0, 10, 0, 0);
        assertTrue(legacy.pastureSpan() == 1);
        // active(comp) never returns spanning rituals; spanning() never returns single-pasture ones.
        RitualBook book = RitualConfig.defaults().rituals();
        Composition all = new Composition(Map.of(), Set.of(), Map.of("meowth", 99, "kartana", 9, "xerneas", 9,
                "koffing", 9, "ekans", 9));
        assertTrue(book.active(all).stream().noneMatch(r -> r.pastureSpan() > 1));
        assertTrue(book.spanning().stream().allMatch(r -> r.pastureSpan() > 1));
        assertTrue(book.spanning().stream().anyMatch(r -> r.id().equals("professors_summit")));
    }

    @Test
    void spanGateBanksExactlyOncePerSatisfiedPair() {
        // Pair (100, 200) satisfied: 100 banks (has a larger partner), 200 does not — one pull per sweep total.
        assertTrue(SpanGate.shouldBank(100L, List.of(200L)));
        assertFalse(SpanGate.shouldBank(200L, List.of(100L)));
        // Three-way (100 pairs with 200 AND 300): still exactly one banker (100), one pull per sweep.
        assertTrue(SpanGate.shouldBank(100L, List.of(200L, 300L)));
        assertFalse(SpanGate.shouldBank(200L, List.of(100L)));
        assertFalse(SpanGate.shouldBank(300L, List.of(100L)));
        assertFalse(SpanGate.shouldBank(100L, List.of()), "no satisfying partner → no bank");
    }

    @Test
    void progressionDropsShipAndMergeIntoExistingTables() {
        // Echo/amethyst (2026-07-05): the mobless-world progression gate — tether/daemon need echo shards.
        TypeDropTable defs = RitualConfig.defaults().typeDrops();
        assertTrue(defs.drops().stream().anyMatch(d -> d.type().equals("ghost") && d.item().equals("minecraft:echo_shard")));
        assertTrue(defs.drops().stream().anyMatch(d -> d.type().equals("fairy") && d.item().equals("minecraft:amethyst_shard")));
        // echo is the rarest entry in the whole table — the deliberate balance anchor
        double echoMax = defs.drops().stream().filter(d -> d.item().equals("minecraft:echo_shard"))
                .mapToDouble(TypeDrop::chancePercent).max().orElse(99);
        assertTrue(defs.drops().stream().filter(d -> !d.item().equals("minecraft:echo_shard"))
                .allMatch(d -> d.chancePercent() >= echoMax), "nothing common may be rarer than echo");

        // merge: an admin file WITHOUT the new entries gains exactly them; tuned existing entries survive
        TypeDropTable adminFile = new TypeDropTable(true, java.util.List.of(
                new TypeDrop("fire", "minecraft:blaze_rod", 99.0, 1, 1),      // admin cranked this — must survive
                new TypeDrop("ghost", "minecraft:echo_shard", 0.5, 1, 1)));   // admin nerfed echo — must survive
        TypeDropTable merged = adminFile.mergeMissingDefaults(defs);
        assertEquals(99.0, merged.drops().stream().filter(d -> d.item().equals("minecraft:blaze_rod"))
                .findFirst().orElseThrow().chancePercent(), 1e-9);
        assertEquals(0.5, merged.drops().stream().filter(d -> d.type().equals("ghost") && d.item().equals("minecraft:echo_shard"))
                .findFirst().orElseThrow().chancePercent(), 1e-9, "admin's echo tune wins over the default");
        assertTrue(merged.drops().stream().anyMatch(d -> d.type().equals("dark") && d.item().equals("minecraft:echo_shard")),
                "missing defaults appended");
        assertTrue(merged.drops().size() > adminFile.drops().size());

        // idempotent: merging a complete table returns the SAME instance (no save churn)
        assertSame(defs, defs.mergeMissingDefaults(defs));
    }

    @Test
    void batch2ShipsFourteenHintedRituals() {
        RitualBook book = RitualConfig.defaults().rituals();
        String[] ids = {"nether_star", "wither_skull", "elytra", "totem_of_undying", "shulker_shell",
                "trident", "heart_of_the_sea", "echo_chorus", "saddle", "name_tag", "slime_court",
                "ominous_bottle", "pasture_band", "fossil_communion"};
        for (String id : ids) {
            Ritual r = book.byId(id);
            assertNotNull(r, id + " missing");
            assertFalse(r.hint().isEmpty(), id + " needs a hint — hints ARE the discovery design");
        }
        // supply chain: the wither skull trickles faster than the star it feeds
        assertTrue(book.byId("wither_skull").baseChancePercent() > book.byId("nether_star").baseChancePercent());
        // apex pacing: nether star + fossils are the slowest things in the book
        assertTrue(book.byId("nether_star").baseChancePercent() <= 0.5);
        assertTrue(book.byId("fossil_communion").pastureSpan() == 2, "fossils span pastures like the Summit");
        // the OG three got their riddles too (Deuce approved A/A/A, 2026-07-05)
        assertTrue(book.byId("feast_of_the_blade").hint().contains("living blade"));
        assertTrue(book.byId("black_market").hint().contains("Prepare for trouble"));
        assertTrue(book.byId("professors_summit").hint().contains("first friend"));
    }

    @Test
    void groupGateCountsAnyMix() {
        Ritual elytra = RitualConfig.defaults().rituals().byId("elytra");
        Composition allHeracross = new Composition(Map.of(), Set.of(),
                Map.of("ninjask", 1, "shedinja", 1, "heracross", 6));
        Composition mixed = new Composition(Map.of(), Set.of(),
                Map.of("ninjask", 1, "shedinja", 1, "heracross", 3, "pinsir", 3));
        Composition fiveBeetles = new Composition(Map.of(), Set.of(),
                Map.of("ninjask", 1, "shedinja", 1, "heracross", 2, "pinsir", 3));
        assertTrue(elytra.requirement().satisfiedBy(allHeracross), "6 heracross alone is a valid mix");
        assertTrue(elytra.requirement().satisfiedBy(mixed), "3+3 is a valid mix");
        assertFalse(elytra.requirement().satisfiedBy(fiveBeetles), "5 beetles is not 6");
    }

    @Test
    void formQualifiedKeysGateTheOminousBottle() {
        Ritual bottle = RitualConfig.defaults().rituals().byId("ominous_bottle");
        Composition kantoCrew = new Composition(Map.of(), Set.of(), Map.of(
                "absol", 4, "honchkrow", 1, "meowth", 2, "persian", 1, "zigzagoon", 2));
        Composition alolanCrew = new Composition(Map.of(), Set.of(), Map.of(
                "absol", 4, "honchkrow", 1, "meowth", 2, "meowth:alolan", 2,
                "persian", 1, "persian:alolan", 1, "zigzagoon", 2, "zigzagoon:galarian", 2));
        assertFalse(bottle.requirement().satisfiedBy(kantoCrew), "Kanto strays can't bottle Alolan misfortune");
        assertTrue(bottle.requirement().satisfiedBy(alolanCrew), "the real syndicate (reader emits form keys alongside base)");
    }

    @Test
    void outputPoolRollsUniformlyAndFallsBackToFixed() {
        Ritual band = RitualConfig.defaults().rituals().byId("pasture_band");
        assertFalse(band.outputPool().isEmpty());
        Set<String> seen = new java.util.HashSet<>();
        java.util.Random rng = new java.util.Random(42);
        for (int i = 0; i < 500; i++) seen.add(band.rollOutput(rng::nextDouble));
        assertEquals(band.outputPool().size(), seen.size(), "500 rolls hit every disc in the set list");
        Ritual fixedOut = RitualConfig.defaults().rituals().byId("saddle");
        assertEquals("minecraft:saddle", fixedOut.rollOutput(rng::nextDouble), "no pool → fixed output, always");
    }

    @Test
    void speciesCountsAreCaseInsensitiveAndDefaultEmpty() {
        Composition c = new Composition(Map.of(), Set.of(), Map.of("Meowth", 8));
        assertTrue(c.countOfSpecies("meowth") == 8 && c.countOfSpecies("MEOWTH") == 8);
        Composition legacy = new Composition(Map.of("fire", 1), Set.of("meowth"));   // 2-arg compat shape
        assertTrue(legacy.countOfSpecies("meowth") == 0, "legacy shape has no counts — count gates just fail closed");
        Requirement legacyReq = new Requirement(Map.of("fire", 1), 0, List.of());    // 3-arg compat shape
        assertTrue(legacyReq.satisfiedBy(legacy), "requirements without count gates are unaffected");
    }
}
