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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.world.ClientWorld;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Toggle overlay (key J): boxes pastures that have only ONE tethered mon (the "I pulled a mon and
 * forgot which pasture" case), grouped by the real pasture position read off the pastured entities.
 *
 * <p>Limitation: this is client-side and reads the tether→pasture link off entities. Cobblemon syncs
 * that link server-only, so on a server this typically sees 0 — a proper version needs a server-side
 * scan. The toggle prints a readable status to chat ({@link #chatReport}) instead of a long HUD line.
 */
public final class PastureCheck {
    private PastureCheck() {}

    public static volatile boolean enabled = false;
    private static volatile int underFilled = 0;

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
        }
    }

    public static void toggle() { enabled = !enabled; }

    /** Count client-visible pastured mons per pasture position. */
    private static Map<BlockPos, Integer> scanCounts(ClientWorld world) {
        Map<BlockPos, Integer> counts = new HashMap<>();
        for (Entity e : world.getEntities()) {
            BlockPos pos = pasturePosOf(e);
            if (pos != null) counts.merge(pos, 1, Integer::sum);
        }
        return counts;
    }

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

        int under = 0;
        for (Map.Entry<BlockPos, Integer> en : scanCounts(mc.world).entrySet()) {
            if (en.getValue() >= 2) continue;             // full enough — skip to avoid clutter
            under++;
            BlockPos b = en.getKey();
            WorldRenderer.drawBox(m, lines,
                    b.getX() - cam.x, b.getY() - cam.y, b.getZ() - cam.z,
                    b.getX() + 1 - cam.x, b.getY() + 2 - cam.y, b.getZ() + 1 - cam.z,
                    1.0f, 0.85f, 0.2f, 1f);            // yellow = under-staffed
        }
        underFilled = under;
    }

    /** Short on-screen indicator (just the count); the full detail goes to chat on toggle. */
    public static void renderHud(DrawContext ctx, RenderTickCounter t) {
        if (!enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        ctx.drawText(mc.textRenderer, "Pasture check: " + underFilled + " under-staffed",
                4, mc.getWindow().getScaledHeight() - 22, 0xFFFFD94D, true);
    }

    /** Readable status to chat — called on toggle so it never overflows the HUD. */
    public static void chatReport(MinecraftClient mc) {
        if (mc.player == null) return;
        if (!enabled) {
            mc.player.sendMessage(Text.literal("[EggOracle] Pasture check OFF."), false);
            return;
        }
        init();
        if (!ok || mc.world == null) {
            mc.player.sendMessage(Text.literal("[EggOracle] Pasture check ON — Cobblemon classes not found."), false);
            return;
        }
        Map<BlockPos, Integer> counts = scanCounts(mc.world);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        long under = counts.values().stream().filter(v -> v < 2).count();
        if (total == 0) {
            mc.player.sendMessage(Text.literal(
                    "[EggOracle] Pasture check ON — 0 tethered mons visible client-side. "
                    + "Cobblemon syncs the pasture link server-only, so this view can't see them "
                    + "(a server-side check is the fix)."), false);
        } else {
            mc.player.sendMessage(Text.literal(
                    "[EggOracle] Pasture check ON — " + counts.size() + " pasture(s), "
                    + under + " under-staffed (boxed yellow), " + total + " mons read."), false);
        }
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
