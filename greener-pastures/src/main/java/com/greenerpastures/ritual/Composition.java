package com.greenerpastures.ritual;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A snapshot of WHAT a pasture currently holds, in the Minecraft-free terms the ritual engine reasons about:
 * how many tethered mons carry each elemental type, and which species are present. Type / species keys are
 * lowercased so config and runtime compare cleanly. Built by the (MC-side) composition reader from Cobblemon
 * types; consumed by {@link Requirement} (rituals) and {@link TypeDropTable} (type-drops). Pure + tested.
 */
public record Composition(Map<String, Integer> typeCounts, Set<String> species, Map<String, Integer> speciesCounts) {
    public static final Composition EMPTY = new Composition(Map.of(), Set.of());

    /** Compat shape (typeCounts + species set) - species COUNTS default empty; count-gated rituals
     *  (e.g. "8 Meowth") need the 3-arg form the composition reader builds. */
    public Composition(Map<String, Integer> typeCounts, Set<String> species) {
        this(typeCounts, species, Map.of());
    }

    public Composition {
        Map<String, Integer> t = new HashMap<>();
        if (typeCounts != null) {
            typeCounts.forEach((k, v) -> {
                if (k != null && v != null && v > 0) t.put(k.toLowerCase(Locale.ROOT), v);
            });
        }
        Set<String> s = new HashSet<>();
        if (species != null) {
            for (String sp : species) if (sp != null && !sp.isBlank()) s.add(sp.toLowerCase(Locale.ROOT));
        }
        Map<String, Integer> sc = new HashMap<>();
        if (speciesCounts != null) {
            speciesCounts.forEach((k, v) -> {
                if (k != null && v != null && v > 0) sc.put(k.toLowerCase(Locale.ROOT), v);
            });
        }
        typeCounts = Map.copyOf(t);
        species = Set.copyOf(s);
        speciesCounts = Map.copyOf(sc);
    }

    /** How many tethered mons ARE this species (0 if absent) - the count-gate rituals check. */
    public int countOfSpecies(String s) {
        return s == null ? 0 : speciesCounts.getOrDefault(s.toLowerCase(Locale.ROOT), 0);
    }

    public int countOfType(String type) {
        return type == null ? 0 : typeCounts.getOrDefault(type.toLowerCase(Locale.ROOT), 0);
    }

    public int distinctTypes() { return typeCounts.size(); }

    public boolean hasSpecies(String s) {
        return s != null && species.contains(s.toLowerCase(Locale.ROOT));
    }

    /** The set of types present (lowercased) - what {@link TypeDropTable#forTypes} keys on. */
    public Set<String> types() { return typeCounts.keySet(); }

    /** The combined composition of two pastures - spanning rituals (pastureSpan 2) evaluate against this:
     *  type/species counts sum, species presence unions. Pure; either side null = the other unchanged. */
    public static Composition union(Composition a, Composition b) {
        if (a == null) return b == null ? EMPTY : b;
        if (b == null) return a;
        Map<String, Integer> t = new HashMap<>(a.typeCounts);
        b.typeCounts.forEach((k, v) -> t.merge(k, v, Integer::sum));
        Set<String> s = new HashSet<>(a.species);
        s.addAll(b.species);
        Map<String, Integer> sc = new HashMap<>(a.speciesCounts);
        b.speciesCounts.forEach((k, v) -> sc.merge(k, v, Integer::sum));
        return new Composition(t, s, sc);
    }
}
