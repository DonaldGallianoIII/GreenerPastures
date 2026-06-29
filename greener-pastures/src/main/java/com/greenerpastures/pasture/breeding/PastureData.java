package com.greenerpastures.pasture.breeding;

import com.greenerpastures.economy.AugmentFunction;
import com.greenerpastures.economy.DarkEconomy;
import com.greenerpastures.economy.SoulTether;
import com.greenerpastures.economy.Tether;
import com.greenerpastures.pasture.breeding.gui.PastureMenu;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /** Bred eggs waiting to drain into the pasture's small visible tray — the FIFO from
     *  {@code EGG_STORAGE_DESIGN.md} (bounded, pause-when-full, never evict). Persisted. */
    public final EggQueue<ItemStack> eggQueue = new EggQueue<>(EggQueue.MIN_CAP);
    /** Next world-time this pasture may breed — in-memory schedule only (NOT persisted; a missed
     *  interval after a restart is harmless). Lives here instead of a static map to avoid a leak. */
    public transient long nextBreedTick = 0L;
    /** The pasture's operator — the player who last slotted a Soul Tether here; their Data account pays
     *  the tether burn (rented amplification). Null = no operator → tethers stay inert (free base). */
    public UUID owner;

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

    /** The Kernel's base augment levels (slot 0), as a function→level map for the amplification core. */
    public Map<AugmentFunction, Integer> baseAugmentLevels() {
        Augments a = upgrades.getStack(0).get(GpComponents.AUGMENTS);
        return a == null ? Map.of() : a.toLevels();
    }

    /** The Soul Tethers slotted into the functional slots (1..), as runtime tethers (blanks dropped). */
    public List<SoulTether> slottedTethers() {
        List<SoulTether> out = new ArrayList<>();
        for (int i = 1; i < upgrades.size(); i++) {
            Tether t = upgrades.getStack(i).get(DarkEconomy.TETHER);
            if (t != null && !t.isBlank()) out.add(t.toSoulTether());
        }
        return out;
    }

    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putString("name", name);
        nbt.put("upgrades", upgrades.toNbtList(lookup));
        NbtCompound pn = new NbtCompound();
        pairings.forEach((id, bucket) -> pn.putInt(id.toString(), bucket));
        nbt.put("pairings", pn);

        NbtList qn = new NbtList();
        eggQueue.forEach(egg -> { if (!egg.isEmpty()) qn.add(egg.encode(lookup)); });
        nbt.put("eggQueue", qn);
        if (owner != null) nbt.putUuid("owner", owner);
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

        List<ItemStack> queued = new ArrayList<>();
        for (NbtElement el : nbt.getList("eggQueue", NbtElement.COMPOUND_TYPE)) {
            ItemStack.fromNbt(lookup, el).ifPresent(queued::add);
        }
        d.eggQueue.restore(queued);
        if (nbt.containsUuid("owner")) d.owner = nbt.getUuid("owner");
        return d;
    }
}
