package com.greenerpastures.egg.oracle.calc;

/** The user-editable inputs that drive the odds calculation. */
public class Profile {
    public String presetName = "Cobbreeding";
    public double baseRate = 8192;     // shiny denominator (1/baseRate per roll)
    public double masudaMult = 4;      // divides the rate when parents differ in OT
    public boolean diffOT = true;      // are the breeding pairs Masuda-eligible?
    public int pairs = 10;             // number of active breeding pairs
    public double eggTimeMin = 9.2;    // avg minutes per egg, per pair
    public double horizonHours = 24;   // "chance by N hours" window

    public Profile copy() {
        Profile p = new Profile();
        p.presetName = presetName;
        p.baseRate = baseRate;
        p.masudaMult = masudaMult;
        p.diffOT = diffOT;
        p.pairs = pairs;
        p.eggTimeMin = eggTimeMin;
        p.horizonHours = horizonHours;
        return p;
    }
}
