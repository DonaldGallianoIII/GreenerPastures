package com.shedmon.shedscope;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.HashMap;
import java.util.Map;

import static com.shedmon.shedscope.Target.Category.DEEP_DARK;
import static com.shedmon.shedscope.Target.Category.LOOT;
import static com.shedmon.shedscope.Target.Category.ORE;

/** The default set of blocks ShedScope hunts for. Edit freely. */
public final class Targets {
    public static final Map<Block, Target> BY_BLOCK = new HashMap<>();

    private Targets() {}

    private static void add(Target t, Block... blocks) {
        for (Block b : blocks) BY_BLOCK.put(b, t);
    }

    static {
        // ---- Ores (color roughly matches the gem/metal) ----
        add(new Target("Diamond", 0x4AEDD9, ORE), Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
        add(new Target("Emerald", 0x2BD662, ORE), Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
        add(new Target("Ancient Debris", 0x8A5A4B, ORE), Blocks.ANCIENT_DEBRIS);
        add(new Target("Gold", 0xF2C14E, ORE), Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
        add(new Target("Iron", 0xD8D8D8, ORE), Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
        add(new Target("Copper", 0xE0734D, ORE), Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
        add(new Target("Redstone", 0xFF3A2F, ORE), Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        add(new Target("Lapis", 0x2657D6, ORE), Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
        add(new Target("Coal", 0x6E6E6E, ORE), Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        add(new Target("Quartz", 0xEDE6DD, ORE), Blocks.NETHER_QUARTZ_ORE);
        add(new Target("Amethyst", 0xB983FF, ORE), Blocks.BUDDING_AMETHYST);

        // ---- Loot & structures ----
        add(new Target("Chest", 0xFFB454, LOOT), Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL);
        add(new Target("Spawner", 0x2FE3D0, LOOT), Blocks.SPAWNER);
        add(new Target("Trial Spawner", 0x36C9FF, LOOT), Blocks.TRIAL_SPAWNER);
        add(new Target("Vault", 0xFFE05C, LOOT), Blocks.VAULT);
        add(new Target("Sus. Sand/Gravel", 0xCDBE91, LOOT), Blocks.SUSPICIOUS_SAND, Blocks.SUSPICIOUS_GRAVEL);

        // ---- Deep Dark markers ----
        add(new Target("Sculk Shrieker", 0x14E0C8, DEEP_DARK), Blocks.SCULK_SHRIEKER);
        add(new Target("Sculk Catalyst", 0x1FA89B, DEEP_DARK), Blocks.SCULK_CATALYST);
        add(new Target("Reinforced Deepslate", 0x8A8F94, DEEP_DARK), Blocks.REINFORCED_DEEPSLATE);
    }
}
