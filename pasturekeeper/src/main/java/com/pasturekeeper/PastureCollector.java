package com.pasturekeeper;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.List;

/**
 * Sweeps pasture loot — the ground items pastureLoot spits out each tick (off the tethered data, so
 * it keeps generating even when wandering is suppressed) — into a chest placed next to the pasture.
 *
 * Opt-in: if no adjacent inventory exists, it does nothing and loot drops normally. The chest is the
 * "collection GUI" — just open it. Called at the pasture's own check cadence ({@code checkPokemon}).
 */
public final class PastureCollector {
    private PastureCollector() {}

    private static final double RADIUS = 4.0;

    public static void collect(World world, BlockPos pasturePos) {
        if (world == null || world.isClient || pasturePos == null) return;
        Inventory inv = findInventory(world, pasturePos);
        if (inv == null) return;

        Box box = new Box(pasturePos).expand(RADIUS);
        List<ItemEntity> items = world.getEntitiesByClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getStack().isEmpty());
        boolean changed = false;
        for (ItemEntity ie : items) {
            int before = ie.getStack().getCount();
            ItemStack left = insert(inv, ie.getStack());
            if (left.isEmpty()) {
                ie.discard();
                changed = true;
            } else if (left.getCount() != before) {
                ie.setStack(left);
                changed = true;
            }
        }
        if (changed) inv.markDirty();
    }

    private static Inventory findInventory(World world, BlockPos p) {
        BlockPos[] around = { p.up(), p.up(2), p.north(), p.south(), p.east(), p.west(), p.down() };
        for (BlockPos n : around) {
            BlockEntity be = world.getBlockEntity(n);
            if (be instanceof Inventory inv) return inv;
        }
        return null;
    }

    /** Insert as much of the stack as fits (merge then fill empties); returns the remainder. */
    private static ItemStack insert(Inventory inv, ItemStack incoming) {
        ItemStack stack = incoming.copy();
        int size = inv.size();
        for (int s = 0; s < size && !stack.isEmpty(); s++) {            // merge into matching stacks
            ItemStack slot = inv.getStack(s);
            if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                int max = Math.min(inv.getMaxCountPerStack(), slot.getMaxCount());
                int can = max - slot.getCount();
                if (can > 0) {
                    int moved = Math.min(can, stack.getCount());
                    slot.increment(moved);
                    stack.decrement(moved);
                }
            }
        }
        for (int s = 0; s < size && !stack.isEmpty(); s++) {            // fill empty slots
            if (inv.getStack(s).isEmpty()) {
                int max = Math.min(inv.getMaxCountPerStack(), stack.getMaxCount());
                int moved = Math.min(max, stack.getCount());
                ItemStack put = stack.copy();
                put.setCount(moved);
                inv.setStack(s, put);
                stack.decrement(moved);
            }
        }
        return stack;
    }
}
