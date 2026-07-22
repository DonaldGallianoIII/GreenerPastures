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
    SHINY     ("shiny",      "Shiny",          TetherClass.QUALITY,    15),
    SPEED     ("speed",      "Speed",          TetherClass.THROUGHPUT,  1),
    IV_FLOOR  ("iv_floor",   "IV Floor",       TetherClass.QUALITY,     0),   // retired tether target (Deuce, 2026-07-21)
    EV        ("ev",         "Fine-Tune (EV)", TetherClass.QUALITY,     0),   // retired: no consumers since the EV-spread rework
    ENRICHMENT("enrichment", "Enrichment",     TetherClass.THROUGHPUT, 10),
    DROP_RATE ("drop_rate",  "Drop Rate",      TetherClass.THROUGHPUT, 100),  // centipercent: +1.00% per tether level
    DROP_YIELD("drop_yield", "Drop Yield",     TetherClass.THROUGHPUT,  1),
    NATURE    ("nature",     "Nature",         TetherClass.QUALITY, true),
    BALL      ("ball",       "Poké Ball",      TetherClass.QUALITY, true),
    ABILITY   ("ability",    "Hidden Ability", TetherClass.QUALITY, true),
    EGG_MOVE  ("egg_move",   "Egg Moves",      TetherClass.QUALITY, true),
    HATCH     ("hatch",      "Hatch Haste",    TetherClass.THROUGHPUT,  1);   // egg TIMER scaling: ladder extends past III via tethers

    public final String id;
    public final String label;
    public final TetherClass cls;
    /** A choice-encoding augment (level = catalog index), never tether-amplified. False for magnitude augments. */
    public final boolean selector;
    /** The magnitude ONE tether level adds on top of the Kernel's stored mod (Deuce, 2026-07-21: tethers
     *  are flat, ADDITIVE, stack, and deliberately push PAST the augment's rollable max - that's what the
     *  rent buys). Each step = half the augment's level-I value, continuing the exact I→II→III install
     *  ladder ({@code AugmentType}: II = +½ base, III = +½ more). Units are the function's own
     *  (% · level · centipercent · budget). {@code 0} = not a tether target. */
    public final int tetherStep;

    AugmentFunction(String id, String label, TetherClass cls, int tetherStep) {
        this.id = id;
        this.label = label;
        this.cls = cls;
        this.selector = false;
        this.tetherStep = tetherStep;
    }

    AugmentFunction(String id, String label, TetherClass cls, boolean selector) {
        this.id = id;
        this.label = label;
        this.cls = cls;
        this.selector = selector;
        this.tetherStep = 0;
    }

    /** Can a Soul Tether target this function at all? Selectors encode choices (nothing to scale), and
     *  EV / IV Floor are retired targets (EV's flat floor lost its consumers to the spread rework). */
    public boolean tetherable() {
        return tetherStep > 0;
    }

    /** Player-facing boost label for {@code levels} tether levels ("+3.00% drops" · "+45% shiny" ·
     *  "+2 levels") - ONE formatter so the Loom, tooltips, and logs never disagree. */
    public String boostLabel(int levels) {
        int mag = tetherStep * Math.max(0, levels);
        return switch (this) {
            case DROP_RATE  -> "+" + String.format("%.2f", mag / 100.0) + "% drops";
            case SHINY      -> "+" + mag + "% shiny";
            case ENRICHMENT -> "+" + mag + "% render value";
            default         -> "+" + levels + " level" + (levels == 1 ? "" : "s");
        };
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
