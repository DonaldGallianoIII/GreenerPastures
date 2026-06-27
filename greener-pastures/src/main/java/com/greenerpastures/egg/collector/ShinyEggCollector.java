package com.greenerpastures.egg.collector;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShinyEggCollector {
    public static final String MOD_ID = "greenerpastures";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Identifier BLOCK_ID = Identifier.of(MOD_ID, "shiny_egg_collector");

    public static Block COLLECTOR_BLOCK;
    public static BlockEntityType<ShinyCollectorBlockEntity> COLLECTOR_BE;

    public static void init() {
        COLLECTOR_BLOCK = Registry.register(Registries.BLOCK, BLOCK_ID,
                new ShinyCollectorBlock(AbstractBlock.Settings.create().strength(2.0f).requiresTool().nonOpaque()));

        Registry.register(Registries.ITEM, BLOCK_ID,
                new BlockItem(COLLECTOR_BLOCK, new Item.Settings()));

        COLLECTOR_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, BLOCK_ID,
                FabricBlockEntityTypeBuilder.create(ShinyCollectorBlockEntity::new, COLLECTOR_BLOCK).build());

        LOGGER.info("Shiny Egg Collector loaded.");
    }
}
