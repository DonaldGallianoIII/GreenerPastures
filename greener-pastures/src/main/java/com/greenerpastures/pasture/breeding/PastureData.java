package com.greenerpastures.pasture.breeding;

import com.greenerpastures.pasture.breeding.gui.PastureMenu;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The server-side, per-pasture record (keyed by position in {@link PastureRegistry}). Shared, not
 * player-owned — anyone with a wand edits it. Single source of truth for everything pasture-related:
 * the wand GUI, the breeding engine, the fill-check, the finder, the dashboards.
 */
public class PastureData {
    /** Human handle for this pasture (set in the wand GUI); the name dashboards/finder reference. */
    public String name = "";
    /** Slot 0 = Pasture Upgrade; slots 1.. = functional upgrades. */
    public final SimpleInventory upgrades = new SimpleInventory(1 + PastureMenu.MAX_FUNCTIONAL);
    /** mon (tetheringId) -> pair bucket number. Set via the wand GUI's pairing board (P2). */
    public final Map<UUID, Integer> pairings = new HashMap<>();

    /** The installed Pasture Upgrade tier (slot 0), or null if none — drives pairs + slot count. */
    public BreedingTier tier() {
        ItemStack s = upgrades.getStack(0);
        return (s.getItem() instanceof BreedingUpgradeItem bui) ? bui.tier() : null;
    }

    /**
     * Shiny proc chance (0..1) granted by the slot-0 upgrade's {@code augments} component, or 0 if it
     * carries no shiny augment. Deliberately SEPARATE from {@link #tier()}: the tier alone gives pairs
     * + slots; shiny is a paid augment Compiled onto the item (see {@link Augments}).
     */
    public double shinyProcChance() {
        Augments a = upgrades.getStack(0).get(GpComponents.AUGMENTS);
        return a == null ? 0.0 : a.shinyProcChance();
    }

    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putString("name", name);
        nbt.put("upgrades", upgrades.toNbtList(lookup));
        NbtCompound pn = new NbtCompound();
        pairings.forEach((id, bucket) -> pn.putInt(id.toString(), bucket));
        nbt.put("pairings", pn);
        return nbt;
    }

    public static PastureData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        PastureData d = new PastureData();
        d.name = nbt.getString("name");
        d.upgrades.readNbtList(nbt.getList("upgrades", NbtElement.COMPOUND_TYPE), lookup);
        NbtCompound pn = nbt.getCompound("pairings");
        for (String k : pn.getKeys()) {
            try {
                d.pairings.put(UUID.fromString(k), pn.getInt(k));
            } catch (IllegalArgumentException ignored) {
                // drop a malformed key
            }
        }
        return d;
    }
}
