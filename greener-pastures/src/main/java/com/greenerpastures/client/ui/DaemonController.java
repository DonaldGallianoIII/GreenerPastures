package com.greenerpastures.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Daemon's <b>state + interaction</b>, Minecraft-free. The in-game {@code DaemonScreen} and the
 * desktop Design Studio both forward mouse events here and paint {@link #buildModel()} - same code.
 *
 * <p><b>Interaction (Deuce's spec, 2026-06-28):</b>
 * <ul>
 *   <li><b>drag the MIDDLE of a unit → wire</b> (the pink port lives at the top-center of the box)</li>
 *   <li><b>drag the SIDES of a unit → move</b> the box (people grab edges to reposition)</li>
 *   <li><b>right-click a unit → unpair</b></li>
 *   <li><b>scroll → zoom</b> toward the cursor · <b>middle-drag / empty-drag → pan</b></li>
 * </ul>
 *
 * <p><b>Viewport-relative:</b> opens auto-fit-to-viewport (≤16 units in a grid, centered/scaled to the
 * screen - resolution independent), then you zoom/pan freely. Text stays crisp at any zoom (handled in
 * the canvas backends).
 */
public final class DaemonController {
    /** A tethered unit, Minecraft-free (id + display + its saved pair bucket). */
    public record Unit(UUID id, String species, String label, int initialBucket) {}

    private static final int MARGIN = 16, PAD = 12, FOOTER = 22;
    private static final int GAP_X = 34, GAP_Y = 26;
    private static final double MIN_FIT = 0.30, MIN_ZOOM = 0.25, MAX_ZOOM = 3.0;

    private final List<Unit> units;
    private final int nBuckets;
    private final String pastureName;

    private final Map<UUID, Integer> assign = new HashMap<>();
    private final Map<UUID, float[]> nodePos = new HashMap<>();
    private boolean dirty;

    private double panX, panY, zoom = 1.0;             // fit transform on open; then user zoom/pan
    private int viewW = 960, viewH = 540, contentTop = -1;

    private enum Mode { NONE, WIRE, MOVE, PAN }
    private Mode mode = Mode.NONE;
    private UUID wireFrom, dragId;
    private double wireWX, wireWY, grabWX, grabWY;
    private int lastX, lastY;
    private String flashMsg;
    private int flashTicks;
    private DaemonView.Model cachedModel;   // perf-audit H4: rebuilt on input/flash only, not every frame
    private boolean modelDirty = true;

    public DaemonController(List<Unit> units, int maxPairs, String pastureName) {
        this.units = units;
        this.nBuckets = Math.max(0, Math.min(8, maxPairs));
        this.pastureName = pastureName == null ? "" : pastureName;

        Map<Integer, List<UUID>> byBucket = new HashMap<>();
        for (Unit u : units) {
            int b = u.initialBucket();
            if (b >= 1 && b <= nBuckets) byBucket.computeIfAbsent(b, k -> new ArrayList<>()).add(u.id());
        }
        byBucket.forEach((b, mem) -> { if (mem.size() >= 2) { assign.put(mem.get(0), b); assign.put(mem.get(1), b); } });

        relayout();
    }

    /** Fit the graph to the viewport. Re-fits ONLY when the size changes, so it never stomps the user's
     *  zoom/pan/moves mid-session (safe to call each frame). {@code top} = chrome height above the canvas. */
    public void setViewport(int w, int h, int top) {
        w = Math.max(120, w); h = Math.max(120, h); top = Math.max(0, top);
        if (w == viewW && h == viewH && top == contentTop) return;
        viewW = w; viewH = h; contentTop = top;
        relayout();
        modelDirty = true;
    }

    private void relayout() {
        int n = units.size();
        int availW = Math.max(80, viewW - 2 * MARGIN);
        int availTop = Math.max(0, contentTop) + PAD;
        int availH = Math.max(80, viewH - availTop - FOOTER - PAD);
        if (n == 0) { zoom = 1.0; panX = MARGIN; panY = availTop; return; }

        double aspect = availW / (double) availH;
        int cols = (int) Math.round(Math.sqrt(n * aspect * (DaemonView.NODE_H + GAP_Y) / (double) (DaemonView.NODE_W + GAP_X)));
        cols = Math.max(1, Math.min(n, cols));
        int rows = (int) Math.ceil(n / (double) cols);

        for (int i = 0; i < n; i++) {
            int col = i % cols, row = i / cols;
            nodePos.put(units.get(i).id(),
                    new float[]{ col * (float) (DaemonView.NODE_W + GAP_X), row * (float) (DaemonView.NODE_H + GAP_Y) });
        }

        double neededW = cols * DaemonView.NODE_W + (cols - 1) * GAP_X;
        double neededH = rows * DaemonView.NODE_H + (rows - 1) * GAP_Y;
        zoom = Math.max(MIN_FIT, Math.min(1.0, Math.min(availW / neededW, availH / neededH)));
        panX = MARGIN + (availW - neededW * zoom) / 2.0;
        panY = availTop + (availH - neededH * zoom) / 2.0;
    }

    public int pairCount() { return completePairs(); }
    public int maxPairs() { return nBuckets; }
    public boolean isDirty() { return dirty; }
    public Map<UUID, Integer> pairings() {
        Map<UUID, Integer> out = new HashMap<>();
        assign.forEach((id, b) -> { if (b != null && b > 0) out.put(id, b); });
        return out;
    }
    public void tickFlash() { if (flashTicks > 0 && --flashTicks == 0) modelDirty = true; }

    // ---- screen <-> world ----
    private double wx(double mx) { return (mx - panX) / zoom; }
    private double wy(double my) { return (my - panY) / zoom; }
    private float[] world(UUID id) {
        float[] w = nodePos.get(id);
        if (w == null) { w = new float[]{0f, 0f}; nodePos.put(id, w); }
        return w;
    }
    /** Wire port: top-center of the box (the pink line emanates upward from here). */
    private double[] topPortW(UUID id) { float[] w = world(id); return new double[]{w[0] + DaemonView.NODE_W / 2.0, w[1]}; }

    public DaemonView.Model buildModel() {
        if (cachedModel != null && !modelDirty && mode == Mode.NONE && flashTicks <= 0) return cachedModel;
        DaemonView.Model m = new DaemonView.Model();
        m.panX = panX; m.panY = panY; m.zoom = zoom;
        m.title = pastureName.isEmpty() ? "Daemon" : "Daemon - " + pastureName;
        m.sub = "daemon · " + completePairs() + "/" + nBuckets + " threads · " + Math.round(zoom * 100) + "%";
        m.hint = "drag middle = wire · drag sides = move · right-click = unpair · scroll = zoom";
        m.noUpgrade = nBuckets == 0;
        if (flashTicks > 0) m.flash = flashMsg;
        for (Unit u : units) {
            float[] w = world(u.id());
            m.nodes.add(new DaemonView.Node(w[0], w[1], u.label(), u.species(), isPaired(u.id()), assign.getOrDefault(u.id(), 0)));
        }
        for (int b = 1; b <= nBuckets; b++) {
            UUID[] p = membersOf(b);
            if (p == null) continue;
            double[] a = topPortW(p[0]), c = topPortW(p[1]);
            m.wires.add(new DaemonView.Wire(a[0], a[1], c[0], c[1], DaemonView.PAIR));
        }
        if (mode == Mode.WIRE && wireFrom != null) {
            double[] a = topPortW(wireFrom);
            m.wires.add(new DaemonView.Wire(a[0], a[1], wireWX, wireWY, DaemonView.WIRE_TEMP));
        }
        cachedModel = m;
        modelDirty = false;
        return m;
    }

    // ---- hit-testing ----
    private UUID nodeAt(double mx, double my) {
        double X = wx(mx), Y = wy(my);
        UUID hit = null;
        for (Unit u : units) {
            float[] w = world(u.id());
            if (X >= w[0] && X <= w[0] + DaemonView.NODE_W && Y >= w[1] && Y <= w[1] + DaemonView.NODE_H) hit = u.id();
        }
        return hit;
    }
    /** True if {@code mx} falls in the middle horizontal third of the node (the wire zone). */
    private boolean inWireZone(UUID id, double mx) {
        float[] w = world(id);
        double X = wx(mx), third = DaemonView.NODE_W / 3.0;
        return X >= w[0] + third && X <= w[0] + 2 * third;
    }

    // ---- pairing ----
    private int bucketCount(int b) { int c = 0; for (int v : assign.values()) if (v == b) c++; return c; }
    private int completePairs() { int n = 0; for (int b = 1; b <= nBuckets; b++) if (bucketCount(b) >= 2) n++; return n; }
    private boolean isPaired(UUID id) { Integer b = assign.get(id); return b != null && bucketCount(b) >= 2; }
    private UUID[] membersOf(int b) {
        UUID a = null, c = null;
        for (Unit u : units) {
            if (!Integer.valueOf(b).equals(assign.get(u.id()))) continue;
            if (a == null) a = u.id(); else { c = u.id(); break; }
        }
        return (a != null && c != null) ? new UUID[]{a, c} : null;
    }
    private int freeBucket() { for (int b = 1; b <= nBuckets; b++) if (bucketCount(b) == 0) return b; return 0; }
    private void pair(UUID a, UUID b) {
        if (a.equals(b)) return;
        assign.remove(a); assign.remove(b);
        int f = freeBucket();
        if (f == 0) { flash("All " + nBuckets + " threads are full - unpair one first."); return; }
        assign.put(a, f); assign.put(b, f); dirty = true;
    }
    private void unpair(UUID id) {
        Integer b = assign.get(id);
        if (b == null) return;
        assign.values().removeIf(v -> v.equals(b)); dirty = true;
        modelDirty = true;   // regression fix: right-click-unpair must invalidate the model cache (was stale until btn release)
    }
    private void flash(String s) { flashMsg = s; flashTicks = 80; modelDirty = true; }

    // ---- input (screen coords; button: 0 = left, 1 = right, 2 = middle) ----
    public boolean mouseDown(double mx, double my, int button) {
        UUID node = nodeAt(mx, my);
        if (node != null && button == 1) { unpair(node); return true; }       // right-click a unit: unpair
        if (node != null && button == 0) {
            if (inWireZone(node, mx)) {                                        // middle of box: start a wire
                mode = Mode.WIRE; wireFrom = node; wireWX = wx(mx); wireWY = wy(my);
            } else {                                                          // sides of box: move it
                float[] w = world(node);
                mode = Mode.MOVE; dragId = node; grabWX = wx(mx) - w[0]; grabWY = wy(my) - w[1];
            }
            return true;
        }
        if (button == 2 || (button == 0 && node == null)) {                   // middle anywhere, or left on empty: pan
            mode = Mode.PAN; lastX = (int) mx; lastY = (int) my; return true;
        }
        return false;
    }

    public boolean mouseDrag(double mx, double my) {
        switch (mode) {
            case WIRE -> { wireWX = wx(mx); wireWY = wy(my); return true; }
            case MOVE -> { nodePos.put(dragId, new float[]{(float) (wx(mx) - grabWX), (float) (wy(my) - grabWY)}); return true; }
            case PAN  -> { panX += mx - lastX; panY += my - lastY; lastX = (int) mx; lastY = (int) my; return true; }
            default   -> { return false; }
        }
    }

    public boolean mouseUp(double mx, double my, int button) {
        if (mode == Mode.WIRE && wireFrom != null) {
            UUID target = nodeAt(mx, my);
            if (target != null && !target.equals(wireFrom)) pair(wireFrom, target);
        }
        boolean was = mode != Mode.NONE;
        mode = Mode.NONE; wireFrom = null; dragId = null;
        modelDirty = true;
        return was;
    }

    /** Scroll to zoom toward the cursor (for navigating big filter chains). */
    public boolean scroll(double mx, double my, double dir) {
        double old = zoom;
        double nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (dir > 0 ? 1.12 : 1 / 1.12)));
        if (nz == old) return true;
        double X = (mx - panX) / old, Y = (my - panY) / old;
        zoom = nz; panX = mx - X * zoom; panY = my - Y * zoom;
        modelDirty = true;
        return true;
    }
}
