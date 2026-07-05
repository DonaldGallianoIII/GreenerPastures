package com.greenerpastures.egg.oracle.farm;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * In-world finder: boxes every Cobblemon Pokémon whose display name matches the target
 * species (set from the Farm dashboard), so you can walk straight to the right pasture
 * instead of hand-checking dozens. Matches the entity's shown name - works for un-nicknamed
 * breeding stock; nicknamed mons match on the nickname.
 */
public final class PokemonFinder {
    private PokemonFinder() {}

    // magenta - stands out against everything
    private static final float MR = 1.0f, MG = 0.35f, MB = 0.9f;
    private static final int HUD_COLOR = 0xFFFF5BD1;

    public static void renderWorld(WorldRenderContext ctx) {
        if (!Finder.active()) { Finder.lastCount = 0; Finder.nearestDist = -1; return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        MatrixStack m = ctx.matrixStack();
        VertexConsumerProvider vp = ctx.consumers();
        if (m == null || vp == null) return;

        Vec3d cam = ctx.camera().getPos();
        Vec3d pp = mc.player.getPos();
        VertexConsumer lines = vp.getBuffer(RenderLayer.getLines());
        String target = Finder.target.toLowerCase();

        int count = 0;
        double nearest = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!matches(e, target)) continue;
            count++;
            double d = e.getPos().distanceTo(pp);
            if (d < nearest) nearest = d;
            Box b = e.getBoundingBox();
            WorldRenderer.drawBox(m, lines,
                    b.minX - cam.x, b.minY - cam.y, b.minZ - cam.z,
                    b.maxX - cam.x, b.maxY - cam.y, b.maxZ - cam.z, MR, MG, MB, 1f);
        }
        Finder.lastCount = count;
        Finder.nearestDist = count > 0 ? nearest : -1;
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        if (!Finder.active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        String s = "Finding " + Finder.target + ":  " + Finder.lastCount + " in view"
                + (Finder.nearestDist >= 0 ? "  ·  nearest " + Math.round(Finder.nearestDist) + "m"
                                           : "  ·  none loaded (get closer)");
        ctx.drawText(mc.textRenderer, s, 4, mc.getWindow().getScaledHeight() - 12, HUD_COLOR, true);
    }

    private static boolean matches(Entity e, String targetLower) {
        Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
        if (id == null || !id.getNamespace().equals("cobblemon") || !id.getPath().contains("pokemon")) return false;
        String name = e.getName().getString();
        return name != null && name.toLowerCase().contains(targetLower);
    }
}
