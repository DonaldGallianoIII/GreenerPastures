package com.greenerpastures.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * In-game {@link GpCanvas} backend — forwards to Minecraft's {@code DrawContext} + {@code TextRenderer}.
 * The other backend ({@code com.greenerpastures.studio.Java2DGpCanvas}) draws the same calls with
 * Java2D so the Design Studio previews the identical paint code outside Minecraft.
 */
public class McGpCanvas implements GpCanvas {
    private final DrawContext ctx;
    private final TextRenderer tr;

    public McGpCanvas(DrawContext ctx, TextRenderer tr) {
        this.ctx = ctx;
        this.tr = tr;
    }

    @Override public void fill(int x1, int y1, int x2, int y2, int argb) { ctx.fill(x1, y1, x2, y2, argb); }
    @Override public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) { ctx.fillGradient(x1, y1, x2, y2, top, bottom); }

    /** No native line in DrawContext — plot a continuous thickness-t line per segment (no gaps/cubes). */
    @Override public void stroke(double[] xs, double[] ys, float width, int color) {
        int t = Math.max(1, Math.round(width));
        for (int i = 1; i < xs.length; i++) seg(xs[i - 1], ys[i - 1], xs[i], ys[i], t, color);
    }
    private void seg(double x0, double y0, double x1, double y1, int t, int color) {
        int h = t / 2;
        double dx = x1 - x0, dy = y1 - y0, n = Math.max(Math.abs(dx), Math.abs(dy));
        if (n < 1) { int px = (int) Math.round(x0) - h, py = (int) Math.round(y0) - h; ctx.fill(px, py, px + t, py + t, color); return; }
        double sx = dx / n, sy = dy / n, x = x0, y = y0;
        for (int i = 0; i <= n; i++) {
            int px = (int) Math.round(x) - h, py = (int) Math.round(y) - h;
            ctx.fill(px, py, px + t, py + t, color);
            x += sx; y += sy;
        }
    }
    @Override public void text(String s, int x, int y, int argb) { ctx.drawText(tr, Text.literal(s), x, y, argb, false); }
    @Override public int textWidth(String s) { return tr.getWidth(s); }
    @Override public String trim(String s, int maxWidth) { return tr.trimToWidth(s, maxWidth); }

    @Override public void push() { ctx.getMatrices().push(); }
    @Override public void pop() { ctx.getMatrices().pop(); }
    @Override public void translate(double x, double y) { ctx.getMatrices().translate(x, y, 0); }
    @Override public void scale(double s) { ctx.getMatrices().scale((float) s, (float) s, 1f); }
}
