package com.greenerpastures.ritual;

import java.util.Collection;

/**
 * The banking anchor for SPANNING rituals: when a pasture pair satisfies a ritual, BOTH pastures' sweeps see
 * the satisfied union - without an anchor each would bank a pull and the ritual would run at double rate.
 * Rule (rev 2 - review found the pair-only version banked (N-1)x in a clique of N mutually-satisfying
 * pastures): a pasture banks only when it is SMALLER than EVERY satisfying partner - the global minimum
 * of its satisfying set. In a clique of any size exactly one pasture banks; two DISJOINT complete
 * compositions still bank independently (two full setups = two pulls, intended). Pure + tested.
 */
public final class SpanGate {
    private SpanGate() {}

    public static boolean shouldBank(long selfPosKey, Collection<Long> satisfyingPartnerPosKeys) {
        if (satisfyingPartnerPosKeys == null || satisfyingPartnerPosKeys.isEmpty()) return false;
        for (Long p : satisfyingPartnerPosKeys) {
            if (p == null || selfPosKey >= p) return false;
        }
        return true;
    }
}
