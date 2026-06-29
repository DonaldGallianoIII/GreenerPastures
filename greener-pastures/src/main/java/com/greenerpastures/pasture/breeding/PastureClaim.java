package com.greenerpastures.pasture.breeding;

import java.util.UUID;

/**
 * The pasture <b>operator claim</b> — who pays the Soul-Tether Data cost. A deliberate locked-boolean
 * toggle (Deuce's model, 2026-06-28): pastures are shared (a group drops one or two and everyone adds
 * their mons), so the payer is set by an EXPLICIT click, never implicitly by slotting a tether. Pure +
 * unit-tested; the C2S {@code ClaimOperatorPayload} drives it and the breeder drains the claimed owner.
 *
 * <p>The box behaves like a lock:
 * <ul>
 *   <li><b>free</b> (no owner) → a click <b>claims</b> it: it locks ON to you.</li>
 *   <li><b>yours</b> → a click <b>releases</b> it: it unlocks, free for the next person.</li>
 *   <li><b>someone else's</b> → a click is <b>rejected</b> — only the owner can release their lock.</li>
 * </ul>
 * No owner ⇒ tethers stay inert (the free base); the operator's Data only pays while they hold the lock.
 */
public final class PastureClaim {
    private PastureClaim() {}

    public enum Outcome { CLAIMED, RELEASED, LOCKED_BY_OTHER }

    /** {@code owner} = the resulting operator (null = free); {@code outcome} = what the click did. */
    public record Result(UUID owner, Outcome outcome) {
        /** True unless the click was rejected (the box is locked by someone else). */
        public boolean changed() { return outcome != Outcome.LOCKED_BY_OTHER; }
    }

    /** Toggle the operator box for {@code clicker} against the pasture's {@code currentOwner}. */
    public static Result toggle(UUID currentOwner, UUID clicker) {
        if (clicker == null) return new Result(currentOwner, Outcome.LOCKED_BY_OTHER);
        if (currentOwner == null) return new Result(clicker, Outcome.CLAIMED);        // free → claim (lock ON)
        if (currentOwner.equals(clicker)) return new Result(null, Outcome.RELEASED);  // mine → release (unlock)
        return new Result(currentOwner, Outcome.LOCKED_BY_OTHER);                     // someone else's lock → rejected
    }
}
