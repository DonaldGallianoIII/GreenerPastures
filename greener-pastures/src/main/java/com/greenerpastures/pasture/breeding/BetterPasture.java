package com.greenerpastures.pasture.breeding;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.pasture.breeding.compiler.AugmentItem;
import com.greenerpastures.pasture.breeding.compiler.AugmentType;
import com.greenerpastures.pasture.breeding.compiler.CompilerBlock;
import com.greenerpastures.pasture.breeding.compiler.CompilerMenu;
import com.greenerpastures.pasture.breeding.gui.PastureMenu;
import com.greenerpastures.pasture.breeding.net.PastureNet;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Better Pasture module — opt-in multi-pair breeding via the Pasture Wand GUI + slotted Pasture
 * Upgrades. All Cobbreeding contact is isolated in {@link CobbreedingBridge} (version-guarded);
 * the breeding engine is {@link MultiPairBreeder}. Stays dormant if Cobbreeding is unavailable.
 */
public final class BetterPasture {
    private BetterPasture() {}

    /** Pasture Upgrade items (the slot-expander / pair-scaler line), keyed by tier. */
    public static final Map<BreedingTier, BreedingUpgradeItem> ITEMS = new EnumMap<>(BreedingTier.class);
    public static PastureWand WAND;
    /** The Compiler block (apply augments to a Kernel) + its item form. */
    public static Block COMPILER_BLOCK;
    public static Item COMPILER_ITEM;
    /** Augment "packages" (slot with a Kernel at the Compiler). v1: the Shiny augment. */
    public static AugmentItem AUGMENT_SHINY;

    public static void init() {
        GpComponents.init();        // register greenerpastures:augments before any item can carry it
        CobbreedingBridge.init();
        PastureMenu.register();
        CompilerMenu.register();    // the Compiler bench container
        PastureNet.init();          // C2S: save pasture name + pair assignments
        registerItems();
        if (CobbreedingBridge.isAvailable()) {
            MultiPairBreeder.init();
            GreenerPastures.LOG.info("[better-pasture] active; {} pasture-upgrade tiers + wand registered.", ITEMS.size());
        } else {
            GreenerPastures.LOG.info("[better-pasture] items registered; breeding dormant (Cobbreeding unavailable).");
        }
    }

    private static void registerItems() {
        for (BreedingTier tier : BreedingTier.values()) {
            BreedingUpgradeItem item = new BreedingUpgradeItem(tier, new Item.Settings().maxCount(16));
            Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "breeding_upgrade_" + tier.id()), item);
            ITEMS.put(tier, item);
        }
        WAND = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "pasture_wand"),
                new PastureWand(new Item.Settings().maxCount(1)));

        // Compiler block (+ its item) and the augment packages it installs onto a Kernel.
        COMPILER_BLOCK = Registry.register(Registries.BLOCK, Identifier.of(GreenerPastures.MOD_ID, "compiler"),
                new CompilerBlock(AbstractBlock.Settings.create().strength(2.5f).requiresTool().nonOpaque()));
        COMPILER_ITEM = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "compiler"),
                new BlockItem(COMPILER_BLOCK, new Item.Settings()));
        AUGMENT_SHINY = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "augment_shiny"),
                new AugmentItem(AugmentType.SHINY, new Item.Settings()));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(WAND);
            ITEMS.values().forEach(entries::add);
            entries.add(AUGMENT_SHINY);
            entries.add(COMPILER_ITEM);
        });
    }
}
