package com.greenerpastures.client.ui;

/**
 * A minimal, Minecraft-free 2D drawing surface — the seam that lets the SAME paint code run both
 * in-game (over MC's {@code DrawContext}, see {@code McGpCanvas}) and in the standalone Design Studio
 * (over Java2D, see {@code com.greenerpastures.studio.Java2DGpCanvas}). Screens draw against this
 * interface instead of {@code DrawContext}, so what we tweak in the local preview is byte-for-byte the
 * code that renders in Minecraft.
 *
 * <p>Coordinates are GUI pixels. {@code argb} is 0xAARRGGBB. {@link #push}/{@link #translate}/
 * {@link #scale}/{@link #pop} form a transform stack (used for the Daemon's pan + zoom).
 */
public interface GpCanvas {
    void fill(int x1, int y1, int x2, int y2, int argb);
    /** Vertical gradient: {@code top} at y1, {@code bottom} at y2. */
    void gradient(int x1, int y1, int x2, int y2, int top, int bottom);
    /** A smooth stroked polyline through (xs[i],ys[i]) — a real line, not square dots. */
    void stroke(double[] xs, double[] ys, float width, int color);
    void text(String s, int x, int y, int argb);
    int textWidth(String s);
    String trim(String s, int maxWidth);

    void push();
    void pop();
    void translate(double x, double y);
    void scale(double s);
}
