package com.greenerpastures.economy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The heart of the Soul-Tether mechanic, Minecraft-free + unit-tested: combine a Kernel's <b>base
 * augment levels</b> with its <b>slotted Soul Tethers</b> into the <b>effective</b> magnitude per
 * function. A tether multiplies ONLY its matching function's base ({@code effective = base × Π
 * amplification}); a tether whose function has no base mod amplifies nothing (there's nothing to scale).
 * Blank / inert (starved-Daemon) tethers contribute nothing.
 *
 * <p>This is "base = free, amplification = rented" expressed as pure math: pass an empty tether list (or
 * all-blank) and {@code effective == base}. The MC layer converts the Kernel's {@code Augments} component
 * to the base map and reads back {@link #shinyProcChance()} / {@link #enrichmentMultiplier()} / etc.
 */
public final class EffectiveAugments {
    private final Map<AugmentFunction, Double> effective;

    private EffectiveAugments(Map<AugmentFunction, Double> effective) {
        this.effective = effective;
    }

    public static EffectiveAugments of(Map<AugmentFunction, Integer> base, List<SoulTether> tethers) {
        // 1) per-function amplification = product of every powered tether naming that function
        Map<AugmentFunction, Double> amp = new EnumMap<>(AugmentFunction.class);
        if (tethers != null) {
            for (SoulTether t : tethers) {
                if (t == null || t.isBlank()) continue;
                AugmentFunction f = AugmentFunction.byId(t.function());
                if (f != null) amp.merge(f, t.amplification(), (a, b) -> a * b);
            }
        }
        // 2) effective = base × amplification, only where a base mod actually exists. A SELECTOR augment
        //    (Nature: level = a catalog index) is never amplified — scaling a choice is meaningless and would
        //    corrupt the index (Adamant×1.2 → a different nature), so it passes through at its raw base level.
        Map<AugmentFunction, Double> eff = new EnumMap<>(AugmentFunction.class);
        if (base != null) {
            base.forEach((f, lvl) -> {
                if (f != null && lvl != null && lvl > 0) {
                    eff.put(f, lvl * (f.selector ? 1.0 : amp.getOrDefault(f, 1.0)));
                }
            });
        }
        return new EffectiveAugments(eff);
    }

    /** Amplified magnitude of a function (0 when the Kernel has no base mod for it). */
    public double magnitude(AugmentFunction f) {
        return effective.getOrDefault(f, 0.0);
    }

    public boolean has(AugmentFunction f) {
        return magnitude(f) > 0.0;
    }

    /** Shiny proc as a 0..1 probability (clamped) — feeds the bounded bred-shiny reroll. */
    public double shinyProcChance() {
        return Math.max(0.0, Math.min(100.0, magnitude(AugmentFunction.SHINY))) / 100.0;
    }

    /** Render-Data multiplier (≥1) for an Enrichment-amplified FUEL pasture; 1× when no Enrichment mod. */
    public double enrichmentMultiplier() {
        double pct = magnitude(AugmentFunction.ENRICHMENT);
        return pct <= 0.0 ? 1.0 : 1.0 + pct / 100.0;
    }

    /** Speed augment level (rounded), 0 when none — drives the breeding-cadence reduction. */
    public int speedLevel() {
        return (int) Math.round(magnitude(AugmentFunction.SPEED));
    }

    /** Drop-rate bonus as a 0..1 fraction ADDED to the Harvester's per-mon proc. Stored in centipercent
     *  ({@code 25} = 0.25%), so a tether amplifies it the same as any mod; 0 when the Kernel has none. */
    public double dropRateFraction() {
        return magnitude(AugmentFunction.DROP_RATE) / 10000.0;
    }

    /** IV Floor: the number of IVs (0..6) the breeder guarantees at the perfect value (31) on a bred egg —
     *  base level × any fed IV Floor tether (rounded, capped at the 6 stats); 0 when the Kernel has none. */
    public int ivFloorCount() {
        return Math.max(0, Math.min(6, (int) Math.round(magnitude(AugmentFunction.IV_FLOOR))));
    }

    /** EV Floor: the EVs (0..85 per stat) the breeder pre-sets on EVERY one of the 6 permanent stats of a bred
     *  egg — a flat head-start, clamped so all six fit Cobblemon's 510 EV total. Base × tether; 0 when none. */
    public int evFloorPerStat() {
        return Math.max(0, Math.min(85, (int) Math.round(magnitude(AugmentFunction.EV))));
    }

    /** Nature selector: the 1-based catalog index of the nature to lock the bred egg to (0 = no lock). A SELECTOR
     *  augment, so this is the raw base level — never tether-amplified. The breeder maps it via {@code NatureCatalog}. */
    public int natureIndex() {
        return Math.max(0, (int) Math.round(magnitude(AugmentFunction.NATURE)));
    }

    /** Ball selector: the 1-based catalog index of the ball to lock the bred egg to (0 = no lock). SELECTOR →
     *  raw base level, never tether-amplified; the breeder maps it via {@code BallCatalog}. */
    public int ballIndex() {
        return Math.max(0, (int) Math.round(magnitude(AugmentFunction.BALL)));
    }

    /** Hidden Ability: a binary toggle (any level ⇒ on) that forces the bred egg to the species' hidden ability.
     *  Marked a selector so it's never tether-amplified — a toggle has nothing to scale. */
    public boolean forceHiddenAbility() {
        return magnitude(AugmentFunction.ABILITY) >= 1.0;
    }

    /** Egg Moves: a binary toggle (any level ⇒ on) that teaches the bred egg its species' egg moves. Selector →
     *  never tether-amplified. */
    public boolean teachEggMoves() {
        return magnitude(AugmentFunction.EGG_MOVE) >= 1.0;
    }

    /** Drop-yield bonus = a flat integer ADDED to Cobblemon's {@code amount} budget ceiling per drop event
     *  (LEVER 2): level 1 = a chance at +1 more budget → more items per proc, never fewer (the floor is
     *  untouched). A Drop Yield tether amplifies the base level the same as any mod; 0 when the Kernel has
     *  none. Rounded because a tether multiplies the integer base (e.g. 2 × 1.2 = 2.4 → 2). */
    public int dropYieldBonus() {
        return (int) Math.round(magnitude(AugmentFunction.DROP_YIELD));
    }
}
