package com.greenerpastures.buff;

/**
 * Per-buff admin settings (one JSON entry, keyed by {@link BuffId#id}):
 * <ul>
 *   <li>{@code enabled} - is this buff offered at all;</li>
 *   <li>{@code maxTier} - the cap on its effective tier. The effective tier in play is
 *       {@code min(daemonLevel, maxTier)}, so a shop-economy server caps Fortune by lowering this. {@code 0}
 *       disables the buff as surely as {@code enabled=false};</li>
 *   <li>{@code costPerSec} - Data drained per second <i>per tier</i> of this buff while active (the
 *       tier-scaled, summed drain model).</li>
 * </ul>
 * The compact constructor clamps to sane bounds so a config typo can't grant absurd levels or negative cost;
 * {@link BuffResolver} re-clamps at use-site too, so even a hand-mangled file stays safe.
 */
public record BuffSetting(boolean enabled, int maxTier, double costPerSec) {

    /** The +1/+2/+3 design ceiling - a held Daemon tops out at Mk III, and no cap exceeds it. */
    public static final int TIER_CEILING = 3;

    public BuffSetting {
        if (maxTier < 0) maxTier = 0;
        if (maxTier > TIER_CEILING) maxTier = TIER_CEILING;
        if (costPerSec < 0) costPerSec = 0;
    }
}
