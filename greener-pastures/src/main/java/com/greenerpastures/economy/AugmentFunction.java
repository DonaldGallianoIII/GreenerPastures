package com.greenerpastures.economy;

/**
 * The shared vocabulary of breeding-augment <b>functions</b> - the catalog that BOTH a Kernel's base
 * mods ({@code Augments}) and a {@link SoulTether} name. A tether amplifies the base mod of its matching
 * function (a Shiny Tether boosts the Shiny augment, not Speed). Each function carries its economic
 * {@link TetherClass} (quality = expensive expense · throughput = investment that pays off), which is how
 * the tether burn-split charges more for Shiny than for Speed.
 *
 * <p>{@code id} is the stable string key used in NBT / data components / {@code SoulTether.function}.
 * v1 = the seven shipping magnitude augments; Nature (a <b>selector</b>) is the first of the breeding-meta
 * batch - Egg-Move/Ability/Ball join as more constants.
 *
 * <p><b>Magnitude vs selector.</b> Most augments are <i>magnitudes</i> ("how much" - more shiny, higher IV floor)
 * and a matching fed Tether <i>amplifies</i> them. A {@link #selector} augment instead encodes a <i>choice</i>
 * ("which one" - which nature) as its level used as a catalog index; amplifying a choice is meaningless, so
 * {@code EffectiveAugments} never runs a selector through tether amplification.
 */
public enum AugmentFunction {
    SHINY     ("shiny",      "Shiny",          TetherClass.QUALITY),
    SPEED     ("speed",      "Speed",          TetherClass.THROUGHPUT, false, true),
    IV_FLOOR  ("iv_floor",   "IV Floor",       TetherClass.QUALITY),
    EV        ("ev",         "Fine-Tune (EV)", TetherClass.QUALITY),
    ENRICHMENT("enrichment", "Enrichment",     TetherClass.THROUGHPUT),
    DROP_RATE ("drop_rate",  "Drop Rate",      TetherClass.THROUGHPUT),
    DROP_YIELD("drop_yield", "Drop Yield",     TetherClass.THROUGHPUT, false, true),
    NATURE    ("nature",     "Nature",         TetherClass.QUALITY, true, false),
    BALL      ("ball",       "Poké Ball",      TetherClass.QUALITY, true, false),
    ABILITY   ("ability",    "Hidden Ability", TetherClass.QUALITY, true, false),
    EGG_MOVE  ("egg_move",   "Egg Moves",      TetherClass.QUALITY, true, false),
    HATCH     ("hatch",      "Hatch Haste",    TetherClass.THROUGHPUT, false, true);   // egg TIMER scaling: I ×0.5 · II ×0.25 · III ×0.1

    public final String id;
    public final String label;
    public final TetherClass cls;
    /** A choice-encoding augment (level = catalog index), never tether-amplified. False for magnitude augments. */
    public final boolean selector;
    /** A small-integer LEVEL augment (Speed 0-3, Drop Yield, Hatch 0-3). A tether adds flat levels
     *  (+tier) instead of multiplying - multiply-then-round on a 1..3 level ate whole tiers
     *  (Deuce QA 2026-07-21: every break point must produce something that can be felt). */
    public final boolean discrete;

    AugmentFunction(String id, String label, TetherClass cls) {
        this(id, label, cls, false, false);
    }

    AugmentFunction(String id, String label, TetherClass cls, boolean selector, boolean discrete) {
        this.id = id;
        this.label = label;
        this.cls = cls;
        this.selector = selector;
        this.discrete = discrete;
    }

    /** Can a Soul Tether target this function at all? Selectors encode choices (nothing to scale), and
     *  EV / IV Floor are retired tether targets (Deuce, 2026-07-21): EV's flat floor has no consumers
     *  since the EV-spread rework, and IV Floor's rounding jank wasn't worth keeping. */
    public boolean tetherable() {
        return !selector && this != EV && this != IV_FLOOR;
    }

    /** Look up a function by its stable id (e.g. a tether's {@code function} string); null if unknown. */
    public static AugmentFunction byId(String id) {
        if (id == null) return null;
        for (AugmentFunction f : values()) {
            if (f.id.equals(id)) return f;
        }
        return null;
    }
}
