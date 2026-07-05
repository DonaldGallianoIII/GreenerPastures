package com.greenerpastures.buff;

/**
 * The Daemon {@link BuffCategory#ENCHANT} buffs that are delivered as a direct player attribute modifier - the
 * cleanest enchant-boost path: <b>no mixin, no ItemStack write</b> (so no dupe/desync/NBT surface), synced over
 * the attribute channel the client already trusts. Pure data + math so the cores/tests stay MC-free;
 * {@link DaemonAttributeBuffs} binds each entry to the real {@code EntityAttribute} and applies it in the
 * {@link DaemonBuffs} settle loop.
 *
 * <p>Each entry maps a {@link BuffId} to an operation and a per-tier amount; the modifier value is
 * {@code perTier × tier}. Verified against the 1.21.1 jar (see {@code ENCHANT_BOOST.md}):
 * <ul>
 *   <li><b>Respiration</b> → {@code generic.oxygen_bonus}, {@code ADD_VALUE} {@code tier}. {@code getNextAirUnderwater}
 *       gives a {@code N/(N+1)} chance to not spend air at bonus {@code N}, so {@code +tier} == vanilla Respiration tier.</li>
 *   <li><b>Swift Sneak</b> → {@code player.sneaking_speed}, {@code ADD_MULTIPLIED_TOTAL} {@code 0.15×tier}, mirroring
 *       the vanilla enchant's {@code add_multiplied_total} 0.15/level.</li>
 *   <li><b>Feather Falling</b> → {@code generic.fall_damage_multiplier}, {@code ADD_MULTIPLIED_TOTAL} {@code −0.15×tier}
 *       (15% softer landing per tier), floored by {@link #FALL_FLOOR} so it can never invert the multiplier.</li>
 * </ul>
 *
 * <p>All three are QOL/movement ({@code gathering == false}) - deliberately so, since an attribute modifier can't be
 * individually capped the way a shop server caps Fortune. Unlike Fortune (which only rides an enchant the gear
 * already has), these are granted <i>flat</i> while held + fed and stack on top of any real same-kind enchant.
 */
public enum AttributeBuff {
    RESPIRATION    (BuffId.RESPIRATION,     Op.ADD_VALUE,            1.0),
    SWIFT_SNEAK    (BuffId.SWIFT_SNEAK,     Op.ADD_MULTIPLIED_TOTAL, 0.15),
    FEATHER_FALLING(BuffId.FEATHER_FALLING, Op.ADD_MULTIPLIED_TOTAL, -0.15),
    /** Tool break-speed multiplier ("Mining Damage" - Deuce): ×(1 + tier/3) → I +33% · II +67% · III +100%.
     *  Sized so III + our Haste III + an Efficiency V netherite pick crosses deepslate's instamine threshold
     *  (35 × 1.6 × 2 = 112 ≥ 90) - each piece alone deliberately does NOT. Affects blocks only, PvP-neutral. */
    MINING_DAMAGE  (BuffId.MINING_DAMAGE,   Op.ADD_MULTIPLIED_TOTAL, 1.0 / 3.0);

    /** The three vanilla attribute operations, mirrored MC-free (maps 1:1 to {@code EntityAttributeModifier.Operation}). */
    public enum Op { ADD_VALUE, ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL }

    /** A reduction modifier is never allowed below this, so a {@code (1 + Σmultiplied)} factor stays ≥ 0.1 (never ≤ 0). */
    public static final double FALL_FLOOR = -0.9;

    public final BuffId buff;
    public final Op operation;
    public final double perTier;

    AttributeBuff(BuffId buff, Op operation, double perTier) {
        this.buff = buff;
        this.operation = operation;
        this.perTier = perTier;
    }

    /** The modifier value at this tier - {@code perTier × max(0,tier)}, floored so a reduction can't invert a multiplier. */
    public double value(int tier) {
        double v = perTier * Math.max(0, tier);
        return v < FALL_FLOOR ? FALL_FLOOR : v;
    }

    /** Stable, unique modifier-id path (one per buff) so the adapter can add/find/remove exactly our modifier. */
    public String modifierPath() {
        return "buff/" + buff.id;
    }

    /** The attribute buff that rides {@code id}, or {@code null} if that buff isn't attribute-delivered. */
    public static AttributeBuff forBuff(BuffId id) {
        for (AttributeBuff a : values()) {
            if (a.buff == id) return a;
        }
        return null;
    }
}
