package com.eggoracle.farm;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Toggle overlay (key J): counts each pasture's tethered mons by reading the pasture link
 * (getTethering().getPasturePos()) off the pastured entities, then boxes the pastures that
 * have only ONE mon — the "I pulled a mon and forgot which pasture" case. Tower-exact: it
 * groups by the real pasture position, not proximity, so stacked pastures don't blur.
 *
 * Caveat: it can only see what's on the client. If Cobblemon doesn't sync the tethering link
 * to clients, this finds nothing -> fall back to a proximity counter. Fully-empty (0-mon)
 * pastures have no entities so they aren't shown here.
 */
public final class PastureCheck {
    private PastureCheck() {}

    public static volatile boolean enabled = false;
    private static volatile int underFilled = 0;
    private static volatile String note = "";

    private static boolean initDone, ok;
    private static Method getTethering, getPasturePos;

    private static synchronized void init() {
        if (initDone) return;
        initDone = true;
        try {
            Class<?> pe = Class.forName("com.cobblemon.mod.common.entity.pokemon.PokemonEntity");
            getTethering = pe.getMethod("getTethering");
            Class<?> teth = Class.forName("com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity$Tethering");
            getPasturePos = teth.getMethod("getPasturePos");
            ok = true;
        } catch (Throwable t) {
            ok = false;
            note = "Cobblemon classes not found";
        }
    }

    public static void toggle() { enabled = !enabled; }

    public static void renderWorld(WorldRenderContext ctx) {
        if (!enabled) { underFilled = 0; return; }
        init();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!ok || mc.world == null) return;
        MatrixStack m = ctx.matrixStack();
        VertexConsumerProvider vp = ctx.consumers();
        if (m == null || vp == null) return;
        Vec3d cam = ctx.camera().getPos();
        VertexConsumer lines = vp.getBuffer(RenderLayer.getLines());

        Map<BlockPos, Integer> counts = new HashMap<>();
        int tethered = 0;
        for (Entity e : mc.world.getEntities()) {
            BlockPos pos = pasturePosOf(e);
            if (pos != null) { counts.merge(pos, 1, Integer::sum); tethered++; }
        }

        int under = 0;
        for (Map.Entry<BlockPos, Integer> en : counts.entrySet()) {
            if (en.getValue() >= 2) continue;             // full enough — skip to avoid clutter
            under++;
            BlockPos b = en.getKey();
            WorldRenderer.drawBox(m, lines,
                    b.getX() - cam.x, b.getY() - cam.y, b.getZ() - cam.z,
                    b.getX() + 1 - cam.x, b.getY() + 2 - cam.y, b.getZ() + 1 - cam.z,
                    1.0f, 0.85f, 0.2f, 1f);            // yellow = under-staffed
        }
        underFilled = under;
        note = tethered == 0 ? "no tethered mons read on client (link may be server-only)" : "";
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter t) {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        String s = "Pasture check:  " + underFilled + " under-staffed (1 mon, yellow)"
                + (note.isEmpty() ? "  ·  empties not shown" : "  ·  " + note);
        ctx.drawText(mc.textRenderer, s, 4, mc.getWindow().getScaledHeight() - 22, 0xFFFFD94D, true);
    }

    private static BlockPos pasturePosOf(Entity e) {
        Identifier id = Registries.ENTITY_TYPE.getId(e.getType());
        if (id == null || !id.getNamespace().equals("cobblemon") || !id.getPath().contains("pokemon")) return null;
        try {
            Object teth = getTethering.invoke(e);
            if (teth == null) return null;
            Object pos = getPasturePos.invoke(teth);
            return (pos instanceof BlockPos bp) ? bp : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
