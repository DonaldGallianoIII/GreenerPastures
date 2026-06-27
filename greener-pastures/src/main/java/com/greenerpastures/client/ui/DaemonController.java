package com.greenerpastures.client.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Daemon's <b>state + interaction</b>, with zero Minecraft dependencies — pan, mouse-wheel zoom,
 * drag-to-wire pairing, right-click unpair. The in-game {@code DaemonScreen} forwards Minecraft mouse
 * events here and paints {@link #buildModel()} via {@code McGpCanvas}; the desktop Design Studio
 * forwards Swing mouse events here and paints via Java2D. <b>Same controller, same behaviour</b>, so
 * the GUI can be driven interactively outside Minecraft exactly as it is in-game.
 *
 * <p>Holds only the pairing state ({@code assign}) + cosmetic node positions; persistence/navigation
 * is the adapter's job (the screen sends packets; the studio just closes the window).
 */
public final class DaemonController {
    /** A tethered unit, Minecraft-free (id + display + its saved pair bucket). */
    public record Unit(UUID id, String species, String label, int initialBucket) {}

    private static final double PORT = 7, MIN_ZOOM = 0.2, MAX_ZOOM = 2.5;

    private final List<Unit> units;
    private final int nBuckets;
    private final String pastureName;

    private final Map<UUID, Integer> assign = new HashMap<>();      // unit -> bucket (complete pairs)
    private final Map<UUID, float[]> nodePos = new HashMap<>();     // unit -> world top-left
    private boolean dirty;

    private double panX, panY, zoom = 1.0;
    private enum Mode { NONE, PAN, DRAG, WIRE }
    private Mode mode = Mode.NONE;
    private UUID dragId, wireFrom;
    private double grabWX, grabWY, wireWX, wireWY;
    private int lastX, lastY;
    private String flashMsg;
    private int flashTicks;

    public DaemonController(List<Unit> units, int maxPairs, String pastureName) {
        this.units = units;
        this.nBuckets = Math.max(0, Math.min(12, maxPairs));
        this.pastureName = pastureName == null ? "" : pastureName;

        Map<Integer, List<UUID>> byBucket = new HashMap<>();
        for (Unit u : units) {
            int b = u.initialBucket();
            if (b >= 1 && b <= nBuckets) byBucket.computeIfAbsent(b, k -> new ArrayList<>()).add(u.id());
        }
        byBucket.forEach((b, mem) -> { if (mem.size() >= 2) { assign.put(mem.get(0), b); assign.put(mem.get(1), b); } });

        for (int i = 0; i < units.size(); i++) {
            int col = i % 3, row = i / 3;
            nodePos.put(units.get(i).id(), new float[]{40f + col * 176f, 56f + row * 84f});
        }
    }

    /** Set the initial camera (used to frame content below the Notebook chrome). */
    public void initView(double px, double py, double z) { this.panX = px; this.panY = py; this.zoom = z; }

    public int pairCount() { return completePairs(); }
    public int maxPairs() { return nBuckets; }

    public boolean isDirty() { return dirty; }
    public Map<UUID, Integer> pairings() {
        Map<UUID, Integer> out = new HashMap<>();
        assign.forEach((id, b) -> { if (b != null && b > 0) out.put(id, b); });
        return out;
    }
    public void tickFlash() { if (flashTicks > 0) flashTicks--; }

    // ---- screen <-> world ----
    private double wx(double mx) { return (mx - panX) / zoom; }
    private double wy(double my) { return (my - panY) / zoom; }
    private float[] world(UUID id) {
        float[] w = nodePos.get(id);
        if (w == null) { w = new float[]{40f, 56f}; nodePos.put(id, w); }
        return w;
    }
    private double[] rightPortW(UUID id) { float[] w = world(id); return new double[]{w[0] + DaemonView.NODE_W, w[1] + DaemonView.NODE_H / 2.0}; }
    private double[] leftPortW(UUID id)  { float[] w = world(id); return new double[]{w[0],                    w[1] + DaemonView.NODE_H / 2.0}; }

    public DaemonView.Model buildModel() {
        DaemonView.Model m = new DaemonView.Model();
        m.panX = panX; m.panY = panY; m.zoom = zoom;
        m.title = pastureName.isEmpty() ? "Daemon" : "Daemon — " + pastureName;
        m.sub = "daemon · " + completePairs() + "/" + nBuckets + " threads · " + Math.round(zoom * 100) + "%";
        m.hint = "drag a unit's port onto another unit to pair · right-click to unpair · scroll to zoom · drag to pan · Esc -> menu";
        m.noUpgrade = nBuckets == 0;
        if (flashTicks > 0) m.flash = flashMsg;
        for (Unit u : units) {
            float[] w = world(u.id());
            m.nodes.add(new DaemonView.Node(w[0], w[1], u.label(), u.species(), isPaired(u.id()), assign.getOrDefault(u.id(), 0)));
        }
        for (int b = 1; b <= nBuckets; b++) {
            UUID[] p = membersOf(b);
            if (p == null) continue;
            double[] a = rightPortW(p[0]), c = leftPortW(p[1]);
            m.wires.add(new DaemonView.Wire(a[0], a[1], c[0], c[1], DaemonView.PAIR));
        }
        if (mode == Mode.WIRE && wireFrom != null) {
            double[] a = rightPortW(wireFrom);
            m.wires.add(new DaemonView.Wire(a[0], a[1], wireWX, wireWY, DaemonView.WIRE_TEMP));
        }
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
    private boolean overRightPort(UUID id, double mx, double my) {
        double[] rp = rightPortW(id);
        return Math.abs(wx(mx) - rp[0]) <= PORT && Math.abs(wy(my) - rp[1]) <= PORT;
    }

    // ---- pairing ----
    private int bucketCount(int b) { int c = 0; for (int v : assign.values()) if (v == b) c++; return c; }
    private int completePairs() { int n = 0; for (int b = 1; b <= nBuckets; b++) if (bucketCount(b) >= 2) n++; return n; }
    private boolean isPaired(UUID id) { Integer b = assign.get(id); return b != null && bucketCount(b) >= 2; }
    private UUID[] membersOf(int b) {
        UUID a = null, c = null;
        for (Unit u : units) {
            if (!Integer.valueOf(b).equals(assign.get(u.id()))) continue;
            if (a == null) a = u.id(); else if (c == null) { c = u.id(); break; }
        }
        return (a != null && c != null) ? new UUID[]{a, c} : null;
    }
    private int freeBucket() { for (int b = 1; b <= nBuckets; b++) if (bucketCount(b) == 0) return b; return 0; }
    private void pair(UUID a, UUID b) {
        if (a.equals(b)) return;
        assign.remove(a); assign.remove(b);
        int f = freeBucket();
        if (f == 0) { flash("All " + nBuckets + " threads are full — unpair one first."); return; }
        assign.put(a, f); assign.put(b, f); dirty = true;
    }
    private void unpair(UUID id) {
        Integer b = assign.get(id);
        if (b == null) return;
        assign.values().removeIf(v -> v.equals(b)); dirty = true;
    }
    private void flash(String s) { flashMsg = s; flashTicks = 80; }

    // ---- input (screen coords; button: 0 = left, 1 = right) ----
    public boolean mouseDown(double mx, double my, int button) {
        UUID node = nodeAt(mx, my);
        if (node != null) {
            if (button == 1) { unpair(node); return true; }
            if (button == 0) {
                if (overRightPort(node, mx, my)) { mode = Mode.WIRE; wireFrom = node; wireWX = wx(mx); wireWY = wy(my); }
                else { float[] w = world(node); mode = Mode.DRAG; dragId = node; grabWX = wx(mx) - w[0]; grabWY = wy(my) - w[1]; }
                return true;
            }
        }
        if (button == 0) { mode = Mode.PAN; lastX = (int) mx; lastY = (int) my; return true; }
        return false;
    }

    public boolean mouseDrag(double mx, double my) {
        switch (mode) {
            case WIRE -> { wireWX = wx(mx); wireWY = wy(my); return true; }
            case DRAG -> { nodePos.put(dragId, new float[]{(float) (wx(mx) - grabWX), (float) (wy(my) - grabWY)}); return true; }
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
        return was;
    }

    /** {@code dir > 0} zooms in (toward the cursor). */
    public boolean scroll(double mx, double my, double dir) {
        double old = zoom;
        double nz = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (dir > 0 ? 1.12 : 1 / 1.12)));
        if (nz == old) return true;
        double X = (mx - panX) / old, Y = (my - panY) / old;
        zoom = nz; panX = mx - X * zoom; panY = my - Y * zoom;
        return true;
    }
}
