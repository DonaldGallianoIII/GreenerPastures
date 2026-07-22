package com.greenerpastures.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Soul-Tether FED / STARVED decision, Minecraft-free + unit-tested. Given a Kernel's base augment
 * levels, its slotted tethers, and the operator's current Data balance, decide whether the tethers
 * amplify (fed) or fall back to the free base (starved).
 *
 * <p><b>Billing lives elsewhere</b> (Deuce, 2026-07-21): rent is a flat per-second charge on the
 * {@code TetherUpkeep} ticker - only while the tether sits on a LINKED pasture with mons inside a loaded
 * chunk. This class never debits; it only answers "can the owner cover a second of rent right now?" -
 * a broke owner's tethers read starved everywhere the moment the rent stops clearing. Starvation drops
 * you to base - it never pauses breeding and never destroys anything; hunger is a luxury, never life
 * support.
 */
public final class TetherRuntime {
    private TetherRuntime() {}

    /** What to run this cycle: the (possibly amplified) augments, the rent gate that was checked
     *  (whole Data for one second of the selected tethers, informational), and whether fed. */
    public record Resolution(EffectiveAugments effective, long upkeep, boolean amplified) {}

    /** Whole-Data rent for {@code seconds} of the given tethers (ceil - the house never rounds in the
     *  renter's favor). The catch-up consumers use this to PRE-PAY an away window before applying the
     *  boost (Deuce, 2026-07-21: check the Data first, then buff): can't pay = no charge, base mods. */
    public static long rentFor(long seconds, List<SoulTether> tethers) {
        if (seconds <= 0) return 0L;
        long centi = upkeepCentiPerSecond(tethers);
        if (centi <= 0) return 0L;
        return (centi * seconds + 99) / 100;
    }

    /** Sum of the selected tethers' rent in centi-Data per second (blank tethers rent 0). */
    public static long upkeepCentiPerSecond(List<SoulTether> tethers) {
        long sum = 0L;
        if (tethers != null) {
            for (SoulTether t : tethers) {
                if (t != null) sum += t.upkeepCentiPerSecond();
            }
        }
        return sum;
    }

    public static Resolution resolve(Map<AugmentFunction, Integer> base, List<SoulTether> tethers, long balance) {
        long centi = upkeepCentiPerSecond(tethers);
        long gate = (centi + 99) / 100;                 // one second of rent, rounded up to whole Data
        if (centi > 0 && balance >= gate) {
            // FED: the account can cover the rent → amplify the base mods (TetherUpkeep does the billing).
            return new Resolution(EffectiveAugments.of(base, tethers), gate, true);
        }
        // STARVED or no tethers: run the free base.
        return new Resolution(EffectiveAugments.of(base, List.of()), 0L, false);
    }

    /** The tethers whose function is in {@code functions} - the subset a single consumer owns. Blank or
     *  unknown-function tethers are dropped. */
    public static List<SoulTether> select(List<SoulTether> tethers, Set<AugmentFunction> functions) {
        List<SoulTether> out = new ArrayList<>();
        if (tethers != null && functions != null) {
            for (SoulTether t : tethers) {
                if (t == null || t.isBlank()) continue;
                AugmentFunction f = AugmentFunction.byId(t.function());
                if (f != null && functions.contains(f)) out.add(t);
            }
        }
        return out;
    }

    /**
     * {@link #resolve} restricted to the tethers a consumer owns ({@code functions}) - the per-consumer,
     * per-clock entry point. Because the Kernel's tethers are shared but each consumer runs on its own
     * clock (breeder per breeding cycle · Harvester per IRL minute · Renderer per cull), every consumer
     * resolves + drains ONLY its own functions so a tether is billed exactly once.
     *
     * <p><b>Contract:</b> the consumer function sets MUST be disjoint - {@code SHINY/SPEED} = breeder ·
     * {@code DROP_RATE/DROP_YIELD} = Harvester · {@code ENRICHMENT} = Renderer. Overlapping sets would
     * charge one tether on two clocks.
     */
    public static Resolution resolveFor(Map<AugmentFunction, Integer> base, List<SoulTether> tethers,
                                        long balance, Set<AugmentFunction> functions) {
        return resolve(base, select(tethers, functions), balance);
    }
}
