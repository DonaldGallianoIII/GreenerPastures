package com.greenerpastures.pasture.breeding.gui;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.economy.DarkEconomy;
import com.greenerpastures.pasture.breeding.BreedingUpgradeItem;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Container for the Pasture Wand GUI. Slot 0 = Pasture Upgrade (slot-expander); slots 1..MAX_FUNCTIONAL
 * are functional slots, enabled only up to the count the slotted upgrade unlocks. The mon roster is
 * delivered to the client via the extended screen-handler ({@link PastureOpenData}); the pairing board
 * (P2c) renders from it.
 */
public class PastureMenu extends ScreenHandler {
    public static final int MAX_FUNCTIONAL = 8;
    public static final ScreenHandlerType<PastureMenu> TYPE =
            new ExtendedScreenHandlerType<>(PastureMenu::new, PastureOpenData.CODEC);

    public static void register() {
        Registry.register(Registries.SCREEN_HANDLER, Identifier.of(GreenerPastures.MOD_ID, "pasture_menu"), TYPE);
    }

    // GUI layout — matches Deuce's GreenerPastures-Layout.html (wand canvas 200×166, +hotbar row).
    // Upgrade slots are a contiguous 9-wide row at x=8,y=40 (slot 0 = Pasture Upgrade, 1..8 functional).
    private static final int SLOT_Y   = 40;   // upgrade slot row (design upgradeSlots.y)
    private static final int INV_X    = 8;    // design invGrid.x
    private static final int INV_Y    = 104;  // design invGrid.y (3 main rows)
    private static final int HOTBAR_Y = 162;  // +4px below the 3rd row (design omits the hotbar)

    private final Inventory upgrades;
    public final BlockPos pasturePos;
    private final String pastureName;
    private final List<MonEntry> roster;

    /** Client ctor — built from the server payload. */
    public PastureMenu(int syncId, PlayerInventory inv, PastureOpenData data) {
        this(syncId, inv, new SimpleInventory(1 + MAX_FUNCTIONAL), data.pos(), data.name(), data.roster());
    }

    /** Server ctor — backed by the pasture's real upgrade inventory. */
    public PastureMenu(int syncId, PlayerInventory inv, Inventory upgrades, BlockPos pasturePos, String name) {
        this(syncId, inv, upgrades, pasturePos, name, List.of());
    }

    private PastureMenu(int syncId, PlayerInventory inv, Inventory upgrades, BlockPos pasturePos,
                        String name, List<MonEntry> roster) {
        super(TYPE, syncId);
        this.upgrades = upgrades;
        this.pasturePos = pasturePos;
        this.pastureName = name == null ? "" : name;
        this.roster = roster;

        addSlot(new Slot(upgrades, 0, 8, SLOT_Y) {
            @Override public boolean canInsert(ItemStack s) { return s.getItem() instanceof BreedingUpgradeItem; }
            @Override public int getMaxItemCount() { return 1; }
        });
        for (int i = 0; i < MAX_FUNCTIONAL; i++) {
            final int fi = i;
            // contiguous with slot 0: columns 1..8 of the 9-wide row (x = 8 + (i+1)*18)
            addSlot(new Slot(upgrades, 1 + i, 26 + i * 18, SLOT_Y) {
                @Override public boolean isEnabled() { return fi < unlockedSlots(); }
                @Override public boolean canInsert(ItemStack s) {
                    return fi < unlockedSlots() && !(s.getItem() instanceof BreedingUpgradeItem);
                }
                @Override public int getMaxItemCount() { return 1; }
            });
        }
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, INV_X + c * 18, HOTBAR_Y));
    }

    public List<MonEntry> roster() { return roster; }

    /** This pasture's display name (from the server) — seeds the wand GUI's name field. */
    public String pastureName() { return pastureName; }

    /**
     * Breeding pairs the slotted Pasture Upgrade allows — the arrangement board's bucket count.
     * Computed live from the upgrade slot (like {@link #unlockedSlots()}) so it updates the moment a
     * Pasture Upgrade is inserted, no GUI reopen needed.
     */
    public int maxPairs() {
        ItemStack s = upgrades.getStack(0);
        return (s.getItem() instanceof BreedingUpgradeItem bui) ? bui.tier().maxPairs : 0;
    }

    public int unlockedSlots() {
        ItemStack s = upgrades.getStack(0);
        return (s.getItem() instanceof BreedingUpgradeItem bui) ? bui.tier().slots : 0;
    }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        // The tether-slotter becomes the pasture operator (whose Data account pays the burn). Recorded on
        // close whenever a Soul Tether sits in a functional slot (server-authoritative).
        if (player instanceof ServerPlayerEntity sp && sp.getServer() != null) {
            boolean hasTether = false;
            for (int i = 1; i < upgrades.size(); i++) {
                if (upgrades.getStack(i).get(DarkEconomy.TETHER) != null) { hasTether = true; break; }
            }
            if (hasTether) {
                PastureRegistry reg = PastureRegistry.get(sp.getServer());
                PastureData pd = reg.get(sp.getServerWorld(), pasturePos);
                if (pd != null) { pd.owner = sp.getUuid(); reg.markDirty(); }
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack stack = slot.getStack();
            result = stack.copy();
            int invStart = 1 + MAX_FUNCTIONAL;
            if (index < invStart) {
                if (!this.insertItem(stack, invStart, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!insertIntoUpgrades(stack, invStart)) {     // respects each upgrade slot's max of 1
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY); else slot.markDirty();
        }
        return result;
    }

    /**
     * Shift-click insert into the upgrade region (slots {@code 0..to}). Unlike vanilla {@code insertItem},
     * this honors each slot's {@link Slot#getMaxItemCount()} — the upgrade slots cap at 1, so a shift-clicked
     * stack of upgrades can't overfill one (bug-hunt #5). Returns whether anything moved.
     */
    private boolean insertIntoUpgrades(ItemStack stack, int to) {
        boolean moved = false;
        for (int i = 0; i < to && !stack.isEmpty(); i++) {
            Slot s = this.slots.get(i);
            if (!s.canInsert(stack)) continue;
            int cap = Math.min(s.getMaxItemCount(), stack.getMaxCount());
            ItemStack cur = s.getStack();
            if (cur.isEmpty()) {
                if (cap <= 0) continue;
                ItemStack put = stack.copy();
                put.setCount(Math.min(cap, stack.getCount()));
                stack.decrement(put.getCount());
                s.setStack(put);
                s.markDirty();
                moved = true;
            } else if (ItemStack.areItemsAndComponentsEqual(cur, stack) && cur.getCount() < cap) {
                int n = Math.min(cap - cur.getCount(), stack.getCount());
                cur.increment(n);
                stack.decrement(n);
                s.markDirty();
                moved = true;
            }
        }
        return moved;
    }
}
