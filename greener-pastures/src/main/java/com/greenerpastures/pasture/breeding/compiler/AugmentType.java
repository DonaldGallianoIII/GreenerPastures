package com.greenerpastures.pasture.breeding.compiler;

import com.greenerpastures.economy.AugmentFunction;
import com.greenerpastures.pasture.breeding.Augments;
import com.greenerpastures.pasture.breeding.BreedingUpgradeItem;
import com.greenerpastures.pasture.breeding.EvSpread;
import com.greenerpastures.pasture.breeding.HatchHaste;
import com.greenerpastures.pasture.breeding.GpComponents;
import net.minecraft.item.ItemStack;

/**
 * The augment "packages" the Notebook's Augmenter can install onto a Kernel (a Pasture Upgrade item).
 * Each type names an {@link AugmentFunction} and the level/magnitude it writes; install + apply are
 * <b>generic</b> over that function (merge into the {@code greenerpastures:augments} component), so adding
 * an augment is just one new constant (+ a display line in {@link #effectSummary}). Ships all SEVEN v1
 * functions - each now has a live effect (shiny / speed / IV floor / EV at the breeder, drop rate / drop
 * yield at the Harvester, enrichment at the Renderer).
 *
 * <p>Magnitudes are calibration (tune via config). A Drop Rate augment deliberately writes ABOVE the
 * Kernel's base {@code drop_rate} ({@link BreedingUpgradeItem#BASE_DROP_RATE} = 25 centipercent) so it's a
 * real upgrade rather than a silent no-op; {@link #installedOn} also keeps a higher value from being
 * downgraded.
 */
public enum AugmentType {
    SHINY     ("shiny-boost",      "1.0", AugmentFunction.SHINY,      30),   // % chance of one bounded reroll
    SPEED     ("speed-boost",      "1.0", AugmentFunction.SPEED,       1),   // level I → faster breeding cadence
    ENRICHMENT("enrichment-boost", "1.0", AugmentFunction.ENRICHMENT, 20),   // +20% → 1.20× render value
    DROP_RATE ("droprate-boost",   "2.0", AugmentFunction.DROP_RATE, 200),   // centipercent: 2.00% (base is 0.50%/tier) - doubled 2026-07-03
    DROP_YIELD("dropyield-boost",  "1.0", AugmentFunction.DROP_YIELD,  1),   // +1 to the amount-budget ceiling
    IV_FLOOR  ("ivfloor-boost",    "1.0", AugmentFunction.IV_FLOOR,    3),   // guarantee 3 perfect (31) IVs
    EV        ("ev-primer",        "2.0", AugmentFunction.EV,          0),   // PARAMETERIZED: the allocator's targeted EV_SPREAD (BUG-002; v1's flat +20 blanket retired)
    NATURE    ("nature-lock",      "1.0", AugmentFunction.NATURE,      0),   // PARAMETERIZED: picked 1-based NatureCatalog index
    BALL      ("ball-lock",        "1.0", AugmentFunction.BALL,        0),   // PARAMETERIZED: picked 1-based BallCatalog index
    ABILITY   ("ability-splice",   "1.0", AugmentFunction.ABILITY,     1),   // toggle: force the hidden ability on every egg
    EGG_MOVES ("eggmove-tutor",    "1.0", AugmentFunction.EGG_MOVE,    1),   // toggle: teach species egg moves at hatch
    HATCH     ("hatch-haste",      "1.0", AugmentFunction.HATCH,       1);   // level I → bred eggs hatch in half the time (II ×0.25 · III ×0.1 via QA/tethers)

    public final String pkgName;
    public final String version;
    public final AugmentFunction function;
    /** Level / magnitude written for {@link #function} - units are the function's own (% · level · centipercent · budget). */
    public final int value;

    AugmentType(String pkgName, String version, AugmentFunction function, int value) {
        this.pkgName = pkgName;
        this.version = version;
        this.function = function;
        this.value = value;
    }

    /** Item-id suffix (stable) - the registered item is {@code augment_<function id>}, e.g. {@code augment_shiny},
     *  {@code augment_drop_rate}. Keeping {@code augment_shiny} byte-identical preserves the existing item. */
    public String id() {
        return function.id;
    }

    /** pip-style package id, e.g. {@code shiny-boost==1.0}. */
    public String pkg() {
        return pkgName + "==" + version;
    }

    /** Only Kernels (Pasture Upgrade items) can receive augments. */
    public boolean appliesTo(ItemStack kernel) {
        return !kernel.isEmpty() && kernel.getItem() instanceof BreedingUpgradeItem;
    }

    /** A choice-carrying augment (#34/#35) whose install needs a picked value - the console sends
     *  {@code "NATURE:7"} / {@code "EV:0,252,…"} instead of the bare type; a bare install is rejected. */
    public boolean parameterized() {
        return this == EV || this == NATURE || this == BALL;
    }

    /** True if the Kernel's level for this function is already at/above {@code value} - so re-compiling is a
     *  no-op AND a higher mod (incl. a future stronger augment, or a hand-set base) is never downgraded.
     *  Selectors count as installed at ANY picked value; the EV primer counts by its EV_SPREAD component
     *  (v1's blanket level ≥20 no longer marks it - the breeder only reads the spread). */
    public boolean installedOn(ItemStack kernel) {
        return installedLevelOn(kernel) > 0;
    }

    /** Installed level on this Kernel: 0 = none · 1 · 2 (the 1.5× tier, Deuce 2026-07-05). DROP_RATE is
     *  measured ABOVE the Kernel's born-with base so a bare Greener (base 5.00%) neither reads as installed
     *  nor eats phantom slots (latent pre-leveling bug, fixed here). */
    public int installedLevelOn(ItemStack kernel) {
        if (this == EV) {
            EvSpread s = kernel.get(GpComponents.EV_SPREAD);
            return s != null && !s.isEmpty() ? 1 : 0;
        }
        Augments a = kernel.get(GpComponents.AUGMENTS);
        if (a == null) return 0;
        int stored = a.level(function);
        if (function.selector) return stored > 0 ? 1 : 0;
        return levelOf(stored - baseOf(kernel), value, valueAt(2), maxLevel());
    }

    /** The Kernel's born-with magnitude for this function (only DROP_RATE has one). */
    private int baseOf(ItemStack kernel) {
        if (this != DROP_RATE) return 0;
        return kernel.getItem() instanceof BreedingUpgradeItem bu ? bu.tier().baseDropRateCentipercent() : 0;
    }

    /** PURE level math (headless-tested): effective magnitude → 0/1/2 against the two tier values. */
    public static int levelOf(int effectiveMagnitude, int v1, int v2, int maxLevel) {
        if (maxLevel > 1 && effectiveMagnitude >= v2) return 2;
        return effectiveMagnitude >= v1 ? 1 : 0;
    }

    /** Level-II magnitude: 1.5× level I (Deuce 2026-07-05). Speed/Hatch use their native level curves;
     *  IV Floor rounds DOWN (4, not 5) so perfection stays a chase. */
    public int valueAt(int level) {
        if (level <= 1) return value;
        return switch (this) {
            case SPEED, HATCH -> 2;                     // native curves: ×2 cadence · ×0.25 hatch time
            case IV_FLOOR     -> 4;                     // 4.5 floored
            default           -> (int) Math.round(value * 1.5);   // shiny 45 · drop_rate +3.00% · yield 2 · enrichment 30
        };
    }

    /** Two levels for magnitude augments; selectors/binaries/EV stay single-level (nothing to 1.5×). */
    public int maxLevel() {
        return parameterized() || this == ABILITY || this == EGG_MOVES ? 1 : 2;
    }

    /** PURE slot math (headless-tested): level II occupies THREE slots total - not even possible on Copper. */
    public static int slotsForLevel(int level) {
        return level >= 2 ? 3 : level == 1 ? 1 : 0;
    }

    /** What THIS kernel's install of this augment writes as the stored level (DROP_RATE rides its base). */
    public int storedValueFor(ItemStack kernel, int level) {
        return baseOf(kernel) + valueAt(level);
    }

    /** A copy of the Kernel with this augment's function set to {@code value} (replace-in-place, one per function). */
    public ItemStack apply(ItemStack kernel) {
        return apply(kernel, value);
    }

    /** Parameterized install: set this augment's function to the PICKED {@code level} (a selector's catalog
     *  index). The EV primer never routes here - its value is the EV_SPREAD component, not a level. */
    public ItemStack apply(ItemStack kernel, int level) {
        ItemStack out = kernel.copy();
        Augments base = out.get(GpComponents.AUGMENTS);
        if (base == null) base = Augments.NONE;
        out.set(GpComponents.AUGMENTS, base.withLevel(function, level));
        return out;
    }

    /** Human one-line effect for the item tooltip (the only per-type display text). */
    public String effectSummary() {
        return switch (this) {
            case SHINY      -> "✦ +" + value + "% shiny proc · bounded reroll";
            case SPEED      -> "⚡ Speed " + value + " · faster breeding cadence";
            case ENRICHMENT -> "❖ +" + value + "% render value (Enrichment)";
            case DROP_RATE  -> "⛏ +" + String.format("%.2f", value / 100.0) + "% drop rate";
            case DROP_YIELD -> "⛏ +" + value + " drop yield";
            case IV_FLOOR   -> "✦ " + value + " perfect IV" + (value == 1 ? "" : "s") + " guaranteed";
            case EV         -> "✦ EV Primer · allocate a targeted EV spread";
            case NATURE     -> "🧬 Nature Lock · every egg hatches the picked nature";
            case BALL       -> "◉ Ball Lock · every egg hatches in the picked ball";
            case ABILITY    -> "✦ Ability Splice · force the hidden ability";
            case EGG_MOVES  -> "📖 Egg-Move Tutor · teach species egg moves";
            case HATCH      -> "🐣 Hatch Haste " + value + " · bred eggs hatch ×" + HatchHaste.factorLabel(value);
        };
    }
}
