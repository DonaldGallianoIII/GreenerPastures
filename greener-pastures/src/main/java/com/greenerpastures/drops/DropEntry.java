package com.greenerpastures.drops;

/**
 * One entry in a Pokémon's drop table - an item that drops with {@code chance} (0..1) in quantity
 * {@code [min, max]}. Minecraft-free + unit-tested. The MC adapter ({@code DropsBridge}) builds these from
 * a Cobblemon drop table, and we roll them OURSELVES so the cadence / yield is ours to modulate later
 * (Drop Rate / Drop Yield augments, species-combo easter eggs) rather than Cobblemon's.
 */
public record DropEntry(String itemId, double chance, int min, int max) {
    public DropEntry {
        if (chance < 0.0) chance = 0.0;
        if (chance > 1.0) chance = 1.0;
        if (min < 0) min = 0;
        if (max < min) max = min;
    }

    /** Does this entry drop, given a uniform [0,1) roll? */
    public boolean dropsAt(double roll) {
        return roll < chance;
    }

    /** Quantity for one successful drop, given a uniform [0,1) roll over the inclusive {@code [min,max]}. */
    public int quantityAt(double roll) {
        if (max <= min) return min;
        int q = min + (int) Math.floor(roll * (max - min + 1));
        return Math.min(q, max);   // guard the roll == 1.0 edge
    }

    /** A copy with the quantity ceiling raised by {@code bonus} (floor unchanged) - the Drop Yield lever,
     *  mirroring {@link DropsBridge#widenAmount} for native drops: only ever a chance at MORE, never fewer.
     *  {@code bonus <= 0} is a no-op. */
    public DropEntry widenedBy(int bonus) {
        return bonus <= 0 ? this : new DropEntry(itemId, chance, min, max + bonus);
    }
}
