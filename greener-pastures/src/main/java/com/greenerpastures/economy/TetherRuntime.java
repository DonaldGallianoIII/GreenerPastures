package com.greenerpastures.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The per-breeding-cycle Soul-Tether decision, Minecraft-free + unit-tested. Given a Kernel's base
 * augment levels, its slotted tethers, and the operator's current Data balance, decide whether the
 * tethers are <b>FED</b> (amplify the base mods + drain Data) or <b>STARVED</b> (inert → the free base
 * only, no drain).
 *
 * <p><b>All-or-nothing</b> (DAEMON_AND_TETHERS.md): if the balance can't cover the full burn this cycle,
 * the tethers fall back to the free base. Starvation drops you to base - it never pauses breeding and
 * never destroys anything; hunger is a luxury, never life support. The breeder calls {@link #resolve}
 * each cycle, applies {@link Resolution#effective()} to the egg, and debits {@link Resolution#drain()}.
 */
public final class TetherRuntime {
    private TetherRuntime() {}

    /** What to run this cycle: the (possibly amplified) augments, the Data to debit, and whether fed. */
    public record Resolution(EffectiveAugments effective, long drain, boolean amplified) {}

    /** Sum of every powered tether's burn this cycle (blank/inert tethers burn 0). */
    public static long totalBurn(List<SoulTether> tethers) {
        long sum = 0L;
        if (tethers != null) {
            for (SoulTether t : tethers) {
                if (t != null) sum += t.burnPerCycle();
            }
        }
        return sum;
    }

    public static Resolution resolve(Map<AugmentFunction, Integer> base, List<SoulTether> tethers, long balance) {
        long burn = totalBurn(tethers);
        if (burn > 0 && balance >= burn) {
            // FED: the account covers the full burn → amplify the base mods and drain that much.
            return new Resolution(EffectiveAugments.of(base, tethers), burn, true);
        }
        // STARVED or no tethers: run the free base, drain nothing.
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
