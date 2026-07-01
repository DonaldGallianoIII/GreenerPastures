package com.greenerpastures.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The Daemon's <b>look</b>, Minecraft-free. {@link #paintCanvas} draws the node graph (units +
 * augment/filter/collection/void nodes + wires) onto a {@link GpCanvas}; {@link #paint} wraps it with
 * the standalone Daemon header/footer. The in-game screen and the Design Studio both paint through it
 * (McGpCanvas / Java2D) — <b>same code, same pixels</b>. Palette is the single source of truth.
 */
public final class DaemonView {
    private DaemonView() {}

    // palette ---------------------------------------------------------------------------------------
    public static final int BG = 0xFF0C0F14;
    public static final int GRID_MINOR = 0xFF10141B, GRID_MAJOR = 0xFF1A2230;
    public static final int BAR_TOP = 0xFF141A23, BAR_BOT = 0xFF0C1016;
    public static final int PANEL = 0xFF161C25, PANEL_HI = 0xFF1F2733, BODY = 0xFF12171F, CELL = 0xFF0E131A;
    public static final int BORDER = 0xFF2A3543, BORDER_PAIR = 0xFF2E5A47;
    public static final int TEXT = 0xFFE7EEF6, MUTED = 0xFF8593A4, DIM = 0xFF566273;
    public static final int PAIR = 0xFFD56BFF, PASS = 0xFF4FD6A0, FLOW = 0xFFFFB454, VOIDC = 0xFFFF6B6B, DATA = 0xFF5CC8FF;
    public static final int FLASH = 0xFFCFA0A0, HINTY = 0xFFCFC08A;
    public static final int SPRITE_TEXT = 0xFF0C0F14, WIRE_TEMP = 0x99D56BFF;
    public static final int SHADOW = 0x55000000, HILITE = 0x16FFFFFF, DIVIDER = 0xFF222C38;
    public static final int[] SPRITES = {0xFFC98FD6, 0xFFFF8A5C, 0xFF4FB0D6, 0xFF7AD19A, 0xFFE6C84F, 0xFFE07A9A};
    public static final String[] STATS = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};

    public static final int NODE_W = 120, NODE_H = 62;     // unit size (kept for the controller's geometry)
    private static final int HEADER_H = 28, FOOTER_H = 22;

    public enum Kind { UNIT, AUGMENT, FILTER, COLLECTION, VOID }

    // model -----------------------------------------------------------------------------------------
    public static final class Node {
        public Kind kind = Kind.UNIT;
        public double x, y;
        public String label, species;       // unit
        public boolean paired; public int bucket;
        public String title, glyph;         // augment / generic header
        public String[] body;               // augment props / lines
        public int count;                   // collection / void
        public int[][] ivs;                 // filter: 6 rows {min,max}
        public String nature, shiny;        // filter

        /** UNIT (existing signature — the controller uses this). */
        public Node(double x, double y, String label, String species, boolean paired, int bucket) {
            this.x = x; this.y = y; this.label = label; this.species = species; this.paired = paired; this.bucket = bucket;
        }
        private Node(Kind kind, double x, double y) { this.kind = kind; this.x = x; this.y = y; }

        public static Node augment(double x, double y, String glyph, String title, String[] body) {
            Node n = new Node(Kind.AUGMENT, x, y); n.glyph = glyph; n.title = title; n.body = body; return n;
        }
        public static Node filter(double x, double y, int[][] ivs, String nature, String shiny) {
            Node n = new Node(Kind.FILTER, x, y); n.ivs = ivs; n.nature = nature; n.shiny = shiny; return n;
        }
        public static Node collection(double x, double y, int count) {
            Node n = new Node(Kind.COLLECTION, x, y); n.count = count; return n;
        }
        public static Node voidBin(double x, double y, int count) {
            Node n = new Node(Kind.VOID, x, y); n.count = count; return n;
        }

        public int w() {
            return switch (kind) { case UNIT -> NODE_W; case AUGMENT -> 152; case FILTER -> 156; case COLLECTION -> 132; case VOID -> 120; };
        }
        public int h() {
            return switch (kind) { case UNIT -> NODE_H; case AUGMENT -> 40 + (body == null ? 0 : body.length * 12); case FILTER -> 150; case COLLECTION, VOID -> 60; };
        }
    }

    public static final class Wire {
        public final double ax, ay, bx, by;
        public final int color;
        public Wire(double ax, double ay, double bx, double by, int color) { this.ax = ax; this.ay = ay; this.bx = bx; this.by = by; this.color = color; }
    }

    public static final class Model {
        public final List<Node> nodes = new ArrayList<>();
        public final List<Wire> wires = new ArrayList<>();
        public double panX, panY, zoom = 1.0;
        public String title = "Daemon", sub = "", hint = "";
        public String flash;
        public boolean noUpgrade;
    }

    // ---- standalone (in-game) Daemon: canvas + its own header/footer ----
    public static void paint(GpCanvas c, Model m, int w, int h) {
        paintCanvas(c, m, w, h);
        c.gradient(0, 0, w, HEADER_H, BAR_TOP, BAR_BOT);
        c.fill(0, HEADER_H, w, HEADER_H + 1, BORDER);
        dot(c, 11, 11, PASS);
        c.text(m.title, 22, 7, TEXT);
        if (!m.sub.isEmpty()) c.text(m.sub, w - c.textWidth(m.sub) - 10, 7, MUTED);
        if (m.noUpgrade) c.text("Slot a Kernel in the wand to unlock breeding threads.", 12, HEADER_H + 12, HINTY);
        if (m.flash != null) c.text(m.flash, 12, h - FOOTER_H - 12, FLASH);
        c.gradient(0, h - FOOTER_H, w, h, BAR_BOT, BAR_TOP);
        c.fill(0, h - FOOTER_H, w, h - FOOTER_H + 1, BORDER);
        c.text(c.trim(m.hint, w - 20), 12, h - 15, DIM);
    }

    /** Just the node canvas (grid + wires + nodes) — reused by the Notebook frame. */
    public static void paintCanvas(GpCanvas c, Model m, int w, int h) {
        c.fill(0, 0, w, h, BG);
        grid(c, m, w, h);
        c.push();
        c.translate(m.panX, m.panY);
        c.scale(m.zoom);
        for (Wire wi : m.wires) wire(c, wi.ax, wi.ay, wi.bx, wi.by, wi.color);
        for (Node n : m.nodes) node(c, n);
        c.pop();
    }

    // ---- grid / wires -----------------------------------------------------------------------------
    private static void grid(GpCanvas c, Model m, int w, int h) {
        line(c, m.panX, m.panY, Math.max(8, (int) (28 * m.zoom)), w, h, GRID_MINOR);
        line(c, m.panX, m.panY, Math.max(28, (int) (112 * m.zoom)), w, h, GRID_MAJOR);
    }
    private static void line(GpCanvas c, double panX, double panY, int step, int w, int h, int color) {
        int ox = ((int) panX % step + step) % step, oy = ((int) panY % step + step) % step;
        for (int gx = ox; gx < w; gx += step) c.fill(gx, 0, gx + 1, h, color);
        for (int gy = oy; gy < h; gy += step) c.fill(0, gy, w, gy + 1, color);
    }

    private static void wire(GpCanvas c, double ax, double ay, double bx, double by, int color) {
        double dist = Math.hypot(bx - ax, by - ay);
        double k = Math.max(28, Math.min(120, dist * 0.45 + 24));   // bow upward — both ports emanate from the top
        double c1x = ax, c1y = ay - k, c2x = bx, c2y = by - k;
        int n = (int) Math.max(18, Math.min(72, dist / 8));
        double[] xs = new double[n + 1], ys = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n, u = 1 - t;
            xs[i] = u * u * u * ax + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * bx;
            ys[i] = u * u * u * ay + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * by;
        }
        c.stroke(xs, ys, 4f, (color & 0x00FFFFFF) | 0x2E000000);
        c.stroke(xs, ys, 1.6f, color);
    }

    // ---- nodes ------------------------------------------------------------------------------------
    private static void node(GpCanvas c, Node n) {
        switch (n.kind) {
            case UNIT -> unit(c, n);
            case AUGMENT -> augment(c, n);
            case FILTER -> filter(c, n);
            case COLLECTION -> bin(c, n, "Collection", "kept", PASS);
            case VOID -> bin(c, n, "Void bin", "voided", VOIDC);
        }
    }

    private static void panel(GpCanvas c, int x, int y, int w, int h, int accent) {
        c.fill(x + 1, y + 3, x + w + 1, y + h + 3, SHADOW);
        c.fill(x + 2, y + 5, x + w + 2, y + h + 5, 0x33000000);
        c.fill(x - 1, y - 1, x + w + 1, y + h + 1, accent);
        c.fill(x, y, x + w, y + h, PANEL_HI);
        c.fill(x + 1, y + 24, x + w - 1, y + h - 1, BODY);
        c.fill(x, y + 23, x + w, y + 24, DIVIDER);
        c.fill(x, y, x + w, y + 1, HILITE);
    }

    private static void unit(GpCanvas c, Node n) {
        int x = (int) n.x, y = (int) n.y, w = NODE_W, h = NODE_H;
        panel(c, x, y, w, h, n.paired ? BORDER_PAIR : BORDER);
        c.fill(x + 6, y + 6, x + 9, y + 18, PAIR);
        int sc = SPRITES[Math.floorMod(n.species.hashCode(), SPRITES.length)];
        c.fill(x + 13, y + 5, x + 31, y + 19, darken(sc));
        c.fill(x + 14, y + 6, x + 30, y + 18, sc);
        c.text(initial(n.label), x + 19, y + 8, SPRITE_TEXT);
        c.text(c.trim(n.label, w - 38), x + 35, y + 8, TEXT);
        c.text("UNIT", x + 12, y + 30, DIM);
        dot(c, x + 14, y + 46, n.paired ? PASS : DIM);
        c.text(n.paired ? "Pair " + n.bucket : "unpaired", x + 22, y + 43, n.paired ? PASS : MUTED);
        // wire port: an embedded pink line out the TOP-center (drag the middle to wire; sides drag-move).
        int cxp = x + w / 2;
        c.fill(cxp - 1, y - 8, cxp + 1, y + 1, PAIR);
        diamond(c, cxp, y - 9, 3, PAIR);
    }

    private static void augment(GpCanvas c, Node n) {
        int x = (int) n.x, y = (int) n.y, w = n.w(), h = n.h();
        panel(c, x, y, w, h, BORDER);
        c.fill(x + 6, y + 6, x + 9, y + 18, DATA);
        c.fill(x + 13, y + 5, x + 31, y + 19, CELL);
        if (n.glyph != null) c.text(n.glyph, x + 17, y + 8, DATA);
        c.text(c.trim(n.title, w - 38), x + 35, y + 8, TEXT);
        c.text("MODIFIER", x + 12, y + 30, DIM);
        if (n.body != null) for (int i = 0; i < n.body.length; i++) c.text("• " + n.body[i], x + 12, y + 42 + i * 12, MUTED);
        triLeft(c, x, y + 33, FLOW);
        triRight(c, x + w, y + 33, FLOW);
    }

    private static void filter(GpCanvas c, Node n) {
        int x = (int) n.x, y = (int) n.y, w = n.w(), h = n.h();
        panel(c, x, y, w, h, BORDER);
        c.fill(x + 6, y + 6, x + 9, y + 18, FLOW);
        c.text("IV filter", x + 13, y + 8, TEXT);
        c.text("IV GATE", x + w - c.textWidth("IV GATE") - 10, y + 9, DIM);
        for (int i = 0; i < STATS.length; i++) {
            int ry = y + 30 + i * 14;
            int[] r = n.ivs != null && i < n.ivs.length ? n.ivs[i] : new int[]{0, 31};
            c.text(STATS[i], x + 12, ry, MUTED);
            chip(c, x + 44, ry - 2, 30, r[0], r[0] == 31);
            c.text("-", x + 78, ry, DIM);
            chip(c, x + 86, ry - 2, 30, r[1], r[1] == 31);
        }
        int ny = y + 30 + STATS.length * 14 + 4;
        c.text("nat " + (n.nature == null ? "Any" : n.nature), x + 12, ny, MUTED);
        c.text("shiny " + (n.shiny == null ? "any" : n.shiny), x + 12, ny + 12, MUTED);
        triLeft(c, x, y + 33, FLOW);
        triRight(c, x + w, y + 40, PASS);
        cross(c, x + w, y + h - 22, VOIDC);
    }

    private static void chip(GpCanvas c, int x, int y, int w, int val, boolean perfect) {
        c.fill(x - 1, y - 1, x + w + 1, y + 12, perfect ? 0xFF234A3A : BORDER);
        c.fill(x, y, x + w, y + 11, CELL);
        String s = String.valueOf(val);
        c.text(s, x + (w - c.textWidth(s)) / 2, y + 2, perfect ? PASS : MUTED);
    }

    private static void bin(GpCanvas c, Node n, String title, String label, int accent) {
        int x = (int) n.x, y = (int) n.y, w = n.w(), h = n.h();
        panel(c, x, y, w, h, BORDER);
        c.fill(x + 6, y + 6, x + 9, y + 18, accent);
        c.text(title, x + 13, y + 8, TEXT);
        c.text(label, x + 12, y + 30, DIM);
        String num = String.valueOf(n.count);
        c.text(num, x + w - c.textWidth(num) * 1 - 14, y + 38, accent);
        if (n.kind == Kind.VOID) cross(c, x, y + 30, accent); else triLeft(c, x, y + 30, accent);
    }

    // ---- shape-coded ports + dots -----------------------------------------------------------------
    private static void diamond(GpCanvas c, int cx, int cy, int half, int color) {
        for (int dy = -half; dy <= half; dy++) { int span = half - Math.abs(dy); c.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color); }
    }
    private static void triRight(GpCanvas c, int cx, int cy, int color) {
        for (int i = 0; i < 5; i++) c.fill(cx - 3 + i, cy - (4 - i), cx - 2 + i, cy + (4 - i), color);
    }
    private static void triLeft(GpCanvas c, int cx, int cy, int color) {
        for (int i = 0; i < 5; i++) c.fill(cx + 3 - i, cy - (4 - i), cx + 4 - i, cy + (4 - i), color);
    }
    private static void cross(GpCanvas c, int cx, int cy, int color) {
        for (int i = -3; i <= 3; i++) { c.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color); c.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color); }
    }
    private static void dot(GpCanvas c, int cx, int cy, int color) {
        c.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
        c.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
    }
    private static int darken(int argb) {
        int r = (int) (((argb >> 16) & 0xFF) * 0.55), g = (int) (((argb >> 8) & 0xFF) * 0.55), b = (int) ((argb & 0xFF) * 0.55);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private static String initial(String s) { return s == null || s.isEmpty() ? "?" : s.substring(0, 1).toUpperCase(); }
}
