package com.greenerpastures.economy;

/**
 * The shared vocabulary of breeding-augment <b>functions</b> — the catalog that BOTH a Kernel's base
 * mods ({@code Augments}) and a {@link SoulTether} name. A tether amplifies the base mod of its matching
 * function (a Shiny Tether boosts the Shiny augment, not Speed). Each function carries its economic
 * {@link TetherClass} (quality = expensive expense · throughput = investment that pays off), which is how
 * the tether burn-split charges more for Shiny than for Speed.
 *
 * <p>{@code id} is the stable string key used in NBT / data components / {@code SoulTether.function}.
 * v1 = the seven shipping augments; Nature/Egg-Move/Ability/… join later as new constants.
 */
public enum AugmentFunction {
    SHINY     ("shiny",      "Shiny",          TetherClass.QUALITY),
    SPEED     ("speed",      "Speed",          TetherClass.THROUGHPUT),
    IV_FLOOR  ("iv_floor",   "IV Floor",       TetherClass.QUALITY),
    EV        ("ev",         "Fine-Tune (EV)", TetherClass.QUALITY),
    ENRICHMENT("enrichment", "Enrichment",     TetherClass.THROUGHPUT),
    DROP_RATE ("drop_rate",  "Drop Rate",      TetherClass.THROUGHPUT),
    DROP_YIELD("drop_yield", "Drop Yield",     TetherClass.THROUGHPUT);

    public final String id;
    public final String label;
    public final TetherClass cls;

    AugmentFunction(String id, String label, TetherClass cls) {
        this.id = id;
        this.label = label;
        this.cls = cls;
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
