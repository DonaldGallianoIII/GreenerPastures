package com.hydrogrid;

import net.minecraft.client.MinecraftClient;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * "Field Fill": from two opposite corners of a flat field, compute the minimum set of water-source
 * positions that hydrate every block. Each source waters a 9x9, so per axis we need ceil(len/9)
 * sources, spread evenly so they all land INSIDE the field (no water spilling past the edge, no dry
 * strip). As water gets placed, spots near you are detected and checked off — and stay off.
 */
public final class Field {
    private Field() {}

    public static final int MAX_SPOTS = 512;   // render/perf guard for huge fields

    public static BlockPos cornerA, cornerB;   // raw corners (null until both set)
    public static int planeY;                  // Y where the water goes
    public static int minX, minZ, maxX, maxZ;  // resolved bounds
    public static int sizeX, sizeZ;            // field dimensions
    public static boolean capped = false;

    public static final List<BlockPos> spots = new ArrayList<>();   // computed water positions
    public static final Set<BlockPos> placed = new HashSet<>();     // spots detected as filled

    private static int tickCounter = 0;

    public static boolean isSet() { return cornerA != null && cornerB != null; }
    public static int needed() { return spots.size(); }
    public static int done() { return placed.size(); }
    public static int remaining() { return Math.max(0, spots.size() - placed.size()); }

    /** Stamp one corner; alternates A then B. A third stamp restarts at a fresh A. */
    public static void stamp(BlockPos p) {
        if (cornerA == null || cornerB != null) {       // start fresh
            cornerA = p; cornerB = null; spots.clear(); placed.clear();
        } else {
            cornerB = p; compute();
        }
    }

    public static void setCorners(BlockPos a, BlockPos b) {
        cornerA = a; cornerB = b; compute();
    }

    public static void clear() {
        cornerA = cornerB = null; spots.clear(); placed.clear(); capped = false;
    }

    private static void compute() {
        spots.clear(); placed.clear(); capped = false;
        planeY = cornerA.getY();
        minX = Math.min(cornerA.getX(), cornerB.getX());
        maxX = Math.max(cornerA.getX(), cornerB.getX());
        minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        maxZ = Math.max(cornerA.getZ(), cornerB.getZ());
        sizeX = maxX - minX + 1;
        sizeZ = maxZ - minZ + 1;
        List<Integer> xs = axis(minX, maxX);
        List<Integer> zs = axis(minZ, maxZ);
        outer:
        for (int x : xs) {
            for (int z : zs) {
                spots.add(new BlockPos(x, planeY, z));
                if (spots.size() >= MAX_SPOTS) { capped = true; break outer; }
            }
        }
    }

    /** Minimum, evenly-spread cover positions for [min,max]; each covers +/-REACH; all inside. */
    static List<Integer> axis(int min, int max) {
        List<Integer> out = new ArrayList<>();
        int len = max - min + 1;
        int span = 2 * Hydro.REACH + 1;                       // 9
        int n = Math.max(1, (int) Math.ceil(len / (double) span));
        if (n == 1) { out.add((min + max) / 2); return out; } // tiny field: one source, centred
        int lo = min + Hydro.REACH, hi = max - Hydro.REACH;   // first covers min, last covers max
        for (int i = 0; i < n; i++) {
            out.add(lo + (int) Math.round((double) (hi - lo) * i / (n - 1)));
        }
        return out;
    }

    /** Check off spots that now have water. Throttled; call each client tick. */
    public static void tick(MinecraftClient mc) {
        if (!isSet() || mc.world == null) return;
        if (++tickCounter < 10) return;
        tickCounter = 0;
        for (BlockPos s : spots) {
            if (placed.contains(s)) continue;
            FluidState fs = mc.world.getFluidState(s);
            if (fs.isStill() && fs.isIn(FluidTags.WATER)) placed.add(s);
        }
    }
}
