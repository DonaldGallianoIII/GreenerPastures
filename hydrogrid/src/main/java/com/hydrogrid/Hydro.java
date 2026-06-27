package com.hydrogrid;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared state + the vanilla farmland-hydration constants.
 *
 * Vanilla {@code FarmlandBlock.isWaterNearby} checks a flat box from (pos-4, 0, pos-4) to
 * (pos+4, +1, pos+4) for any water -> a water source hydrates farmland within 4 blocks in every
 * horizontal direction (a full 9x9 SQUARE, corners included), at its own level or one below.
 *
 * Seamless tiling: two sources placed exactly {@code SPACING}=9 apart cover [w-4..w+4] and
 * [w+5..w+13] -> contiguous, zero gap, zero overlap. A 9-block grid is the minimum-water way to
 * irrigate an unbounded field: 1 source per 81 blocks.
 */
public final class Hydro {
    private Hydro() {}

    public static final int REACH = 4;           // hydration radius (vanilla)
    public static final int SPACING = 9;         // seamless tile pitch: zero gap, zero overlap

    // existing-water audit scan (cheap, cached; refreshed a couple times a second)
    public static final int SCAN_RADIUS = 14;    // horizontal blocks around the player
    public static final int SCAN_VREACH = 4;     // vertical band around the player's feet
    public static final int MAX_WATER = 48;       // cap so an ocean can't spam the overlay

    public static boolean enabled = true;

    /** Cached nearby water-source positions (for auditing a placed field). */
    public static final List<BlockPos> nearbyWater = new ArrayList<>();
    /** True when the scan hit the cap (likely a large body of water nearby). */
    public static boolean capped = false;

    private static int tickCounter = 0;

    public static void toggle() { enabled = !enabled; }

    public static boolean holdingBucket(ClientPlayerEntity p) {
        return p != null && (p.getMainHandStack().isOf(Items.WATER_BUCKET)
                || p.getOffHandStack().isOf(Items.WATER_BUCKET));
    }

    /** Refresh the nearby water cache every ~10 ticks. Call each client tick. */
    public static void tick(MinecraftClient mc) {
        if (!enabled || mc.world == null || mc.player == null) {
            nearbyWater.clear();
            return;
        }
        if (++tickCounter < 10) return;
        tickCounter = 0;
        scan(mc);
    }

    private static void scan(MinecraftClient mc) {
        nearbyWater.clear();
        capped = false;
        BlockPos c = mc.player.getBlockPos();
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dy = -SCAN_VREACH; dy <= SCAN_VREACH; dy++) {
            for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    FluidState fs = mc.world.getFluidState(p);
                    if (fs.isStill() && fs.isIn(FluidTags.WATER)) {
                        nearbyWater.add(p.toImmutable());
                        if (nearbyWater.size() >= MAX_WATER) { capped = true; return; }
                    }
                }
            }
        }
    }
}
