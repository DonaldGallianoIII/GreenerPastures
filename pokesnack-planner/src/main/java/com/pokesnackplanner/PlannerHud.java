package com.pokesnackplanner;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Top-left legend: the anchor, the mine-to-bedrock base, the platform plane, and its coverage. */
public final class PlannerHud {
    private PlannerHud() {}

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        BlockPos a = Planner.anchor;
        if (a == null) return;   // fully hidden when idle; clear the anchor to turn the whole overlay off
        TextRenderer tr = mc.textRenderer;

        int x = 4, y = 4;
        ctx.drawText(tr, Text.literal("PokeSnack Planner"), x, y, 0xFFFFFFFF, true);
        y += 12;
        int ax = a.getX(), az = a.getZ();
        line(ctx, tr, x, y, "snack column    X=" + ax + " Z=" + az, 0xFF5BD1E6); y += 11;
        line(ctx, tr, x, y, "mine to bedrock  X=" + (ax - Planner.HOME_OFFSET) + " Z=" + (az - Planner.HOME_OFFSET) + "  (1 home block, any Y)", 0xFFFF9E33); y += 11;
        if (Planner.homeSolidYs.isEmpty()) {
            line(ctx, tr, x, y, "home block: NONE  - place a solid block in the orange column", 0xFFFF5555); y += 11;
        } else {
            line(ctx, tr, x, y, "home block: ON  (" + Planner.homeSolidYs.size() + " in column, lowest Y=" + Planner.homeSolidYs.get(0) + ")", 0xFF33FF55); y += 11;
        }
        line(ctx, tr, x, y, "platform 17x17   Y=" + Planner.platformY + "   [L = set to look]", 0xFF54E0A0); y += 11;
        line(ctx, tr, x, y, "covers Y " + (Planner.platformY - Planner.V_REACH) + " .. " + (Planner.platformY + Planner.V_REACH) + "  (+/-64)", 0xFFCCCCEE); y += 11;
        line(ctx, tr, x, y, "P = move / clear anchor", 0xFF888888);
    }

    private static void line(DrawContext ctx, TextRenderer tr, int x, int y, String s, int color) {
        ctx.drawText(tr, Text.literal(s), x, y, color, true);
    }
}
