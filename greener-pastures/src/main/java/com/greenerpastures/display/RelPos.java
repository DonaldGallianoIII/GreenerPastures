package com.greenerpastures.display;

/**
 * A waypoint stored <b>relative to the Exhibit Pen block</b> (Display Suite v2 §3 - patrol pathing).
 * The GUI's "record" UX captures the player's own position minus the block origin, so a path survives
 * being authored once and then re-projected anywhere the pen moves - it is always block-local.
 *
 * <p>Immutable + sanitizing like {@link StatueTransform}: whatever comes out of NBT lands back inside a
 * sane leash radius, so the MC goal can trust any instance. Units are blocks (the pen origin is 0,0,0).
 */
public record RelPos(double dx, double dy, double dz) {

    /** No waypoint may sit further than this (blocks) from the pen - a projection is leashed anyway
     *  (Cobblemon's pasture wander), so a path point beyond it could never be reached. */
    public static final double MAX_RANGE = 64.0;

    public RelPos {
        dx = clamp(dx);
        dy = clamp(dy);
        dz = clamp(dz);
    }

    /** Squared distance from this waypoint to a world-relative point (caller passes block-local coords). */
    public double distanceSqTo(double x, double y, double z) {
        double ex = x - dx, ey = y - dy, ez = z - dz;
        return ex * ex + ey * ey + ez * ez;
    }

    private static double clamp(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(-MAX_RANGE, Math.min(MAX_RANGE, v));
    }
}
