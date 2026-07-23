package com.greenerpastures.display;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.entity.ai.pathing.EntityNavigation;

/**
 * The thin MC shell over {@link PatrolPath} (Display Suite v2 §3): it owns ONLY the projection entity and
 * the arrival check; every decision about which waypoint is next, how long to dwell, and when to reverse
 * lives in the headless core. One call per server tick per PATROL/STATIONARY resident.
 *
 * <p>Movement is issued through the mon's own navigation (so it pathfinds around the pen's terrain and
 * animates naturally), re-asserted only when the nav goes idle - which keeps re-issue cheap and lets a
 * dwell hold the mon still without us fighting it every tick.
 */
final class PatrolDriver {
    private PatrolDriver() {}

    /** "Close enough" to count as arrived, horizontally - y is ignored so a slope or the pen lip never
     *  strands the cursor mid-path. Tune in the feel-pass. */
    static final double ARRIVAL_RADIUS = 1.25;

    /**
     * Drive one resident's projection a single tick and return the advanced cursor. {@code (ox,oy,oz)} is
     * the path origin - the bottom-center of the block ABOVE the pen, where projections live and where the
     * GUI records waypoints relative to (so a RelPos of 0,0,0 is exactly the spawn spot).
     */
    static PatrolPath.Progress drive(double ox, double oy, double oz,
                                     PokemonEntity entity, PatrolPath path, PatrolPath.Progress progress) {
        RelPos wp = path.target(progress);
        if (wp == null) return progress;                       // empty path - caller falls back to wander
        double tx = ox + wp.dx();
        double ty = oy + wp.dy();
        double tz = oz + wp.dz();

        double dx = entity.getX() - tx, dz = entity.getZ() - tz;
        boolean atTarget = (dx * dx + dz * dz) <= ARRIVAL_RADIUS * ARRIVAL_RADIUS;

        EntityNavigation nav = entity.getNavigation();
        if (atTarget) {
            if (!nav.isIdle()) nav.stop();                     // arrived → hold for the dwell / next step
        } else if (nav.isIdle()) {
            nav.startMovingTo(tx, ty, tz, path.speed());       // (re)issue only when not already en route
        }
        return path.tick(progress, atTarget);
    }
}
