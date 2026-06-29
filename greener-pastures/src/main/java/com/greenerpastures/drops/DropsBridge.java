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
 * {@code CobbreedingBridge}). It converts a tethered mon's Cobblemon {@code DropTable} into our own tested
 * {@link DropTable} (item id + chance + qty range), so WE own the roll: cadence + yield are ours to
 * modulate later (Drop Rate / Drop Yield augments, species-combo easter eggs), and nothing ever spawns a
 * world item. Item entries only — command / evolution drop entries are skipped.
 */
public final class DropsBridge {
    private DropsBridge() {}

    /** Build our drop table from a mon's Cobblemon species drops (item entries only). Never throws. */
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

    /** Roll the drops for every tethered mon in a pasture → {@code item id → total count}. Never throws. */
    public static Map<String, Integer> harvest(PokemonPastureBlockEntity pasture, Random rng) {
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            for (PokemonPastureBlockEntity.Tethering t : new ArrayList<>(pasture.getTetheredPokemon())) {
                Pokemon p = t.getPokemon();
                if (p == null) continue;
                dropTableFor(p).roll(rng).forEach((id, n) -> out.merge(id, n, Integer::sum));
            }
        } catch (Throwable ex) {
            GreenerPastures.LOG.debug("[harvester] roster read failed", ex);
        }
        return out;
    }
}
