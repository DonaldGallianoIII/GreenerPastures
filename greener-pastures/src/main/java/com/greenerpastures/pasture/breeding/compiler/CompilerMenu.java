package com.greenerpastures.pasture.breeding.compiler;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.pasture.breeding.BreedingUpgradeItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * The Compiler bench container. Two transient input slots — Kernel (0) and Augment (1) — plus the
 * player inventory. The compile is driven by a GUI button routed through vanilla {@link #onButtonClick}
 * (no custom packet): it merges the augment's effect into the Kernel's data component IN PLACE and
 * consumes one augment. Inputs are returned to the player on close (nothing is stored in the block).
 */
public class CompilerMenu extends ScreenHandler {
    public static final int COMPILE_BUTTON = 0;

    public static final ScreenHandlerType<CompilerMenu> TYPE =
            new ExtendedScreenHandlerType<>(CompilerMenu::new, BlockPos.PACKET_CODEC);

    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(GreenerPastures.MOD_ID, "compiler_menu"), TYPE);
        // onClosed does NOT fire on disconnect (MC 1.21.1) — return the open bench's inputs so a paid-for
        // Kernel + augment aren't lost if a player drops / crashes / is kicked with the bench open (re-audit C2).
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player.currentScreenHandler instanceof CompilerMenu cm) cm.dropInputs(handler.player);
        });
    }

    // Bench geometry — MUST match CompilerScreen.
    public static final int KERNEL_X = 58, AUG_X = 94, BENCH_Y = 46, PREVIEW_X = 148;
    private static final int INV_X = 34, INV_Y = 124, HOTBAR_Y = 182;

    private final SimpleInventory inputs = new SimpleInventory(2);
    public final BlockPos pos;

    public CompilerMenu(int syncId, PlayerInventory inv, BlockPos pos) {
        super(TYPE, syncId);
        this.pos = pos == null ? BlockPos.ORIGIN : pos;

        addSlot(new Slot(inputs, 0, KERNEL_X, BENCH_Y) {
            @Override public boolean canInsert(ItemStack s) { return s.getItem() instanceof BreedingUpgradeItem; }
            @Override public int getMaxItemCount() { return 1; }
        });
        addSlot(new Slot(inputs, 1, AUG_X, BENCH_Y) {
            @Override public boolean canInsert(ItemStack s) { return s.getItem() instanceof AugmentItem; }
        });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    public ItemStack kernel()  { return inputs.getStack(0); }
    public ItemStack augment() { return inputs.getStack(1); }

    /** The augmented Kernel this bench would produce now, or EMPTY if the inputs aren't compilable. */
    public ItemStack preview() {
        ItemStack k = kernel(), a = augment();
        if (!(a.getItem() instanceof AugmentItem ai)) return ItemStack.EMPTY;
        if (!ai.type.appliesTo(k) || ai.type.installedOn(k)) return ItemStack.EMPTY;
        return ai.type.apply(k);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (id != COMPILE_BUTTON) return false;
        ItemStack k = kernel(), a = augment();
        if (!(a.getItem() instanceof AugmentItem ai)) return false;
        if (!ai.type.appliesTo(k) || ai.type.installedOn(k)) return false;
        inputs.setStack(0, ai.type.apply(k));   // Kernel keeps count 1, now carries the augment
        a.decrement(1);
        inputs.markDirty();
        sendContentUpdates();
        return true;
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (player.getWorld().isClient) return;
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack s = inputs.removeStack(i);
            if (!s.isEmpty() && !player.getInventory().insertStack(s)) player.dropItem(s, false);
        }
    }

    /** Drop the bench inputs into the world — used on DISCONNECT, where {@link #onClosed} never fires and
     *  inserting into the leaving player's already-saved inventory wouldn't persist (re-audit C2). */
    private void dropInputs(PlayerEntity player) {
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack s = inputs.removeStack(i);
            if (!s.isEmpty()) player.dropItem(s, false);
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();
            int invStart = 2;
            if (index < invStart) {
                if (!this.insertItem(original, invStart, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (original.getItem() instanceof BreedingUpgradeItem) {
                if (!this.insertItem(original, 0, 1, false)) return ItemStack.EMPTY;
            } else if (original.getItem() instanceof AugmentItem) {
                if (!this.insertItem(original, 1, 2, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        }
        return newStack;
    }
}
