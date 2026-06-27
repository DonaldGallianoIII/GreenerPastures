package com.hydrogrid;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Draws, on the ground plane:
 *   - the 9x9 a held water bucket would irrigate (bright cyan fill + outline), with a marker on
 *     the block where the water goes;
 *   - ghost 9x9 outlines at +/-9 in X and Z (the seamless next-bucket spots) so the tiling grid
 *     is obvious; orthogonal neighbours are brighter than diagonals;
 *   - subdued 9x9 outlines over water you've already placed, for auditing a built field.
 */
public final class HydroRenderer {
    private HydroRenderer() {}

    private static final float CR = 0.30f, CG = 0.80f, CB = 0.95f;   // cyan
    private static final float EPS_LO = 0.02f, EPS_HI = 0.05f;       // float just above the ground

    public static void render(WorldRenderContext ctx) {
        if (!Hydro.enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        MatrixStack m = ctx.matrixStack();
        VertexConsumerProvider vp = ctx.consumers();
        if (m == null || vp == null) return;
        Vec3d cam = ctx.camera().getPos();
        VertexConsumer fill = vp.getBuffer(RenderLayer.getDebugFilledBox());
        VertexConsumer line = vp.getBuffer(RenderLayer.getLines());

        // 1) audit: every water source already placed nearby (subdued)
        for (BlockPos w : Hydro.nearbyWater) {
            double gy = w.getY() + 1.0;
            footprint(m, fill, line, cam, w.getX(), gy, w.getZ(), 0.10f, 0.40f);
            marker(m, line, cam, w.getX(), gy, w.getZ(), 0.20f, 0.55f, 1.0f, 0.7f);
        }

        // 2) plan: while holding a water bucket, preview the spot you're aiming at + the next grid
        if (Hydro.holdingBucket(mc.player)) {
            HitResult hit = mc.crosshairTarget;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
                BlockPos b = bhr.getBlockPos();
                double gy = b.getY() + 1.0;
                footprint(m, fill, line, cam, b.getX(), gy, b.getZ(), 0.28f, 0.95f);
                marker(m, line, cam, b.getX(), gy, b.getZ(), 0.25f, 0.60f, 1.0f, 1.0f);
                for (int dx = -Hydro.SPACING; dx <= Hydro.SPACING; dx += Hydro.SPACING) {
                    for (int dz = -Hydro.SPACING; dz <= Hydro.SPACING; dz += Hydro.SPACING) {
                        if (dx == 0 && dz == 0) continue;
                        boolean ortho = (dx == 0 || dz == 0);
                        outline(m, line, cam, b.getX() + dx, gy, b.getZ() + dz, ortho ? 0.55f : 0.28f);
                        marker(m, line, cam, b.getX() + dx, gy, b.getZ() + dz, 0.55f, 0.95f, 1.0f, ortho ? 0.85f : 0.45f);
                    }
                }
            }
        }

        // 3) field fill: the computed dig-and-place spots for a whole rectangular field
        if (Field.isSet()) renderField(m, line, cam);
    }

    private static void renderField(MatrixStack m, VertexConsumer line, Vec3d cam) {
        double by = Field.planeY;
        // field boundary (white)
        WorldRenderer.drawBox(m, line,
                Field.minX - cam.x, by + EPS_LO - cam.y, Field.minZ - cam.z,
                Field.maxX + 1 - cam.x, by + EPS_HI - cam.y, Field.maxZ + 1 - cam.z,
                0.95f, 0.95f, 1.0f, 0.65f);
        for (BlockPos s : Field.spots) {
            if (Field.placed.contains(s)) {
                // green nub = water already here
                WorldRenderer.drawBox(m, line,
                        s.getX() - cam.x, by - cam.y, s.getZ() - cam.z,
                        s.getX() + 1 - cam.x, by + 1 - cam.y, s.getZ() + 1 - cam.z,
                        0.25f, 0.95f, 0.35f, 0.85f);
            } else {
                // amber pillar = go dig + place a bucket here
                WorldRenderer.drawBox(m, line,
                        s.getX() - cam.x, by - cam.y, s.getZ() - cam.z,
                        s.getX() + 1 - cam.x, by + 3 - cam.y, s.getZ() + 1 - cam.z,
                        1.0f, 0.75f, 0.15f, 0.95f);
            }
        }
    }

    /** Flat 9x9 sheet (translucent fill + crisp outline) centred on column (cx,cz). */
    private static void footprint(MatrixStack m, VertexConsumer fill, VertexConsumer line, Vec3d cam,
                                  int cx, double y, int cz, float fa, float la) {
        double x1 = cx - Hydro.REACH, x2 = cx + Hydro.REACH + 1;
        double z1 = cz - Hydro.REACH, z2 = cz + Hydro.REACH + 1;
        WorldRenderer.renderFilledBox(m, fill,
                x1 - cam.x, y + EPS_LO - cam.y, z1 - cam.z,
                x2 - cam.x, y + EPS_HI - cam.y, z2 - cam.z, CR, CG, CB, fa);
        WorldRenderer.drawBox(m, line,
                x1 - cam.x, y + EPS_LO - cam.y, z1 - cam.z,
                x2 - cam.x, y + EPS_HI - cam.y, z2 - cam.z, CR, CG, CB, la);
    }

    /** Outline-only 9x9 (for ghost next-bucket spots). */
    private static void outline(MatrixStack m, VertexConsumer line, Vec3d cam,
                                int cx, double y, int cz, float la) {
        double x1 = cx - Hydro.REACH, x2 = cx + Hydro.REACH + 1;
        double z1 = cz - Hydro.REACH, z2 = cz + Hydro.REACH + 1;
        WorldRenderer.drawBox(m, line,
                x1 - cam.x, y + EPS_LO - cam.y, z1 - cam.z,
                x2 - cam.x, y + EPS_HI - cam.y, z2 - cam.z, CR, CG, CB, la);
    }

    /** 1x1 nub marking the water column (where the bucket goes). */
    private static void marker(MatrixStack m, VertexConsumer line, Vec3d cam,
                               int cx, double y, int cz, float r, float g, float b, float a) {
        WorldRenderer.drawBox(m, line,
                cx - cam.x, y + EPS_LO - cam.y, cz - cam.z,
                cx + 1 - cam.x, y + 0.10 - cam.y, cz + 1 - cam.z, r, g, b, a);
    }
}
