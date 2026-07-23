package com.greenerpastures.drops;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A Pokémon's drop table - a list of {@link DropEntry}. Rolling it yields {@code item id → total count}
 * for the entries that fired. Minecraft-free + unit-tested. The Harvester block rolls a tethered mon's
 * table each harvest cycle and deposits the result straight into its own inventory - never a world item,
 * so nothing despawns and there's no ground-entity lag.
 */
public record DropTable(List<DropEntry> entries) {
    public static final DropTable EMPTY = new DropTable(List.of());

    public DropTable {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** A copy with every entry's quantity ceiling raised by {@code bonus} (the Drop Yield lever - see
     *  {@link DropEntry#widenedBy}). {@code bonus <= 0} returns this table unchanged. */
    public DropTable widenedBy(int bonus) {
        if (bonus <= 0 || entries.isEmpty()) return this;
        java.util.List<DropEntry> w = new java.util.ArrayList<>(entries.size());
        for (DropEntry e : entries) w.add(e.widenedBy(bonus));
        return new DropTable(w);
    }

    /** Roll the whole table once → {@code item id → total count} (entries that don't fire are absent). */
    public Map<String, Integer> roll(Random rng) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (DropEntry e : entries) {
            if (e.dropsAt(rng.nextDouble())) {
                int q = e.quantityAt(rng.nextDouble());
                if (q > 0) out.merge(e.itemId(), q, Integer::sum);
            }
        }
        return out;
    }
}
