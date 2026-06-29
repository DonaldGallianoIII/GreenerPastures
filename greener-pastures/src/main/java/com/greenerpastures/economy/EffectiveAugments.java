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
        // 2) effective = base × amplification, only where a base mod actually exists
        Map<AugmentFunction, Double> eff = new EnumMap<>(AugmentFunction.class);
        if (base != null) {
            base.forEach((f, lvl) -> {
                if (f != null && lvl != null && lvl > 0) {
                    eff.put(f, lvl * amp.getOrDefault(f, 1.0));
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
}
