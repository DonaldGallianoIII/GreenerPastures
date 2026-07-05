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
 * player-owned - anyone with a wand edits it. Single source of truth for everything pasture-related:
 * the wand GUI, the breeding engine, the fill-check, the finder, the dashboards.
 */
public class PastureData {
    /** Human handle for this pasture (set in the wand GUI); the name dashboards/finder reference. */
    public String name = "";
    /** Slot 0 = Pasture Upgrade; slots 1.. = functional upgrades. */
    public final SimpleInventory upgrades = new SimpleInventory(1 + PastureMenu.MAX_FUNCTIONAL);
    /** mon (tetheringId) -> pair bucket number. Set via the wand GUI's pairing board (P2). */
    public final Map<UUID, Integer> pairings = new HashMap<>();

    /** The Daemon visual-scripting graph - filter/sink/source nodes + flow edges + mon-node positions, as JSON
     *  authored in the Notebook console's node editor. The egg-routing eval
     *  ({@link com.greenerpastures.notebook.EggIngest}) reads it; {@code ""} = no graph (keep-all fallback). Persisted. */
    public String graphJson = "";

    /** Bred eggs waiting to drain into the pasture's small visible tray - the FIFO from
     *  {@code EGG_STORAGE_DESIGN.md} (bounded, pause-when-full, never evict). Persisted. */
    public final EggQueue<ItemStack> eggQueue = new EggQueue<>(EggQueue.MIN_CAP);
    /** Next world-time this pasture may breed - in-memory schedule only (NOT persisted; a missed
     *  interval after a restart is harmless). Lives here instead of a static map to avoid a leak. */
    public transient long nextBreedTick = 0L;
    /** The pasture's owner - set by the explicit Notebook-link claim ({@link PastureClaim}). The owner
     *  collects this pasture's drops/eggs/outputs into their Notebook AND their Data account pays its
     *  Soul-Tether burn. Null = unowned → tethers inert (free base), outputs uncollected. */
    public UUID owner;

    /** "Ghost pasture" - when true, this pasture's tethered mons never spawn as roaming entities (the lag fix):
     *  their data still ticks (breeding + loot keep running) but no PokemonEntity exists. Persisted. */
    public boolean suppressed;

    /** World-time (ticks) of the last harvest sweep that actually ran for this pasture - the offline-progress
     *  anchor: when its chunk reloads, the sweep computes the missed interval count from this and rolls a
     *  CATCH-UP deposit ("drops keep accumulating while the chunk is unloaded" - Deuce, 2026-07-03). Persisted;
     *  0 = never swept (no backfill). {@link com.greenerpastures.notebook.OfflineProgress} shifts it past
     *  offline gaps on servers (online away-time counts, offline time doesn't). */
    public long lastHarvestTick;

    /** World-time (ticks) of the last brood that actually ran - the breeding catch-up anchor (same semantics
     *  as {@link #lastHarvestTick}: unloaded-chunk gaps are rolled on reload, offline gaps are gated). Persisted. */
    public long lastBreedTick;

    /** Gacha ritual pull-state per ritual id - {@code [bankedPulls, pity]} (NOTEBOOK_BUILD_PLAN 3b: the state
     *  the retired Harvester block used to hold). Persisted so pity survives restarts - pity is a PROMISE. */
    public final Map<String, int[]> ritualState = new HashMap<>();

    /** The pair cap the breeder actually uses: the tier's pairs + any WILD-corruption bonus on the Kernel
     *  ({@code corrupt_pairs} - the 9-pair Greener exists now). 0 with no Kernel. */
    public int maxPairs() {
        BreedingTier t = tier();
        if (t == null) return 0;
        Integer bonus = upgrades.getStack(0).get(GpComponents.CORRUPT_PAIRS);
        return t.maxPairs + (bonus == null ? 0 : Math.max(0, bonus));
    }

    /** The installed Pasture Upgrade tier (slot 0), or null if none - drives pairs + slot count. */
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

    /** The Kernel's EV allocation spread (slot 0), or {@link EvSpread#NONE} if it carries none. The breeder
     *  pre-sets these EVs per stat on each bred egg (BUG-002 - replaces the old flat "+N on every stat"). */
    public EvSpread evSpread() {
        EvSpread s = upgrades.getStack(0).get(GpComponents.EV_SPREAD);
        return s == null ? EvSpread.NONE : s;
    }

    /** The Soul Tethers slotted into the Kernel's UNLOCKED functional slots, as runtime tethers (blanks
     *  dropped). Gated by the current tier's slot count, so a tether left in a slot a Kernel downgrade has
     *  since hidden is NOT read or drained (it's inaccessible in the GUI - never bill an unreachable slot). */
    public List<SoulTether> slottedTethers() {
        List<SoulTether> out = new ArrayList<>();
        BreedingTier tier = tier();
        int active = (tier == null) ? 0 : Math.min(tier.slots, upgrades.size() - 1);
        for (int i = 1; i <= active; i++) {
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
        if (suppressed) nbt.putBoolean("suppressed", true);
        if (graphJson != null && !graphJson.isEmpty()) nbt.putString("graphJson", graphJson);
        if (lastHarvestTick > 0) nbt.putLong("lastHarvest", lastHarvestTick);
        if (lastBreedTick > 0) nbt.putLong("lastBreed", lastBreedTick);
        if (!ritualState.isEmpty()) {
            NbtCompound rs = new NbtCompound();
            ritualState.forEach((id, st) -> rs.putIntArray(id, new int[]{st[0], st[1]}));
            nbt.put("rituals", rs);
        }
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
        d.suppressed = nbt.getBoolean("suppressed");
        d.graphJson = nbt.getString("graphJson");
        d.lastHarvestTick = nbt.getLong("lastHarvest");
        d.lastBreedTick = nbt.getLong("lastBreed");
        NbtCompound rs = nbt.getCompound("rituals");
        for (String id : rs.getKeys()) {
            int[] st = rs.getIntArray(id);
            if (st.length >= 2) d.ritualState.put(id, new int[]{st[0], st[1]});
        }
        return d;
    }
}
