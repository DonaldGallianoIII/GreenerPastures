package com.greenerpastures.pasture.breeding;

import java.util.List;

/**
 * The Poké Ball ids behind the <b>Ball</b> selector augment - pick the ball a bred egg hatches in (collection /
 * aesthetic control breeders ask for). Same shape as {@link NatureCatalog}: the {@code BALL} augment stores a
 * 1-based index as its "level" ({@code 0} = off → vanilla "inherit mother's ball"), and this maps it to the
 * Cobblemon ball id string the egg spec wants.
 *
 * <p><b>Fully-namespaced ids</b> (e.g. {@code cobblemon:poke_ball}) so the egg spec resolves the ball
 * unambiguously - a bad/unknown id just lapses to the default ball at hatch (Cobblemon validates), never corrupts.
 * Ids are the real Cobblemon ball registry names (derived from {@code PokeBalls}); order is stable + append-only so
 * a stored index always means the same ball. The 16 {@code ancient_*} (Hisuian) balls are intentionally omitted
 * from the v1 list - append them later if wanted, they must go at the END to keep existing indices stable.
 */
public final class BallCatalog {
    private BallCatalog() {}

    private static final String NS = "cobblemon:";

    /** Curated, ordered ball ids. A BALL augment of level N (1-based) picks {@code BALLS.get(N-1)}. */
    public static final List<String> BALLS = List.of(
            NS + "poke_ball",   NS + "great_ball",  NS + "ultra_ball",  NS + "master_ball",
            NS + "premier_ball", NS + "heal_ball",  NS + "net_ball",    NS + "nest_ball",
            NS + "dive_ball",   NS + "dusk_ball",   NS + "timer_ball",  NS + "quick_ball",
            NS + "repeat_ball", NS + "luxury_ball", NS + "level_ball",  NS + "lure_ball",
            NS + "moon_ball",   NS + "friend_ball", NS + "love_ball",   NS + "beast_ball",
            NS + "dream_ball",  NS + "safari_ball", NS + "sport_ball",  NS + "park_ball",
            NS + "cherish_ball", NS + "fast_ball",  NS + "heavy_ball",
            NS + "azure_ball",  NS + "citrine_ball", NS + "roseate_ball", NS + "slate_ball", NS + "verdant_ball");

    public static int size() {
        return BALLS.size();
    }

    /** The ball id for a 1-based augment level, or {@code null} if off ({@code ≤0}) or past the catalog (no lock). */
    public static String byIndex(int level) {
        if (level <= 0 || level > BALLS.size()) return null;
        return BALLS.get(level - 1);
    }

    /** The 1-based index of a ball id (namespace optional, case-insensitive), or {@code 0} if unknown. */
    public static int indexOf(String ballId) {
        if (ballId == null) return 0;
        String want = ballId.trim().toLowerCase();
        if (want.indexOf(':') < 0) want = NS + want;          // accept a bare path too
        for (int i = 0; i < BALLS.size(); i++) {
            if (BALLS.get(i).equals(want)) return i + 1;
        }
        return 0;
    }
}
