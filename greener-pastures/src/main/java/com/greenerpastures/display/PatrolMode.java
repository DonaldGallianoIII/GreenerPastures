package com.greenerpastures.display;

/**
 * How a single Exhibit Pen resident moves (Display Suite v2 §3). Per-resident, author-chosen in the
 * Notebook display tab. Only {@link #PATROL} consults {@link PatrolPath}; the other two are handled by
 * the thin MC goal directly.
 */
public enum PatrolMode {
    /** Cobblemon's stock tether wander - the v1 behavior, and the fallback whenever a patrol has no
     *  waypoints yet. */
    WANDER,
    /** Walk the authored {@link PatrolPath} waypoints (loop or ping-pong, dwelling at each). */
    PATROL,
    /** Stand at the first waypoint (or home) and hold a facing - a greeter. */
    STATIONARY;

    /** Parse from stored/GUI text; unknown or null falls back to {@link #WANDER} (the safe default). */
    public static PatrolMode fromId(String id) {
        if (id == null) return WANDER;
        for (PatrolMode m : values()) {
            if (m.name().equalsIgnoreCase(id)) return m;
        }
        return WANDER;
    }
}
