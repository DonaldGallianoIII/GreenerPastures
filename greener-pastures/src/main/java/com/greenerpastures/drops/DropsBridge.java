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
 * The ONE place Greener Pastures reads Cobblemon's drop tables — isolated + fail-safe (mirrors
 * {@code CobbreedingBridge}). The Harvester's base roll uses Cobblemon's OWN {@code getDrops} (faithful to
 * vanilla rates: amount budget + per-entry %), gated by OUR per-mon <b>proc cadence</b> — the two levers
 * (cadence + amount/yield). The pasture has no defeats, so the proc IS our invention. Nothing ever spawns a
 * world item. {@link #dropTableFor} converts a species' table into our tested {@link DropTable} for the
 * planned override / easter-egg overlay. Item entries only — command / evolution entries are skipped.
 */
public final class DropsBridge {
    private DropsBridge() {}

    /** Convert a species' Cobblemon drops into our {@link DropTable} — for the planned override / easter-egg
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
     * (LEVER 1 — cadence; replaces the wild "on defeat" trigger, since a pasture has no defeats). A procced
     * mon rolls Cobblemon's OWN {@code getDrops} (amount budget + per-entry %, faithful to vanilla — LEVER 2),
     * and we resolve quantities exactly as Cobblemon does. → {@code item id → total count}. Never throws.
     */
    public static Map<String, Integer> harvest(PokemonPastureBlockEntity pasture, Random rng, double procChance) {
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            for (PokemonPastureBlockEntity.Tethering t : new ArrayList<>(pasture.getTetheredPokemon())) {
                if (rng.nextDouble() >= procChance) continue;     // this mon didn't proc a drop this tick
                Pokemon p = t.getPokemon();
                if (p != null) rollEvent(p, rng).forEach((id, n) -> out.merge(id, n, Integer::sum));
            }
        } catch (Throwable ex) {
            GreenerPastures.LOG.debug("[harvester] harvest failed", ex);
        }
        return out;
    }

    /** One drop EVENT via Cobblemon's faithful {@code getDrops} (amount budget + per-entry % + canDrop);
     *  item entries only, quantities rolled like Cobblemon ({@code RangesKt.random} over the range). */
    private static Map<String, Integer> rollEvent(Pokemon pokemon, Random rng) {
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            com.cobblemon.mod.common.api.drop.DropTable cdrops = pokemon.getSpecies().getDrops();
            for (com.cobblemon.mod.common.api.drop.DropEntry e : cdrops.getDrops(cdrops.getAmount(), pokemon)) {
                if (!(e instanceof ItemDropEntry item)) continue;
                Identifier id = item.getItem();
                if (id == null) continue;
                int qty = rollQty(item, rng);
                if (qty > 0) out.merge(id.toString(), qty, Integer::sum);
            }
        } catch (Throwable t) {
            // one mon's odd drop table must not abort the whole pasture's harvest
        }
        return out;
    }

    /** An item entry's quantity, resolved exactly as Cobblemon: uniform over its range, else its fixed qty. */
    private static int rollQty(ItemDropEntry item, Random rng) {
        kotlin.ranges.IntRange r = item.getQuantityRange();
        if (r == null) return item.getQuantity();
        int min = r.getFirst(), max = r.getLast();
        if (max <= min) return Math.max(0, min);
        return min + rng.nextInt(max - min + 1);
    }
}
