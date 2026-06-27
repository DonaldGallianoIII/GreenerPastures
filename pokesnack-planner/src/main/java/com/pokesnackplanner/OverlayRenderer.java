package com.pokesnackplanner;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Draws the snack column, the (-8,-8) home/mine column, the 17x17 platform footprint, and the +/-64 coverage band. */
public final class OverlayRenderer {
    private OverlayRenderer() {}

    private static final float[] SNACK = {0.36f, 0.82f, 0.92f, 1.0f}; // cyan  - stack snacks here
    private static final float[] HOME  = {1.00f, 0.62f, 0.20f, 1.0f}; // orange - mine to bedrock + home block
    private static final float[] PLAT  = {0.33f, 0.88f, 0.50f, 1.0f}; // green - platform footprint
    private static final float[] BAND  = {0.85f, 0.85f, 0.95f, 0.5f}; // faint - vertical coverage
    private static final float[] HOME_ON = {0.20f, 1.00f, 0.35f, 1.0f}; // bright green - solid home block in place

    public static void render(WorldRenderContext ctx) {
        BlockPos a = Planner.anchor;
        if (a == null) return;

        MatrixStack m = ctx.matrixStack();
        VertexConsumerProvider vp = ctx.consumers();
        if (m == null || vp == null) return;

        Vec3d cam = ctx.camera().getPos();
        VertexConsumer lines = vp.getBuffer(RenderLayer.getLines());

        MinecraftClient mc = MinecraftClient.getInstance();
        int wb = (mc.world != null) ? mc.world.getBottomY() : -64;
        int wTop = (mc.world != null) ? mc.world.getBottomY() + mc.world.getHeight() : 320;

        int ax = a.getX(), ay = a.getY(), az = a.getZ();
        int hx = ax - Planner.HOME_OFFSET, hz = az - Planner.HOME_OFFSET;
        int r = Planner.H_RADIUS;
        int py = Planner.platformY;
        int top = Math.min(ay + Planner.towerHeight, wTop);

        // snack column (cyan): where you stack the poke_snacks
        box(m, lines, cam, ax, ay, az, ax + 1, top, az + 1, SNACK);
        // home/base column (orange): mine to bedrock, place one solid home block anywhere in it
        box(m, lines, cam, hx, wb, hz, hx + 1, ay + 1, hz + 1, HOME);
        // any solid block currently in that column = the live home block (bright green)
        for (int hy : Planner.homeSolidYs) {
            box(m, lines, cam, hx, hy, hz, hx + 1, hy + 1, hz + 1, HOME_ON);
        }
        // platform footprint (green, flat 17x17): where the spawn floor goes
        box(m, lines, cam, ax - r, py, az - r, ax + r + 1, py, az + r + 1, PLAT);
        // vertical coverage +/-64 from the platform (faint): the snacks one platform feeds
        box(m, lines, cam, ax - r, clampY(py - Planner.V_REACH, wb, wTop), az - r,
                ax + r + 1, clampY(py + Planner.V_REACH, wb, wTop), az + r + 1, BAND);
    }

    private static int clampY(int y, int lo, int hi) {
        return Math.max(lo, Math.min(hi, y));
    }

    private static void box(MatrixStack m, VertexConsumer vc, Vec3d cam,
                            double x1, double y1, double z1, double x2, double y2, double z2, float[] col) {
        WorldRenderer.drawBox(m, vc, x1 - cam.x, y1 - cam.y, z1 - cam.z, x2 - cam.x, y2 - cam.y, z2 - cam.z,
                col[0], col[1], col[2], col[3]);
    }
}
