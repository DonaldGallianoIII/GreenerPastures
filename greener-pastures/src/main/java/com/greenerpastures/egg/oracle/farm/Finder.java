package com.greenerpastures.egg.oracle.farm;

/** Shared state for the in-world pasture finder: the species currently being located. */
public final class Finder {
    private Finder() {}

    /** Species name to highlight in-world; null = finder off. */
    public static volatile String target = null;
    /** Live readout for the HUD, updated by the world renderer. */
    public static volatile int lastCount = 0;
    public static volatile double nearestDist = -1;

    public static boolean active() {
        return target != null && !target.isBlank();
    }

    /** Click a species to find it; click the same one again to turn it off. */
    public static void toggle(String species) {
        if (species == null || species.isBlank()) return;
        target = species.equalsIgnoreCase(target) ? null : species;
        lastCount = 0;
        nearestDist = -1;
    }

    public static void clear() {
        target = null;
        lastCount = 0;
        nearestDist = -1;
    }
}
