package com.greenerpastures.pasture.breeding;

import java.util.Set;

/**
 * Pure breeding-compatibility rules (BUG-006) — decides whether two parents can produce an egg, mirroring
 * Cobblemon/Cobbreeding's ruleset, so the Daemon graph can validate a wired pair at <i>design time</i> (red wire +
 * a reason tooltip) instead of silently accepting a dead pair that never lays. MC-free + headless-tested; a thin
 * adapter (built with the graph UI) reads egg groups / gender / "is Ditto" off a Cobblemon {@code Pokemon} and maps
 * them onto {@link Parent}.
 *
 * <p>The four rules — all must allow it:
 * <ol>
 *   <li><b>Shared egg group</b> — the parents intersect on ≥1 (non-Undiscovered) egg group;</li>
 *   <li><b>Gender</b> — one ♂ + one ♀ (Ditto bypasses; genderless mons can only go through Ditto);</li>
 *   <li><b>Ditto special case</b> — Ditto breeds with anything <i>except</i> another Ditto (and Undiscovered);</li>
 *   <li><b>Undiscovered exclusion</b> — a mon in the Undiscovered ("No Eggs") group can't breed at all.</li>
 * </ol>
 */
public final class BreedingCompat {
    private BreedingCompat() {}

    /** Cobblemon's egg-group id for unbreedable species. The adapter normalises to this. */
    public static final String UNDISCOVERED = "undiscovered";

    public enum Gender { MALE, FEMALE, GENDERLESS }

    /** The breeding-relevant facts about one parent (read off a Cobblemon {@code Pokemon} by the adapter). */
    public record Parent(Set<String> eggGroups, Gender gender, boolean ditto) {
        /** A mon that can't breed at all: no egg groups, or in the Undiscovered group. */
        public boolean undiscovered() {
            return eggGroups == null || eggGroups.isEmpty() || eggGroups.contains(UNDISCOVERED);
        }
    }

    /** True iff this pair can produce an egg. Order-independent. */
    public static boolean canBreed(Parent a, Parent b) {
        return reason(a, b) == null;
    }

    /**
     * A short, human-readable reason the pair <i>can't</i> breed (for the graph's incompatible-wire tooltip), or
     * {@code null} if they can. Order-independent.
     */
    public static String reason(Parent a, Parent b) {
        if (a == null || b == null) return "missing parent";
        if (a.undiscovered() || b.undiscovered()) return "an Undiscovered-group mon can't breed";
        if (a.ditto() && b.ditto()) return "two Ditto can't breed";
        if (a.ditto() || b.ditto()) return null;                       // rule 3: Ditto + anything breedable
        if (!sharesEggGroup(a, b)) return "no shared egg group";       // rule 1
        if (!oppositeGenders(a.gender(), b.gender())) return "need one ♂ + one ♀ (or a Ditto)";   // rule 2
        return null;
    }

    private static boolean sharesEggGroup(Parent a, Parent b) {
        for (String g : a.eggGroups()) {
            if (!UNDISCOVERED.equals(g) && b.eggGroups().contains(g)) return true;
        }
        return false;
    }

    private static boolean oppositeGenders(Gender x, Gender y) {
        return (x == Gender.MALE && y == Gender.FEMALE) || (x == Gender.FEMALE && y == Gender.MALE);
    }
}
