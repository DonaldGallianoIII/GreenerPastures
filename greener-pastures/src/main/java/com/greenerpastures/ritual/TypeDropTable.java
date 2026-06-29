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

    /** The type-drops whose {@code type} is in {@code presentTypes} (lowercased). Empty when disabled. */
    public List<TypeDrop> forTypes(Set<String> presentTypes) {
        if (!enabled || presentTypes == null) return List.of();
        List<TypeDrop> out = new ArrayList<>();
        for (TypeDrop d : drops) if (presentTypes.contains(d.type())) out.add(d);
        return out;
    }
}
