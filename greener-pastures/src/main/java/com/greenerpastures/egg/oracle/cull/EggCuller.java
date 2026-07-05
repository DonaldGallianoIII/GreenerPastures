package com.greenerpastures.egg.oracle.cull;

import com.greenerpastures.mixin.client.HandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws keep/cull tier overlays on Cobbreeding eggs in any open container.
 *
 * Mirrors the shiny-egg-highlighter approach (confirmed in-game, incl. Sophisticated Storage):
 * registered on {@code ScreenEvents.afterRender} so it paints last, over the whole GUI.
 * Per-ItemStack results are cached so big banks read each egg ~once, not every frame.
 */
public final class EggCuller {
    private EggCuller() {}

    // tier colors (ARGB): translucent fill + opaque border
    private static final int GOLD_F = 0x4DFFD700, GOLD_B = 0xFFFFD700;   // shiny
    private static final int BLUE_F = 0x555BB8FF, BLUE_B = 0xFF5BB8FF;   // keeper (light blue)
    private static final int RED_F  = 0x55E0544D, RED_B  = 0xFFE0544D;   // cull
    private static final int UNKNOWN_B = 0x80FFFFFF;                     // egg, IVs unread

    private static final int SUMMARY_COLOR = 0xFFE7E9F0;

    // Per-ItemStack identity cache (stacks have no value equals, so identity is intended). Synchronized
    // access-order LRU - gradual eviction past CACHE_MAX, no full-clear recompute storm (bug-hunt #7).
    private static final int CACHE_MAX = 4096;
    private static final Map<ItemStack, EggInfo> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<ItemStack, EggInfo>(256, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<ItemStack, EggInfo> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    private static EggInfo infoFor(ItemStack stack) {
        EggInfo cached = CACHE.get(stack);
        if (cached != null) return cached;
        EggInfo info = EggReader.read(stack);
        if (info == null) return null;             // not an egg (cheap id check; don't cache)
        CACHE.put(stack, info);                     // LRU evicts the eldest past CACHE_MAX
        return info;
    }

    public static void drawOverlays(HandledScreen<?> screen, DrawContext ctx, CullSettings cfg) {
        if (!cfg.enabled) return;
        int ox = ((HandledScreenAccessor) screen).eggoracle$getX();
        int oy = ((HandledScreenAccessor) screen).eggoracle$getY();
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        int total = 0, shiny = 0, keep = 0, cull = 0;
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot == null || !slot.isEnabled()) continue;
            EggInfo info = infoFor(slot.getStack());
            if (info == null) continue;
            Tier tier = cfg.classify(info);
            int sx = ox + slot.x, sy = oy + slot.y;
            total++;
            switch (tier) {
                case SHINY  -> { box(ctx, sx, sy, GOLD_F, GOLD_B);  shiny++; }
                case KEEPER -> { box(ctx, sx, sy, BLUE_F, BLUE_B); keep++; }
                case CULL   -> { box(ctx, sx, sy, RED_F, RED_B);    cull++; }
                case UNKNOWN -> ctx.drawBorder(sx, sy, 16, 16, UNKNOWN_B);
                default -> {}
            }
            if (cfg.showNumbers && info.ivsKnown()) {
                drawIvTotal(ctx, tr, sx, sy, info.ivTotal(), numberColor(tier));
            }
        }

        if (cfg.showSummary && total > 0) {
            String s = "Eggs " + total + "   ★" + shiny + "   keep " + keep + "   cull " + cull;
            ctx.drawText(tr, s, ox, oy - 10, SUMMARY_COLOR, true);
        }
    }

    private static void box(DrawContext ctx, int x, int y, int fill, int border) {
        ctx.fill(x, y, x + 16, y + 16, fill);
        ctx.drawBorder(x, y, 16, 16, border);
    }

    /** Small IV total in the slot's top-left (where eggs never show a stack count). */
    private static void drawIvTotal(DrawContext ctx, TextRenderer tr, int sx, int sy, int total, int color) {
        float scale = 0.7f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sx + 1f, sy + 1f, 250f);   // z above items + fills
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawText(tr, Integer.toString(total), 0, 0, color, true);
        ctx.getMatrices().pop();
    }

    private static int numberColor(Tier tier) {
        return switch (tier) {
            case SHINY  -> 0xFFFFE08A;
            case KEEPER -> 0xFFA9D8FF;
            case CULL   -> 0xFFFF9A9A;
            default     -> 0xFFCCCCCC;
        };
    }
}
