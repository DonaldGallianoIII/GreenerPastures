package com.greenerpastures.economy;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * The dark economy's Minecraft layer (min-slice): the <b>Renderer</b> block (culls non-keeper eggs →
 * Data) and the <b>Daemon</b> item (the player's handle to their Data balance). Data itself lives
 * per-player in {@link DataStore}. The keep/cull/value logic is the unit-tested {@link RenderRun} /
 * {@code economy} + {@code biobank} cores; this is the thin registration + wiring.
 */
public final class DarkEconomy {
    private DarkEconomy() {}

    public static final Identifier RENDERER_ID = Identifier.of(GreenerPastures.MOD_ID, "renderer");
    public static final Identifier DAEMON_ID = Identifier.of(GreenerPastures.MOD_ID, "daemon");

    public static Block RENDERER;
    public static BlockEntityType<RendererBlockEntity> BE;
    public static Item DAEMON;
    /** The {@code greenerpastures:tether} data component a Soul Tether item carries ([function, tier]). */
    public static ComponentType<Tether> TETHER;
    public static Item SOUL_TETHER;

    public static void init() {
        RENDERER = Registry.register(Registries.BLOCK, RENDERER_ID,
                new RendererBlock(AbstractBlock.Settings.create().strength(3.0f).requiresTool()));
        Registry.register(Registries.ITEM, RENDERER_ID, new BlockItem(RENDERER, new Item.Settings()));
        BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, RENDERER_ID,
                FabricBlockEntityTypeBuilder.create(RendererBlockEntity::new, RENDERER).build());

        DAEMON = Registry.register(Registries.ITEM, DAEMON_ID, new DaemonItem(new Item.Settings().maxCount(1)));

        // Soul Tether: the [function, tier] data component + the (blank-until-inscribed) item.
        TETHER = Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(GreenerPastures.MOD_ID, "tether"),
                ComponentType.<Tether>builder().codec(Tether.CODEC).packetCodec(Tether.PACKET_CODEC).build());
        SOUL_TETHER = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "soul_tether"),
                new SoulTetherItem(new Item.Settings().maxCount(16)));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(e -> e.add(RENDERER));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> { e.add(DAEMON); e.add(SOUL_TETHER); });

        GreenerPastures.LOG.info("[dark-economy] loaded — Renderer + Daemon + Data store + Soul Tether item.");
    }
}
