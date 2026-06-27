package com.greenerpastures.studio;

import com.greenerpastures.client.ui.GpCanvas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Standalone (no-Minecraft) {@link GpCanvas} backend for the Design Studio — renders our GUI paint
 * code with plain Java2D, so it runs as a normal Java program on the desktop. Pixels are ~faithful to
 * the in-game look (same palette, same layout); text metrics differ slightly from MC's font, so this
 * is a fast design preview, not a pixel-exact emulator.
 */
public class Java2DGpCanvas implements GpCanvas {
    private final Graphics2D g;
    private final Deque<AffineTransform> stack = new ArrayDeque<>();

    public Java2DGpCanvas(Graphics2D g) {
        this.g = g;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));   // ~MC font height; swap for a bundled .ttf later
    }

    private static Color col(int argb) {
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }

    @Override public void fill(int x1, int y1, int x2, int y2, int argb) {
        g.setColor(col(argb));
        g.fillRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    @Override public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        g.setPaint(new GradientPaint(0, Math.min(y1, y2), col(top), 0, Math.max(y1, y2), col(bottom)));
        g.fillRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    @Override public void stroke(double[] xs, double[] ys, float width, int color) {
        g.setColor(col(color));
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < xs.length; i++) p.lineTo(xs[i], ys[i]);
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
    }

    @Override public void text(String s, int x, int y, int argb) {
        g.setColor(col(argb));
        g.drawString(s, x, y + 8);   // MC text is top-left at y; Java2D draws from the baseline
    }

    @Override public int textWidth(String s) { return g.getFontMetrics().stringWidth(s); }

    @Override public String trim(String s, int maxWidth) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= maxWidth) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (fm.stringWidth(b.toString() + s.charAt(i)) > maxWidth) break;
            b.append(s.charAt(i));
        }
        return b.toString();
    }

    @Override public void push() { stack.push(g.getTransform()); }
    @Override public void pop() { g.setTransform(stack.pop()); }
    @Override public void translate(double x, double y) { g.translate(x, y); }
    @Override public void scale(double s) { g.scale(s, s); }
}
