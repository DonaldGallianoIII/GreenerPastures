package com.greenerpastures.client.ui;

/**
 * The unified <b>Notebook</b> chrome (Minecraft-free) — the mockup's window frame wrapping the Daemon
 * canvas: a title bar + Kernel status, a tab strip (Daemon · Dashboard · Compiler), and a header row
 * with the pasture <b>name</b> and the <b>Kernel slot</b> (tier + installed augments). Draws the
 * Daemon canvas via {@link DaemonView#paintCanvas} then overlays the chrome.
 *
 * <p>This is the studio/preview unification of what is, in-game today, several separate screens (wand
 * = name + Kernel slot, Daemon = the canvas). Same paint backend as everything else.
 */
public final class NotebookView {
    private NotebookView() {}

    public static final class Frame {
        public String file = "pasture.ipynb";
        public String pastureName = "";
        public String kernelTier = "";
        public String[] kernelAugments = {};
        public String[] tabs = {"Daemon", "Dashboard", "Compiler"};
        public int activeTab = 0;
        public String threads = "";
    }

    private static final int TITLE = 20, TABS = 24, HEAD = 40, FOOTER = 22;
    public static final int CHROME_TOP = TITLE + TABS + HEAD;   // node canvas sits below this

    public static void paint(GpCanvas c, DaemonView.Model daemon, Frame f, int w, int h) {
        DaemonView.paintCanvas(c, daemon, w, h);

        final int D = DaemonView.DIM, T = DaemonView.TEXT, M = DaemonView.MUTED;

        // title bar
        c.gradient(0, 0, w, TITLE, DaemonView.BAR_TOP, DaemonView.BAR_BOT);
        c.fill(0, TITLE, w, TITLE + 1, DaemonView.BORDER);
        c.text(f.file, 10, 6, T);
        c.text("· Greener Pastures", 12 + c.textWidth(f.file), 6, D);
        String k = "Kernel · running";
        dot(c, w - c.textWidth(k) - 18, 10, DaemonView.PASS);
        c.text(k, w - c.textWidth(k) - 10, 6, M);

        // tab strip
        c.fill(0, TITLE, w, TITLE + TABS, DaemonView.BAR_BOT);
        c.fill(0, TITLE + TABS, w, TITLE + TABS + 1, DaemonView.BORDER);
        int tx = 8;
        for (int i = 0; i < f.tabs.length; i++) {
            int tw = c.textWidth(f.tabs[i]) + 22;
            boolean on = i == f.activeTab;
            if (on) {
                c.fill(tx, TITLE + 4, tx + tw, TITLE + TABS, DaemonView.PANEL_HI);
                c.fill(tx, TITLE + 4, tx + tw, TITLE + 6, DaemonView.PAIR);   // active accent
            }
            c.text(f.tabs[i], tx + 11, TITLE + 9, on ? T : D);
            tx += tw + 3;
        }

        // header row: PASTURE name (left, flexible) + KERNEL slot/tier + threads (right). Laid out from the
        // right using measured text widths so the blocks never overlap on small windows / high GUI scales.
        int hy = TITLE + TABS;
        c.fill(0, hy, w, hy + HEAD, DaemonView.PANEL);
        c.fill(0, hy + HEAD, w, hy + HEAD + 1, DaemonView.BORDER);

        int rightX = w - 10;
        if (!f.threads.isEmpty()) {
            int tw = c.textWidth(f.threads);
            c.text(f.threads, rightX - tw, hy + 14, M);
            rightX -= tw + 16;
        }

        String tier = f.kernelTier.isEmpty() ? "no Kernel" : f.kernelTier;
        final int kSlot = 28;
        int kx = Math.max(118, rightX - (kSlot + 8 + c.textWidth(tier)));
        c.text("KERNEL", kx, hy + 5, D);
        c.fill(kx - 1, hy + 14, kx + kSlot + 1, hy + 34, DaemonView.BORDER_PAIR);
        c.fill(kx, hy + 15, kx + kSlot, hy + 33, DaemonView.CELL);
        c.text("K", kx + 10, hy + 20, DaemonView.PASS);
        c.text(c.trim(tier, Math.max(0, rightX - (kx + kSlot + 8))), kx + kSlot + 8, hy + 20, T);

        c.text("PASTURE", 10, hy + 5, D);
        int nameRight = Math.max(70, kx - 12);
        c.fill(9, hy + 15, nameRight + 1, hy + 35, DaemonView.BORDER);
        c.fill(10, hy + 16, nameRight, hy + 34, DaemonView.CELL);
        boolean named = f.pastureName != null && !f.pastureName.isEmpty();
        c.text(named ? c.trim(f.pastureName, nameRight - 16) : "Name Here!", 16, hy + 20, named ? T : D);

        // footer
        c.gradient(0, h - FOOTER, w, h, DaemonView.BAR_BOT, DaemonView.BAR_TOP);
        c.fill(0, h - FOOTER, w, h - FOOTER + 1, DaemonView.BORDER);
        c.text(c.trim(daemon.hint, w - 20), 12, h - 15, D);
    }

    private static void dot(GpCanvas c, int cx, int cy, int color) {
        c.fill(cx - 1, cy - 2, cx + 2, cy + 3, color);
        c.fill(cx - 2, cy - 1, cx + 3, cy + 2, color);
    }
}
