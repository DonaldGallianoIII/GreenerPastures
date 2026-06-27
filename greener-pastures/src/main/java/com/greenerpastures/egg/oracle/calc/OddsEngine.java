package com.greenerpastures.egg.oracle.calc;

/**
 * Pure shiny-odds math. Mirrors Cobbreeding's calcShiny(): shinyRate is a denominator,
 * and Masuda divides it when parents have different Original Trainers.
 *
 *   effectiveRate = baseRate / (diffOT ? masudaMult : 1)
 *   pPerEgg       = 1 / effectiveRate
 */
public final class OddsEngine {
    private OddsEngine() {}

    public static double effectiveRate(Profile p) {
        double mult = (p.diffOT && p.masudaMult > 0) ? p.masudaMult : 1.0;
        double rate = p.baseRate / mult;
        return Math.max(1.0, rate); // a rate < 1 would mean guaranteed shiny
    }

    public static double pPerEgg(Profile p) {
        return 1.0 / effectiveRate(p);
    }

    /** Eggs produced per hour across ALL active pairs. */
    public static double eggsPerHour(Profile p) {
        if (p.eggTimeMin <= 0) return 0;
        return p.pairs * (60.0 / p.eggTimeMin);
    }

    public static double shiniesPerHour(Profile p) {
        return eggsPerHour(p) * pPerEgg(p);
    }

    public static double shiniesPerDay(Profile p) {
        return shiniesPerHour(p) * 24.0;
    }

    /** Average hours to produce one shiny (Infinity if no eggs are being laid). */
    public static double hoursPerShiny(Profile p) {
        double sph = shiniesPerHour(p);
        return sph > 0 ? 1.0 / sph : Double.POSITIVE_INFINITY;
    }

    /** Probability of at least one shiny within the given number of hours. */
    public static double chanceWithin(Profile p, double hours) {
        double eggs = eggsPerHour(p) * hours;
        double pMiss = Math.pow(1.0 - pPerEgg(p), eggs);
        return 1.0 - pMiss;
    }
}
