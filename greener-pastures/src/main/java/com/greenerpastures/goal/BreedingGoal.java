package com.greenerpastures.goal;

import com.greenerpastures.biobank.EggSummary;

/**
 * A breeding target a player is hunting - e.g. "a shiny Gible with ≥4 perfect IVs". Matched against an
 * {@link EggSummary} (the MC-free egg facts read off an egg). <b>Every criterion is optional</b>: a null / zero
 * field means "don't care", so an empty goal matches everything and the player tightens it as far as they like.
 * Pure data → unit-tested; the MC layer ({@code goal/} command + the egg observer) builds + stores these.
 *
 * @param species       required species key (lowercase), or {@code null} for any
 * @param shiny         required shininess ({@code TRUE} = must be shiny, {@code FALSE} = must be non-shiny, {@code null} = either)
 * @param minPerfectIvs minimum count of 31-IV stats (0 = any), clamped to 0..6
 * @param minIvTotal    minimum sum of the 6 IVs (0 = any)
 * @param count         how many matching eggs the hunt wants (≥1)
 */
public record BreedingGoal(String species, Boolean shiny, int minPerfectIvs, int minIvTotal, int count) {

    public BreedingGoal {
        if (minPerfectIvs < 0) minPerfectIvs = 0;
        if (minPerfectIvs > 6) minPerfectIvs = 6;
        if (minIvTotal < 0) minIvTotal = 0;
        if (count < 1) count = 1;
        if (species != null) {
            String s = species.trim().toLowerCase();
            species = s.isEmpty() ? null : s;
        }
    }

    /** Does this egg satisfy every set criterion? */
    public boolean matches(EggSummary egg) {
        if (egg == null) return false;
        if (species != null && !species.equalsIgnoreCase(egg.species())) return false;
        if (shiny != null && shiny != egg.shiny()) return false;
        if (egg.perfectIvs() < minPerfectIvs) return false;
        return egg.ivTotal() >= minIvTotal;
    }

    /** A short human description for the {@code /gp goal} readout. */
    public String describe() {
        StringBuilder b = new StringBuilder();
        if (Boolean.TRUE.equals(shiny)) b.append("shiny ");
        else if (Boolean.FALSE.equals(shiny)) b.append("non-shiny ");
        b.append(species == null ? "any species" : species);
        if (minPerfectIvs > 0) b.append(", ≥").append(minPerfectIvs).append(" perfect IVs");
        if (minIvTotal > 0) b.append(", ≥").append(minIvTotal).append(" IV total");
        if (count > 1) b.append(" ×").append(count);
        return b.toString();
    }
}
