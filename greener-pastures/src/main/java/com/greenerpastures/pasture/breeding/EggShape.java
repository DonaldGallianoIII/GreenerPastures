package com.greenerpastures.pasture.breeding;

/**
 * The full set of breeding-augment shaping inputs for one bred egg - what the pasture's effective augments
 * resolved to, handed as a single argument to {@link CobbreedingBridge#buildEggForPair}. A small value carrier so
 * the bridge takes ONE parameter instead of an ever-growing positional list (it gains a field per breeding-meta
 * augment). Pure data (no MC types): the breeder resolves the selector indices to their id strings (via
 * {@link NatureCatalog} / {@link BallCatalog}) before filling this in, so the bridge stays decoupled from the
 * catalogs and the whole thing stays unit-testable.
 *
 * @param shinyProcChance   0..1 bonus shiny reroll chance (Shiny augment)
 * @param ivFloor           guaranteed perfect (31) IV count (IV Floor augment)
 * @param evSpread          per-stat EV allocation to pre-set (EV augment - BUG-002; replaces the flat per-stat floor)
 * @param nature            nature id to lock, or {@code null} for vanilla inheritance (Nature selector)
 * @param ball              ball id to lock, or {@code null} for vanilla inheritance (Ball selector)
 * @param forceHiddenAbility force the species' hidden ability (Ability augment)
 * @param teachEggMoves     teach the species' egg moves to the hatchling (Egg Moves augment)
 */
public record EggShape(double shinyProcChance, int ivFloor, EvSpread evSpread,
                       String nature, String ball, boolean forceHiddenAbility, boolean teachEggMoves,
                       int hatchLevel) {

    /** Compat shape (pre-Hatch-Haste). */
    public EggShape(double shinyProcChance, int ivFloor, EvSpread evSpread,
                    String nature, String ball, boolean forceHiddenAbility, boolean teachEggMoves) {
        this(shinyProcChance, ivFloor, evSpread, nature, ball, forceHiddenAbility, teachEggMoves, 0);
    }

    /** No shaping - plain vanilla egg-gen (used when a pasture has no breeding augments). */
    public static final EggShape NONE = new EggShape(0.0, 0, EvSpread.NONE, null, null, false, false, 0);
}
