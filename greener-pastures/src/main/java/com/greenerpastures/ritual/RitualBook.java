package com.greenerpastures.ritual;

import java.util.ArrayList;
import java.util.List;

/**
 * The roster of {@link Ritual}s (from config). {@code enabled=false} switches the whole ritual tier off in
 * one move. {@link #active(Composition)} returns the enabled rituals a pasture currently satisfies — what the
 * Harvester banks pulls toward. Pure + tested.
 */
public record RitualBook(boolean enabled, List<Ritual> rituals) {

    public RitualBook {
        rituals = rituals == null ? List.of() : List.copyOf(rituals);
    }

    public List<Ritual> active(Composition composition) {
        if (!enabled) return List.of();
        List<Ritual> out = new ArrayList<>();
        for (Ritual r : rituals) {
            if (r != null && r.enabled() && r.requirement().satisfiedBy(composition)) out.add(r);
        }
        return out;
    }

    public Ritual byId(String id) {
        if (id == null) return null;
        for (Ritual r : rituals) if (id.equals(r.id())) return r;
        return null;
    }
}
