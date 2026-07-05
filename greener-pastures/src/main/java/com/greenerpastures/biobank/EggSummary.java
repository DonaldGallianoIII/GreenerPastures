package com.greenerpastures.biobank;

/**
 * The filterable summary of a single egg - <b>Minecraft-free</b>, so the egg-economy logic (value
 * rule, render ledger, filters) is unit-tested headless. The MC adapter builds one of these from a
 * Cobbreeding egg {@code ItemStack} via {@code EggReader} (species + shiny + IVs).
 *
 * @param species    normalized species key (e.g. "charmander")
 * @param shiny      is the hatchling shiny
 * @param ivTotal    sum of the 6 IVs (0..186)
 * @param perfectIvs count of 31-IV stats (0..6)
 */
public record EggSummary(String species, boolean shiny, int ivTotal, int perfectIvs) {}
