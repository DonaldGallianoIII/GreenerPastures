package com.pokesnackplanner;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared planner state + the cobblemon:poke_snack spawn constants (read from Cobblemon source):
 *  - FixedAreaSpawner is anchored on the snack's own pos with horizontalRadius = 8  -> 17x17 footprint
 *  - home/activation block sits at (x-8, z-8), any Y  (the -x/-z corner of the spawn area)
 *  - floor-finding corrects vertically up to config.maxVerticalCorrectionBlocks (default 64) -> +/-64 reach
 */
public final class Planner {
    private Planner() {}

    public static final int H_RADIUS = 8;     // horizontalRadius -> 17x17 spawn footprint
    public static final int HOME_OFFSET = 8;  // home/base column at (x-8, z-8)
    public static final int V_REACH = 64;     // maxVerticalCorrectionBlocks default -> +/-64 platform reach

    public static int towerHeight = 128;      // snack-column guide height (purely visual)

    public static BlockPos anchor = null;     // the snack-column base you locked
    public static int platformY = 0;          // platform plane (where the spawn floor goes)

    /** Ys of solid blocks currently sitting in the (x-8,z-8) home column (any one powers the stack). */
    public static final List<Integer> homeSolidYs = new ArrayList<>();

    public static void setAnchor(BlockPos p) {
        anchor = p;
        platformY = p.getY() + towerHeight / 2;  // default the platform to mid-tower so +/-64 covers it all
    }

    public static void clear() {
        anchor = null;
        homeSolidYs.clear();
    }

    /** Scan the home column for solid blocks (client-side world read). Call each client tick. */
    public static void scanHome(MinecraftClient mc) {
        homeSolidYs.clear();
        if (anchor == null || mc.world == null) return;
        int hx = anchor.getX() - HOME_OFFSET, hz = anchor.getZ() - HOME_OFFSET;
        int bottom = mc.world.getBottomY();
        int top = Math.min(anchor.getY() + towerHeight, mc.world.getBottomY() + mc.world.getHeight());
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int y = bottom; y <= top; y++) {
            p.set(hx, y, hz);
            BlockState st = mc.world.getBlockState(p);
            if (!st.isAir() && st.isSolidBlock(mc.world, p)) {
                homeSolidYs.add(y);
            }
        }
    }
}
