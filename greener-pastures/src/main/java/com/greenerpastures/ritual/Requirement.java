package com.greenerpastures.ritual;

import java.util.List;
import java.util.Map;

/**
 * What a pasture's {@link Composition} must satisfy to activate a ritual: a minimum count per elemental type,
 * a minimum number of DISTINCT types present, and (for the rarest) a signature-species gate — at least ONE of
 * the listed species must be in the pasture. Any field may be empty/zero (= no constraint). Pure + tested.
 */
public record Requirement(Map<String, Integer> typeMinCounts, int minDistinctTypes,
                          List<String> signatureSpeciesAnyOf) {

    public Requirement {
        typeMinCounts = typeMinCounts == null ? Map.of() : Map.copyOf(typeMinCounts);
        signatureSpeciesAnyOf = signatureSpeciesAnyOf == null ? List.of() : List.copyOf(signatureSpeciesAnyOf);
        minDistinctTypes = Math.max(0, minDistinctTypes);
    }

    public boolean satisfiedBy(Composition c) {
        if (c == null) return false;
        if (c.distinctTypes() < minDistinctTypes) return false;
        for (Map.Entry<String, Integer> e : typeMinCounts.entrySet()) {
            if (c.countOfType(e.getKey()) < e.getValue()) return false;
        }
        if (!signatureSpeciesAnyOf.isEmpty()) {
            boolean any = false;
            for (String sp : signatureSpeciesAnyOf) {
                if (c.hasSpecies(sp)) { any = true; break; }
            }
            if (!any) return false;
        }
        return true;
    }
}
