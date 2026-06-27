package com.shedmon.shedscope;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/** Draws ESP boxes for every hit plus a tracer line to the nearest one. */
public final class EspRenderer {
    private EspRenderer() {}

    public static void render(WorldRenderContext ctx) {
        if (!State.enabled) return;
        List<Scanner.Hit> hits = Scanner.hits();
        if (hits.isEmpty()) return;

        MatrixStack matrices = ctx.matrixStack();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        Camera cam = ctx.camera();
        Vec3d c = cam.getPos();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        for (Scanner.Hit h : hits) {
            Target t = h.target();
            double x = h.pos().getX() - c.x;
            double y = h.pos().getY() - c.y;
            double z = h.pos().getZ() - c.z;
            WorldRenderer.drawBox(matrices, lines, x, y, z, x + 1, y + 1, z + 1, t.r(), t.g(), t.b(), 1f);
        }

        // Tracer from just below the crosshair to the nearest hit.
        Scanner.Hit n = hits.get(0);
        Target nt = n.target();
        Vec3d look = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d startW = c.add(look.multiply(0.8));
        float sx = (float) (startW.x - c.x);
        float sy = (float) (startW.y - c.y - 0.1);
        float sz = (float) (startW.z - c.z);
        float ex = (float) (n.pos().getX() + 0.5 - c.x);
        float ey = (float) (n.pos().getY() + 0.5 - c.y);
        float ez = (float) (n.pos().getZ() + 0.5 - c.z);

        Matrix4f m = matrices.peek().getPositionMatrix();
        Vector3f dir = new Vector3f(ex - sx, ey - sy, ez - sz);
        if (dir.lengthSquared() > 1e-6f) dir.normalize();
        lines.vertex(m, sx, sy, sz).color(nt.r(), nt.g(), nt.b(), 1f).normal(dir.x, dir.y, dir.z);
        lines.vertex(m, ex, ey, ez).color(nt.r(), nt.g(), nt.b(), 1f).normal(dir.x, dir.y, dir.z);
    }
}
