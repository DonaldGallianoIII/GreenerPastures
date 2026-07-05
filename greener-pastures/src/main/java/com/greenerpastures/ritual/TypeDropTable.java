package com.greenerpastures.ritual;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The Tier-1 type-drop roster (from config). {@code enabled=false} switches the whole type-drop tier off.
 * {@link #forTypes} returns the drops whose type is present in a pasture. Pure + tested.
 */
public record TypeDropTable(boolean enabled, List<TypeDrop> drops) {

    public TypeDropTable {
        drops = drops == null ? List.of() : List.copyOf(drops);
    }

    /** New DEFAULT drops must reach existing config files without clobbering admin edits (same contract as
     *  the hand-designed-ritual merge): any default whose (type, item) pair is absent from this table is
     *  appended; everything the admin wrote - including tuned rates for pairs that DO exist - is preserved.
     *  Returns {@code this} when nothing was missing. Pure + tested. */
    public TypeDropTable mergeMissingDefaults(TypeDropTable defaults) {
        if (defaults == null || defaults.drops.isEmpty()) return this;
        List<TypeDrop> merged = null;
        for (TypeDrop def : defaults.drops) {
            boolean present = false;
            for (TypeDrop mine : drops) {
                if (mine.type().equals(def.type()) && mine.item().equals(def.item())) { present = true; break; }
            }
            if (present) continue;
            if (merged == null) merged = new ArrayList<>(drops);
            merged.add(def);
        }
        return merged == null ? this : new TypeDropTable(enabled, merged);
    }

    /** The type-drops whose {@code type} is in {@code presentTypes} (lowercased). Empty when disabled. */
    public List<TypeDrop> forTypes(Set<String> presentTypes) {
        if (!enabled || presentTypes == null) return List.of();
        List<TypeDrop> out = new ArrayList<>();
        for (TypeDrop d : drops) if (presentTypes.contains(d.type())) out.add(d);
        return out;
    }
}
