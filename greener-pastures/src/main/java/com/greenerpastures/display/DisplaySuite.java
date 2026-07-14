package com.greenerpastures.display;

import com.greenerpastures.GreenerPastures;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * The <b>Display Suite</b>'s Minecraft layer (Deuce, 2026-07-14 - {@code docs/dev/DISPLAY_SPEC.md}):
 * the <b>Exhibit Pen</b> (specimen-disk-fed pasture clone - mons roam, no breeding) and the
 * <b>Specimen Statue</b> (disk in a plinth renders a frozen, positionable mon). GP's first placed
 * blocks. The decision logic is the unit-tested {@link ExhibitRules} / {@link StatueTransform} /
 * {@link RenderSpec} cores; this is the thin registration + wiring.
 *
 * <p>Init here is pure vanilla registration - every Cobblemon touch lives in
 * {@link CobblemonProjector}, class-loaded on first block interaction, never during mod init
 * (the NeoForge/Connector lazy-probe lesson, spec §6).
 */
public final class DisplaySuite {
    private DisplaySuite() {}

    /** Marks a pen's roaming projection: {@code EntityNoSaveMixin} refuses to serialize any entity
     *  carrying it, so a projection can never survive its disk (the projection principle, spec §0). */
    public static final String PROJECTION_TAG = "gp_exhibit_projection";

    // v1 knobs (constants for now; promote to pastures.json-style config if servers ask - spec §1/§2).
    public static final int EXHIBIT_SLOTS = ExhibitRules.DEFAULT_SLOTS;
    public static final double STATUE_MAX_SCALE = 3.0;

    public static Block EXHIBIT_PEN;
    public static Block SPECIMEN_STATUE;
    public static BlockEntityType<ExhibitPenBlockEntity> EXHIBIT_PEN_BE;
    public static BlockEntityType<StatueBlockEntity> SPECIMEN_STATUE_BE;

    public static void init() {
        EXHIBIT_PEN = block("exhibit_pen", new ExhibitPenBlock(AbstractBlock.Settings.create()
                .strength(1.5f).sounds(BlockSoundGroup.WOOD).nonOpaque()));
        SPECIMEN_STATUE = block("specimen_statue", new StatueBlock(AbstractBlock.Settings.create()
                .strength(1.5f).sounds(BlockSoundGroup.STONE).nonOpaque()));

        EXHIBIT_PEN_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(GreenerPastures.MOD_ID, "exhibit_pen"),
                BlockEntityType.Builder.create(ExhibitPenBlockEntity::new, EXHIBIT_PEN).build(null));
        SPECIMEN_STATUE_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(GreenerPastures.MOD_ID, "specimen_statue"),
                BlockEntityType.Builder.create(StatueBlockEntity::new, SPECIMEN_STATUE).build(null));

        GreenerPastures.LOG.info("[display] loaded - Exhibit Pen + Specimen Statue registered.");
    }

    private static Block block(String id, Block block) {
        Identifier ident = Identifier.of(GreenerPastures.MOD_ID, id);
        Block registered = Registry.register(Registries.BLOCK, ident, block);
        Registry.register(Registries.ITEM, ident, new BlockItem(registered, new Item.Settings()));
        return registered;
    }
}
