package com.greenerpastures.biobank;

/**
 * "Is this egg worth keeping?" — the independent safety check the BioBank's render ledger uses to flag
 * eggs that are about to be destroyed (so a shiny is never one misclick from the furnace). Deliberately
 * <b>separate</b> from whatever render filter selected the batch (defense-in-depth against a mis-set
 * filter). Minecraft-free + configurable; the default is "shiny OR any 31-IV stat".
 *
 * @param shinyIsValuable flag shinies as valuable
 * @param minPerfectIvs   flag eggs with at least this many 31-IV stats (≤0 disables this rule)
 * @param minIvTotal      flag eggs with at least this IV total (&gt;186 disables this rule)
 */
public record ValueRule(boolean shinyIsValuable, int minPerfectIvs, int minIvTotal) {
    /** Default: shiny OR at least one perfect (31) IV. (IV-total rule off — 187 is unreachable.) */
    public static final ValueRule DEFAULT = new ValueRule(true, 1, 187);

    public boolean isValuable(EggSummary e) {
        if (shinyIsValuable && e.shiny()) return true;
        if (minPerfectIvs >= 1 && e.perfectIvs() >= minPerfectIvs) return true;
        if (minIvTotal <= 186 && e.ivTotal() >= minIvTotal) return true;
        return false;
    }
}
