package com.hydrogrid;

import com.hydrogrid.farm.Farm;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Bottom-left status line. Shows only when there's something to say (holding a bucket, or water
 * mapped nearby) so there's no idle nag when you're just walking around.
 */
public final class HydroHud {
    private HydroHud() {}

    private static final int CYAN = 0xFF4FD0F0;

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        int h = mc.getWindow().getScaledHeight();
        int x = 4;

        // Auto-Farm status — shown whenever the farm is running, even if the water overlay is off.
        if (Farm.active) {
            StringBuilder sb = new StringBuilder("Auto-Farm ").append(Farm.mode.label());
            sb.append("  ·  ").append(Farm.harvested).append(" reaped");
            if (Farm.picked > 0) sb.append("  ·  ").append(Farm.picked).append(" berries");
            sb.append("  ·  ").append(Farm.planted).append(" planted");
            if (Farm.tilled > 0) sb.append("  ·  ").append(Farm.tilled).append(" tilled");
            sb.append(Field.isSet() ? "  ·  field-scoped" : "  ·  in reach");
            if (Farm.lastWarn != null) sb.append("  ·  ").append(Farm.lastWarn);
            ctx.drawText(tr, Text.literal(sb.toString()), x, h - 36, 0xFF8CE600, true);
        }

        if (!Hydro.enabled) return;
        int y = h - 12;

        if (Field.isSet()) {
            String f = "Field " + Field.sizeX + "x" + Field.sizeZ + "  ·  " + Field.needed() + " needed  ·  "
                    + Field.done() + " placed  ·  " + Field.remaining() + " to go" + (Field.capped ? "  (capped)" : "");
            ctx.drawText(tr, Text.literal(f), x, h - 24, 0xFFFFC83C, true);
        }

        if (Hydro.holdingBucket(mc.player)) {
            ctx.drawText(tr,
                    Text.literal("Water 9x9  ·  80 plantable  ·  next bucket = 9 blocks over (seamless, zero waste)"),
                    x, y, CYAN, true);
        } else if (!Hydro.nearbyWater.isEmpty()) {
            String s = "HydroGrid: " + Hydro.nearbyWater.size() + (Hydro.capped ? "+" : "")
                    + " water mapped  ·  hold a bucket to plan  ·  H hides";
            ctx.drawText(tr, Text.literal(s), x, y, CYAN, true);
        }
        // otherwise: draw nothing
    }
}
