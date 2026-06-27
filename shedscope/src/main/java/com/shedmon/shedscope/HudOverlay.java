package com.shedmon.shedscope;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;

/** Top-left readout: nearest of each kind, with live distance. */
public final class HudOverlay {
    private HudOverlay() {}

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || !State.enabled) return;

        List<Scanner.Hit> hits = Scanner.hits();
        LinkedHashMap<String, Scanner.Hit> nearest = new LinkedHashMap<>();
        for (Scanner.Hit h : hits) nearest.putIfAbsent(h.target().name, h);

        int x = 4;
        int y = 4;
        ctx.drawText(mc.textRenderer, Text.literal("ShedScope"), x, y, 0xFFFFFFFF, true);
        y += 12;

        if (nearest.isEmpty()) {
            ctx.drawText(mc.textRenderer, Text.literal("· nothing in range"), x, y, 0xFFAAAAAA, true);
            return;
        }

        int shown = 0;
        for (Scanner.Hit h : nearest.values()) {
            if (shown++ >= 12) break;
            int dist = (int) Math.round(Math.sqrt(h.distSq()));
            int color = 0xFF000000 | (h.target().color & 0xFFFFFF);
            ctx.drawText(mc.textRenderer, Text.literal("● " + h.target().name + "  " + dist + "m"), x, y, color, true);
            y += 11;
        }
    }
}
