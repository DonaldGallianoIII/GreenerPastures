package com.greenerpastures.buff;

/**
 * How the MC adapter <i>applies</i> a {@link BuffId}. The pure cores ({@link BuffResolver}/{@link BuffConfig})
 * don't care about this - they only resolve tier + cost - but the tick adapter dispatches on it.
 */
public enum BuffCategory {
    /** Rides a vanilla enchantment {@code tier} levels past its normal max on the player's held/worn gear. */
    ENCHANT,
    /** A vanilla status effect applied (refreshed) each tick while the Daemon is held - e.g. Haste, Saturation. */
    EFFECT,
    /** A mechanic the mod implements itself, with no vanilla registry entry - auto-smelt, vein-mine, magnet, … */
    HOOK
}
