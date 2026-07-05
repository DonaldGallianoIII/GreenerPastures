package com.greenerpastures.ritual;

import java.util.Collection;

/**
 * The banking anchor for SPANNING rituals: when a pasture pair satisfies a ritual, BOTH pastures' sweeps see
 * the satisfied union - without an anchor each would bank a pull and the ritual would run at double rate.
 * Rule: a pasture banks only if some satisfying partner has a LARGER pos key - i.e. exactly the smallest
 * pasture of any satisfying pair banks, once per its own sweep. Pure + tested.
 */
public final class SpanGate {
    private SpanGate() {}

    public static boolean shouldBank(long selfPosKey, Collection<Long> satisfyingPartnerPosKeys) {
        if (satisfyingPartnerPosKeys == null) return false;
        for (Long p : satisfyingPartnerPosKeys) {
            if (p != null && selfPosKey < p) return true;
        }
        return false;
    }
}
