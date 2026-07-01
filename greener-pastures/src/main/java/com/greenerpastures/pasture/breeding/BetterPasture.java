package com.greenerpastures.pasture.breeding;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.AugmentFunction;
import com.greenerpastures.notebook.PastureSnapshotStore;
import com.greenerpastures.pasture.breeding.gui.PastureMenu;
import com.greenerpastures.pasture.breeding.net.PastureNet;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.Map;

/**
 * Better Pasture module — opt-in multi-pair breeding via the Notebook + slotted Pasture
 * Upgrades. All Cobbreeding contact is isolated in {@link CobbreedingBridge} (version-guarded);
 * the breeding engine is {@link MultiPairBreeder}. Stays dormant if Cobbreeding is unavailable.
 */
public final class BetterPasture {
    private BetterPasture() {}

    /** Pasture Upgrade items (the slot-expander / pair-scaler line), keyed by tier. */
    public static final Map<BreedingTier, BreedingUpgradeItem> ITEMS = new EnumMap<>(BreedingTier.class);

    public static void init() {
        GpComponents.init();        // register greenerpastures:augments before any item can carry it
        CobbreedingBridge.init();
        PastureMenu.register();
        PastureNet.init();          // C2S: save pasture name + pair assignments
        registerBreakCleanup();     // return a broken pasture's items + eggs, free its record (review H1)
        registerItems();
        if (CobbreedingBridge.isAvailable()) {
            MultiPairBreeder.init();
            GreenerPastures.LOG.info("[better-pasture] active; {} pasture-upgrade tiers registered.", ITEMS.size());
        } else {
            GreenerPastures.LOG.info("[better-pasture] items registered; breeding dormant (Cobbreeding unavailable).");
        }
    }

    private static void registerItems() {
        // every Kernel ships with a base drop-rate perk that SCALES with the tier (copper +0.25% … greener
        // +1.50% — BUG-001 fix; was a flat +0.25% on every tier): a default augments component, visible on the
        // tooltip, read by the Harvester, and amplifiable by a Drop Rate tether (Deuce, 2026-06-29).
        for (BreedingTier tier : BreedingTier.values()) {
            Augments kernelBase = Augments.NONE.withLevel(AugmentFunction.DROP_RATE, tier.baseDropRateCentipercent());
            BreedingUpgradeItem item = new BreedingUpgradeItem(tier,
                    new Item.Settings().maxCount(16).component(GpComponents.AUGMENTS, kernelBase));
            Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "breeding_upgrade_" + tier.id()), item);
            ITEMS.put(tier, item);
        }
        // Augments are no longer physical items — they're applied to a Kernel in the Notebook's Augmenter tab
        // (the AugmentType catalog + a GPU reagent). Only the Kernels themselves show in the creative group.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            ITEMS.values().forEach(entries::add);
        });
    }

    /**
     * When a managed pasture's block is broken, return our items (the slotted Pasture Upgrade + any
     * augmented Kernels) and any queued eggs to the world, then free the record — otherwise both are lost
     * and the record lingers in the save + is rescanned every breeding tick forever (review H1).
     */
    private static void registerBreakCleanup() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld sw)) return;
            PastureRegistry reg = PastureRegistry.get(sw.getServer());
            // the record is keyed at the pasture's bottom (block-entity) pos; the player may break the
            // bottom OR the top half, so resolve both before doing any work.
            BlockPos at = reg.get(sw, pos) != null ? pos
                        : reg.get(sw, pos.down()) != null ? pos.down() : null;
            if (at == null) return;
            PastureData pd = reg.get(sw, at);
            if (pd != null) reclaim(sw, at, pd, reg);
        });
    }

    /**
     * Return a pasture's items (the slotted Pasture Upgrade + augmented Kernels + any queued eggs) to the
     * world and free its record. Shared by the player-break hook above and the breeder's non-player-removal
     * reclaim — TNT / creeper / {@code /setblock} / piston don't fire {@code PlayerBlockBreakEvents}
     * (re-audit H2). Caller must not be mid-iterating the registry's live sub-map (this calls remove()).
     */
    public static void reclaim(ServerWorld world, BlockPos at, PastureData pd, PastureRegistry reg) {
        ItemScatterer.spawn(world, at, pd.upgrades);   // drops + empties the upgrade inventory
        int eggs = pd.eggQueue.size();
        pd.eggQueue.forEach(egg -> {
            if (!egg.isEmpty()) ItemScatterer.spawn(world, at.getX(), at.getY(), at.getZ(), egg);
        });
        reg.remove(world, at);
        // forget any per-player console snapshots of this now-gone pasture so the store shrinks with the world (perf-audit H1)
        PastureSnapshotStore.get(world.getServer()).removeAt(
                world.getRegistryKey().getValue().toString(), at.asLong());
        GpLog.i("pasture", "reclaim", "pos", at.toShortString(), "eggsDropped", eggs);
    }
}
