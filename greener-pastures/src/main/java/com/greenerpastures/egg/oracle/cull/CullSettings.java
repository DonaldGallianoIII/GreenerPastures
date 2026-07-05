package com.greenerpastures.egg.oracle.cull;

/** Filter thresholds + display toggles for the in-container egg-culler overlay. */
public class CullSettings {
    public boolean enabled = true;       // master toggle for the overlay (in-GUI: C)
    public boolean showNumbers = true;   // draw each egg's IV total in its slot corner
    public boolean showSummary = true;   // draw the bank summary line above the GUI

    // An egg is a KEEPER (vs CULL) if it's shiny, OR clears either IV bar below.
    public int keepMinIvTotal = 120;     // total IVs, out of 186
    public int keepMinPerfect = 3;       // count of 31-IV ("perfect") stats

    public Tier classify(EggInfo e) {
        if (e == null) return Tier.NOT_EGG;
        if (e.shiny()) return Tier.SHINY;   // shiny always wins - gold regardless of IVs
        if (!e.ivsKnown()) return Tier.UNKNOWN;
        if (e.ivTotal() >= keepMinIvTotal || e.perfectCount() >= keepMinPerfect) return Tier.KEEPER;
        return Tier.CULL;
    }
}
