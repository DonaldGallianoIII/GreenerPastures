package com.deuce.shinycollector;

import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Shiny Egg Collector. Every {@link #INTERVAL} ticks it scans containers within
 * {@link #RADIUS} blocks (via the Fabric Transfer API, so it reaches vanilla chests AND
 * Sophisticated Storage) and pulls shiny eggs into its own 27-slot inventory.
 */
public class ShinyCollectorBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

    public static final int SIZE = 27;
    private static final int RADIUS = 6;     // cube half-extent
    private static final int INTERVAL = 20;  // ticks between sweeps (1s)

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(SIZE, ItemStack.EMPTY);

    public ShinyCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ShinyEggCollector.COLLECTOR_BE, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ShinyCollectorBlockEntity be) {
        if (world.isClient) return;
        if (world.getTime() % INTERVAL != 0) return;

        Storage<ItemVariant> self = InventoryStorage.of(be, null);
        if (self == null) return;

        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos p = pos.add(dx, dy, dz);
                    Storage<ItemVariant> src = ItemStorage.SIDED.find(world, p, null);
                    if (src == null || src == self || !src.supportsExtraction()) continue;

                    try (Transaction tx = Transaction.openOuter()) {
                        long moved = StorageUtil.move(
                                src, self,
                                v -> ShinyEggUtil.isShinyEgg(v.toStack()),
                                Long.MAX_VALUE, tx);
                        if (moved > 0) tx.commit();
                        else tx.abort();
                    }
                }
            }
        }
    }

    // --- Inventory ------------------------------------------------------------
    @Override public int size() { return SIZE; }

    @Override public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStack(int slot) { return items.get(slot); }

    @Override public ItemStack removeStack(int slot, int amount) {
        ItemStack r = Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) markDirty();
        return r;
    }

    @Override public ItemStack removeStack(int slot) { return Inventories.removeStack(items, slot); }

    @Override public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
        markDirty();
    }

    @Override public boolean canPlayerUse(PlayerEntity player) {
        if (world == null || world.getBlockEntity(pos) != this) return false;
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override public void clear() { items.clear(); }

    // --- persistence ----------------------------------------------------------
    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, items, registries);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, items, registries);
    }

    // --- screen ---------------------------------------------------------------
    @Override public Text getDisplayName() {
        return Text.translatable("block.shinyeggcollector.shiny_egg_collector");
    }

    @Nullable
    @Override public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
    }
}
