package com.greenerpastures.biobank;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * BioBank — AE2 ME-style egg storage block: eggs stored as data, bucketed by species (the lossless
 * "keep" twin of the Renderer; see EGG_DATABASE_DESIGN.md).
 *
 * <p>Batch 1 (this): block + per-world {@link BioBankStore} + deposit-on-right-click + chat summary.
 * Batch 2: the terminal GUI (species tiles + counts + withdraw). Batch 3: hopper / auto-ingest.
 */
public final class BioBank {
    private BioBank() {}

    public static final Identifier ID = Identifier.of(GreenerPastures.MOD_ID, "biobank");

    /** Conservative Batch-1 cap (avoids item-entity spam on break). Config + tiers come later, paired
     *  with a "data cell" drop-on-break so true thousands never scatter as loose stacks. */
    public static final int DEFAULT_CAP = 256;

    public static Block BLOCK;
    public static BlockEntityType<BioBankBlockEntity> BE;

    public static int capacity() { return DEFAULT_CAP; }

    public static void init() {
        BLOCK = Registry.register(Registries.BLOCK, ID,
                new BioBankBlock(AbstractBlock.Settings.create().strength(3.0f).requiresTool()));

        Registry.register(Registries.ITEM, ID, new BlockItem(BLOCK, new Item.Settings()));

        BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, ID,
                FabricBlockEntityTypeBuilder.create(BioBankBlockEntity::new, BLOCK).build());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> e.add(BLOCK));

        GreenerPastures.LOG.info("BioBank loaded (Batch 1: store + deposit + summary).");
    }
}
