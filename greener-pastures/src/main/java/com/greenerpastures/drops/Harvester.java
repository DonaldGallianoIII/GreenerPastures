package com.greenerpastures.drops;

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
 * The passive-drops collector — registration for the {@link HarvesterBlock} + its block entity. Drops are
 * <b>materials only</b> (never Data); they're rolled headlessly and stored in the block, never spawned as
 * world items. The drops arm of the dark economy: eggs → Renderer → Data · drops → Harvester → materials.
 */
public final class Harvester {
    private Harvester() {}

    public static final Identifier ID = Identifier.of(GreenerPastures.MOD_ID, "harvester");

    public static Block BLOCK;
    public static BlockEntityType<HarvesterBlockEntity> BE;

    public static void init() {
        BLOCK = Registry.register(Registries.BLOCK, ID,
                new HarvesterBlock(AbstractBlock.Settings.create().strength(3.0f).requiresTool()));
        Registry.register(Registries.ITEM, ID, new BlockItem(BLOCK, new Item.Settings()));
        BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, ID,
                FabricBlockEntityTypeBuilder.create(HarvesterBlockEntity::new, BLOCK).build());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> e.add(BLOCK));
        GreenerPastures.LOG.info("[harvester] passive-drops collector loaded.");
    }
}
