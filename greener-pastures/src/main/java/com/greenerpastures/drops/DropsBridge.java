package com.greenerpastures.drops;

import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.greenerpastures.GreenerPastures;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The ONE place Greener Pastures reads Cobblemon's drop tables - isolated + fail-safe (mirrors
 * {@code CobbreedingBridge}). The Harvester's base roll uses Cobblemon's OWN {@code getDrops} (faithful to
 * vanilla rates: amount budget + per-entry %), gated by OUR per-mon <b>proc cadence</b> - the two levers
 * (cadence + amount/yield). The pasture has no defeats, so the proc IS our invention. Nothing ever spawns a
 * world item. {@link #dropTableFor} converts a species' table into our tested {@link DropTable} for the
 * planned override / easter-egg overlay. Item entries only - command / evolution entries are skipped.
 */
public final class DropsBridge {
    private DropsBridge() {}

    /** GP-authored extra drops layered on top of each mon's native table (gold for the gold Pokémon, etc.). */
    private static final SpeciesDropOverlay OVERLAY = SpeciesDropOverlay.defaults();

    /** Convert a species' Cobblemon drops into our {@link DropTable} - for the planned override / easter-egg
     *  overlay (inspect or extend a mon's base table). Never throws. (The base harvest rolls Cobblemon's
     *  {@code getDrops} directly; this is the toolkit for custom / combo tables.) */
    public static DropTable dropTableFor(Pokemon pokemon) {
        try {
            Species species = pokemon.getSpecies();
            com.cobblemon.mod.common.api.drop.DropTable cdrops = species.getDrops();
            List<DropEntry> entries = new ArrayList<>();
            for (com.cobblemon.mod.common.api.drop.DropEntry e : cdrops.getEntries()) {
                if (!(e instanceof ItemDropEntry item)) continue;   // skip command / evolution drops
                Identifier id = item.getItem();                     // ItemDropEntry stores the item id directly
                if (id == null) continue;
                double chance = item.getPercentage() / 100.0;       // Cobblemon percentage is 0..100
                int min, max;
                kotlin.ranges.IntRange range = item.getQuantityRange();
                if (range != null) { min = range.getFirst(); max = range.getLast(); }
                else { min = max = item.getQuantity(); }
                entries.add(new DropEntry(id.toString(), chance, min, max));
            }
            return new DropTable(entries);
        } catch (Throwable t) {
            return DropTable.EMPTY;   // unreadable species/drops → harvest nothing for this mon
        }
    }

    /**
     * Harvest a pasture for one tick: each tethered mon has {@code procChance} to roll a drop EVENT
     * (LEVER 1 - cadence; replaces the wild "on defeat" trigger, since a pasture has no defeats). A procced
     * mon rolls Cobblemon's OWN {@code getDrops} (amount budget + per-entry %, faithful to vanilla - LEVER 2),
     * and we resolve quantities exactly as Cobblemon does. {@code yieldBonus} widens that amount budget's
     * ceiling (the Kernel's Drop Yield mod - a chance at more items per event, never fewer). →
     * {@code item id → total count}. Never throws.
     */
    public static Map<String, Integer> harvest(PokemonPastureBlockEntity pasture, Random rng,
                                               double procChance, int yieldBonus) {
        try {
            return harvest(new ArrayList<>(pasture.getTetheredPokemon()), rng, procChance, yieldBonus);
        } catch (Throwable ex) {
            GreenerPastures.LOG.debug("[harvester] harvest failed", ex);
            return new LinkedHashMap<>();
        }
    }

    /** Roster-list overload: a catch-up rolls MANY sweeps against one immutable roster - snapshot the tether
     *  list once and reuse it across sweeps instead of re-copying per sweep (perf-audit R3 tick #1). */
    public static Map<String, Integer> harvest(java.util.List<PokemonPastureBlockEntity.Tethering> roster, Random rng,
                                               double procChance, int yieldBonus) {
        return harvest(roster, rng, procChance, yieldBonus, s -> 1.0);
    }

    /** Per-species proc scale (species display name → ×N ≥ 1) - the Compression press multiplier. Applied to
     *  the WHOLE proc the caller computed (base + augments), then clamped to certainty. */
    @FunctionalInterface
    public interface SpeciesScale { double scale(String species); }

    /** Species-scaled overload: each mon's proc = {@code min(1, procChance × scale(species))}, so a pressed
     *  species drops more often while every other lever (augments, tethers, yield) is untouched. */
    public static Map<String, Integer> harvest(java.util.List<PokemonPastureBlockEntity.Tethering> roster, Random rng,
                                               double procChance, int yieldBonus, SpeciesScale scale) {
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            for (PokemonPastureBlockEntity.Tethering t : roster) {
                Pokemon p = t.getPokemon();
                if (p == null) continue;
                String species;
                try { species = p.getSpecies().getName(); } catch (Throwable ex) { species = "?"; }
                // Compression is keyed by the FAMILY BASE species (eggs are always the base form), so a
                // Meowth press must apply to a tethered Persian, Gastly to Haunter/Gengar, etc. Resolve the
                // mon down its pre-evolution chain for the lookup; the display name still logs as-is.
                String base;
                try { base = baseSpeciesName(p); } catch (Throwable ex) { base = species; }
                double mult;
                try { mult = Math.max(1.0, scale.scale(base)); } catch (Throwable ex) { mult = 1.0; }
                // Split compression 50/50 (Deuce, 2026-07-22): half the bonus to proc CADENCE (capped at
                // certainty), half to a per-species YIELD multiplier (uncapped) - so compression keeps paying
                // after the frequency wall. lever = 1 + (M-1)/2, e.g. a 2× press → ×1.5 freq + ×1.5 yield.
                double lever = CompressionSplit.lever(mult);
                double proc = Math.min(1.0, procChance * lever);
                if (rng.nextDouble() >= proc) continue;     // FREQUENCY half - this mon didn't proc this tick
                Map<String, Integer> rolled = rollEvent(p, rng, yieldBonus);
                if (lever > 1.0) rolled = CompressionSplit.inflate(rolled, lever, rng::nextDouble);   // YIELD half
                // per-proc audit line: the proc HAPPENED - an empty roll means the species' drop table came up
                // dry (all low-% entries missed), which drop-rate QA must see distinctly from "no proc". When
                // the mon is an evolution, `base` names the family root the compression multiplier came from.
                if (com.greenerpastures.core.GpLog.on(com.greenerpastures.core.GpLog.Level.DEBUG)) {
                    String baseKey = base;
                    com.greenerpastures.core.GpLog.d("harvest", "proc", "species", species,
                            "base", (baseKey != null && !baseKey.equalsIgnoreCase(species)) ? baseKey : "-",
                            "comp_x", mult > 1.0 ? String.format("%.2f", mult) : "1",
                            "split", lever > 1.0 ? String.format("f%.2f/y%.2f", lever, lever) : "-",
                            "items", rolled.isEmpty() ? "dry" : rolled.toString());
                }
                rolled.forEach((id, n) -> out.merge(id, n, Integer::sum));
            }
        } catch (Throwable ex) {
            GreenerPastures.LOG.debug("[harvester] harvest failed", ex);
        }
        return out;
    }

    /** Walk a mon's pre-evolution chain to its family BASE species name (Persian→Meowth, Gengar→Haunter→Gastly)
     *  so a Compression press - always fed from base-form eggs - applies to EVERY evolution of that line, not
     *  just the base. Bounded against a malformed cyclic chain; falls back to the mon's own name if the graph
     *  is unreadable. (MC-coupled: exercised by in-game QA, not the headless suite.) */
    static String baseSpeciesName(Pokemon pokemon) {
        Species s = pokemon.getSpecies();
        Species base = s;
        try {
            for (int i = 0; i < 8 && s != null; i++) {   // real chains are ≤3; the bound just guards cycles
                var pre = s.getPreEvolution();
                if (pre == null) break;
                Species next = pre.getSpecies();
                if (next == null || next == s) break;
                base = s = next;
            }
        } catch (Throwable ignored) { /* unreadable evolution graph → fall back to the mon's own species */ }
        return base != null ? base.getName() : pokemon.getSpecies().getName();
    }

    /** One drop EVENT via Cobblemon's faithful {@code getDrops} (amount budget + per-entry % + canDrop);
     *  item entries only, quantities rolled like Cobblemon ({@code RangesKt.random} over the range). The
     *  amount budget is widened by {@code yieldBonus} (LEVER 2 - the Drop Yield mod). */
    private static Map<String, Integer> rollEvent(Pokemon pokemon, Random rng, int yieldBonus) {
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            com.cobblemon.mod.common.api.drop.DropTable cdrops = pokemon.getSpecies().getDrops();
            kotlin.ranges.IntRange amount = widenAmount(cdrops.getAmount(), yieldBonus);
            for (com.cobblemon.mod.common.api.drop.DropEntry e : cdrops.getDrops(amount, pokemon)) {
                if (!(e instanceof ItemDropEntry item)) continue;
                Identifier id = item.getItem();
                if (id == null) continue;
                int qty = rollQty(item, rng, yieldBonus);
                if (qty > 0) out.merge(id.toString(), qty, Integer::sum);
            }
            // GP overlay: extra entries this species carries beyond its native table. Same proc EVENT (so
            // Drop Rate already gated it) AND the same yieldBonus widens its ceiling (so Drop Yield boosts it
            // just like native drops). Fills farmability holes - chiefly gold. Never fails the harvest.
            String species;
            try { species = pokemon.getSpecies().getName(); } catch (Throwable t) { species = null; }
            OVERLAY.roll(species, rng, yieldBonus).forEach((id, n) -> out.merge(id, n, Integer::sum));
        } catch (Throwable t) {
            // one mon's odd drop table must not abort the whole pasture's harvest
        }
        return out;
    }

    /** Widen a species' {@code amount} budget by raising ONLY the ceiling (the floor stays, so Drop Yield
     *  only ever adds a <i>chance</i> at more budget, never removes any). {@code yieldBonus ≤ 0} is a no-op. */
    static kotlin.ranges.IntRange widenAmount(kotlin.ranges.IntRange base, int yieldBonus) {
        if (yieldBonus <= 0) return base;
        return new kotlin.ranges.IntRange(base.getFirst(), base.getLast() + yieldBonus);
    }

    /** An item entry's quantity, resolved as Cobblemon does (uniform over its range, else its fixed qty), then
     *  WIDENED by Drop Yield: the ceiling rises by {@code yieldBonus} for EVERY entry - so even a
     *  percentage-only, fixed-quantity drop (e.g. a 5%-chance single {@code quick_claw}) rolls a range
     *  {@code [qty, qty + yield]} when it procs, instead of a flat 1 (Deuce, 2026-07-22). The floor is
     *  untouched: Drop Yield is only ever a <i>chance</i> at more, never fewer. Reuses the same tested
     *  {@link DropEntry#widenedBy}/{@link DropEntry#quantityAt} math the overlay gold uses. */
    private static int rollQty(ItemDropEntry item, Random rng, int yieldBonus) {
        kotlin.ranges.IntRange r = item.getQuantityRange();
        int min, max;
        if (r == null) { min = max = item.getQuantity(); }   // fixed-quantity (%-only) entry
        else { min = r.getFirst(); max = r.getLast(); }
        return new DropEntry(item.getItem().toString(), 1.0, min, max)
                .widenedBy(Math.max(0, yieldBonus))
                .quantityAt(rng.nextDouble());
    }
}
