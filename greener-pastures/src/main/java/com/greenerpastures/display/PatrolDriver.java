package com.greenerpastures.display;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.util.math.Vec3d;

/**
 * The thin MC shell over {@link PatrolPath} (Display Suite v2 §3): it owns ONLY the projection entity and
 * the arrival check; every decision about which waypoint is next, how long to dwell, and when to reverse
 * lives in the headless core. One call per server tick per PATROL/STATIONARY resident.
 *
 * <p><b>Why scripted, not navigation (2026-07-23):</b> a projection is a live {@link PokemonEntity} with
 * Cobblemon's own pasture-wander AI. Asking its navigation to walk our path meant our {@code startMovingTo}
 * and Cobblemon's wander fought each turn - "sometimes he walks the path, sometimes he wanders off"
 * (Deuce, live QA). An exhibit / gym / puzzle needs DETERMINISM, so for a patrolling resident we
 * {@link #takeControl take the wheel}: disable the mon's AI + gravity and step it straight toward the
 * current waypoint ourselves. Nothing competes; the same waypoints always produce the same walk.
 */
final class PatrolDriver {
    private PatrolDriver() {}

    /** "Close enough" to count as arrived, horizontally - y is ignored so an authored height difference or
     *  the pen lip never strands the cursor mid-path. Tune in the feel-pass. */
    static final double ARRIVAL_RADIUS = 1.0;

    /** {@link PatrolPath#speed} (0.2-0.5) → blocks per tick. 0.7 keeps "normal" (0.35) ≈ 0.245 b/t ≈ 4.9
     *  blocks/s, a natural walking pace; "slow"/"fast" scale around it. */
    private static final double SPEED_TO_BPT = 0.7;

    /** Disable the mon's brain + gravity so ONLY we move it - idempotent, called each driven tick. */
    static void takeControl(PokemonEntity mon) {
        if (!mon.isAiDisabled()) mon.setAiDisabled(true);
        if (!mon.hasNoGravity()) mon.setNoGravity(true);
    }

    /** Hand the mon back to Cobblemon (WANDER / empty patrol / mode switched away) - idempotent. */
    static void releaseControl(PokemonEntity mon) {
        if (mon.isAiDisabled()) mon.setAiDisabled(false);
        if (mon.hasNoGravity()) mon.setNoGravity(false);
    }

    /**
     * Drive one resident's projection a single tick and return the advanced cursor. {@code (ox,oy,oz)} is
     * the path origin - the bottom-center of the block ABOVE the pen, where projections live and where the
     * GUI records waypoints relative to (so a RelPos of 0,0,0 is exactly the spawn spot). Assumes
     * {@link #takeControl} was called for this mon.
     */
    static PatrolPath.Progress drive(double ox, double oy, double oz,
                                     PokemonEntity mon, PatrolPath path, PatrolPath.Progress progress) {
        RelPos wp = path.target(progress);
        if (wp == null) return progress;                        // empty path - caller releases + wanders
        double tx = ox + wp.dx(), ty = oy + wp.dy(), tz = oz + wp.dz();

        double dx = tx - mon.getX(), dz = tz - mon.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        boolean atTarget = horiz <= ARRIVAL_RADIUS;

        mon.setVelocity(Vec3d.ZERO);                            // we own position; keep vanilla physics quiet
        if (!atTarget && horiz > 1.0e-4) {
            double dy = ty - mon.getY();
            double dist3d = Math.sqrt(horiz * horiz + dy * dy);
            double per = Math.min(dist3d, path.speed() * SPEED_TO_BPT);
            double nx = mon.getX() + dx / dist3d * per;
            double ny = mon.getY() + dy / dist3d * per;
            double nz = mon.getZ() + dz / dist3d * per;
            float yaw = (float) Math.toDegrees(Math.atan2(-(dx), dz));   // face the way we're walking
            mon.setPosition(nx, ny, nz);
            mon.setYaw(yaw);
            mon.setBodyYaw(yaw);
            mon.setHeadYaw(yaw);
        }
        return path.tick(progress, atTarget);
    }
}
