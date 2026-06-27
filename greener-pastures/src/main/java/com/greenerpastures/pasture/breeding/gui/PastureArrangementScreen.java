package com.greenerpastures.pasture.breeding.gui;

import com.greenerpastures.client.ui.GpButton;
import com.greenerpastures.pasture.breeding.net.OpenPasturePayload;
import com.greenerpastures.pasture.breeding.net.SavePairingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The "Breeding Arrangement" board — laid out to Deuce's GreenerPastures-Layout.html (a centered
 * 400-wide frame): numbered <b>pair buckets</b> (112×66 on a 3-col / 128×80 grid, top-anchored at
 * y=30) you drag mons into, a "Node size" control, and an Unpaired pool filling the space below.
 *
 * <p>Bucket count comes from the slotted Pasture Upgrade ({@code maxPairs}). The bucket assignment IS
 * the state — chip positions are derived from it each frame — so closing the board sends the whole
 * assignment to the server, which drives {@code MultiPairBreeder}.
 */
public class PastureArrangementScreen extends Screen {
    // palette  (BG/zones are FULLY opaque — this screen paints its own solid canvas, no vanilla blur)
    private static final int BG          = 0xFF0B0B0D;
    private static final int GRID        = 0xFF16161C;
    private static final int TEXT        = 0xFFD6D6DE;
    private static final int DIM         = 0xFF8A8A96;
    private static final int ZONE_BG     = 0xFF141418;
    private static final int POOL_BORDER = 0xFF6E6E78;
    private static final int PAIR_BORDER = 0xFF5A8A6A;
    private static final int CHIP_BG     = 0xFF1C1C22;
    private static final int CHIP_BORDER = 0xFF55555E;
    private static final int CHIP_PAIRED = 0xFF18301F;
    private static final int CHIP_PAIRED_BORDER = 0xFF5A8A6A;
    private static final int DRAG_BG     = 0xFF2A2A34;

    // layout (1 px = 1 GUI px) — design frame 400×260, buckets at 16/144/272 × 30/110, 112×66
    private static final int FRAME_W = 400;
    private static final int COLS = 3;
    private static final int BW = 112, BH = 66;
    private static final int COL_PITCH = 128, ROW_PITCH = 80;
    private static final int BUCKET_Y0 = 30;
    private static final int CHIP_H = 18;
    private static final int MAX_BUCKETS = 12;
    private static final int[] NODE_COLS = {3, 4, 6};                 // Node size: Large / Medium / Small
    private static final String[] NODE_NAMES = {"Large", "Medium", "Small"};

    private final BlockPos pos;
    private final String pastureName;
    private final int nBuckets;
    private final List<MonEntry> roster;

    /** tethering id -> bucket (1-based). Absent = unpaired. The single source of truth here. */
    private final Map<UUID, Integer> assign = new HashMap<>();
    private boolean assignReady;

    // derived each frame / on resize
    private int frameX, belowBucketsY;
    private int nodeIdx = 0;
    private GpButton nodeButton;

    // derived each frame for hit-testing
    private final Map<UUID, int[]> chipBox = new HashMap<>();   // id -> {x,y,w,h}
    private int[] poolRect = {0, 0, 0, 0};
    private final int[][] bucketRect = new int[MAX_BUCKETS][];

    private UUID dragging;
    private int dragOffX, dragOffY, dragX, dragY;
    private boolean dirty;            // only persist pairings if the user actually changed something
    private boolean persisted;        // guard so close() + removed() don't double-send
    private String flashMsg;
    private int flashTicks;

    public PastureArrangementScreen(BlockPos pos, String name, int maxPairs, List<MonEntry> roster) {
        super(Text.literal("Breeding Arrangement"));
        this.pos = pos;
        this.pastureName = name == null ? "" : name;
        this.nBuckets = Math.max(0, Math.min(MAX_BUCKETS, maxPairs));
        this.roster = roster;
    }

    @Override
    protected void init() {
        if (!assignReady) {
            for (MonEntry m : roster) {
                int b = m.bucket();
                if (b >= 1 && b <= nBuckets && bucketCount(b, null) < 2) assign.put(m.id(), b);
            }
            assignReady = true;
        }
        computeFrame();
        nodeButton = new GpButton(frameX + 16, belowBucketsY + 2, 120, 18, nodeLabel(), this::cycleNodeSize);
    }

    private void computeFrame() {
        frameX = Math.max(8, (this.width - FRAME_W) / 2);
        int rows = (nBuckets + COLS - 1) / COLS;     // 0 when no upgrade
        // when there are no buckets, still reserve a line for the "slot an upgrade" message so the
        // Node-size button + pool don't ride up into the header (the overlap in screenshot 622)
        belowBucketsY = BUCKET_Y0 + (rows == 0 ? 20 : rows * ROW_PITCH);
    }

    private String nodeLabel() {
        return "Node size: " + NODE_NAMES[nodeIdx];
    }

    private void cycleNodeSize() {
        nodeIdx = (nodeIdx + 1) % NODE_COLS.length;
        if (nodeButton != null) nodeButton.label = nodeLabel();
    }

    // ---- layout ---------------------------------------------------------------------------------

    private void relayout() {
        chipBox.clear();
        computeFrame();

        // Pair buckets (top-anchored at the design grid)
        for (int b = 1; b <= nBuckets; b++) {
            int idx = b - 1, col = idx % COLS, row = idx / COLS;
            int bx = frameX + 16 + col * COL_PITCH;
            int by = BUCKET_Y0 + row * ROW_PITCH;
            bucketRect[idx] = new int[]{bx, by, BW, BH};
            int placed = 0;
            for (MonEntry m : roster) {
                if (m.id().equals(dragging)) continue;
                Integer a = assign.get(m.id());
                if (a == null || a != b || placed >= 2) continue;
                chipBox.put(m.id(), new int[]{bx + 4, by + 18 + placed * 22, BW - 8, CHIP_H});
                placed++;
            }
        }

        // Unpaired pool — fills from below the Node-size control to the bottom of the screen
        int poolY = belowBucketsY + 26;
        int poolH = Math.max(40, this.height - poolY - 8);
        poolRect = new int[]{frameX + 16, poolY, FRAME_W - 32, poolH};
        int cols = NODE_COLS[nodeIdx];
        int gap = 6;
        int cw = (poolRect[2] - (cols - 1) * gap) / cols;
        int pi = 0;
        for (MonEntry m : roster) {
            if (m.id().equals(dragging) || assign.containsKey(m.id())) continue;
            int col = pi % cols, row = pi / cols;
            chipBox.put(m.id(), new int[]{poolRect[0] + col * (cw + gap), poolY + 18 + row * 20, cw, CHIP_H - 2});
            pi++;
        }
    }

    private int bucketCount(int b, UUID exclude) {
        int c = 0;
        for (Map.Entry<UUID, Integer> e : assign.entrySet()) {
            if (e.getValue() == b && !e.getKey().equals(exclude)) c++;
        }
        return c;
    }

    private MonEntry monById(UUID id) {
        for (MonEntry m : roster) if (m.id().equals(id)) return m;
        return null;
    }

    // ---- render ---------------------------------------------------------------------------------

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Paint our own opaque canvas — do NOT call renderBackground() (its blur/darkening would sit
        // on top of the world and bleed through, hiding everything but the buttons).
        ctx.fill(0, 0, this.width, this.height, BG);
        for (int gx = 0; gx < this.width; gx += 24) ctx.fill(gx, 0, gx + 1, this.height, GRID);
        for (int gy = 0; gy < this.height; gy += 24) ctx.fill(0, gy, this.width, gy + 1, GRID);

        relayout();
        if (flashTicks > 0) flashTicks--;

        // Header — trimmed so it never runs under the Back button (top-right)
        String title = pastureName.isEmpty() ? "Breeding Arrangement" : "Breeding Arrangement — " + pastureName;
        ctx.drawText(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(title, 300)), frameX + 8, 8, TEXT, false);
        ctx.drawText(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth("drag a mon onto a pair · right-click to unpair · Esc to close", 320)),
                frameX + 8, 20, DIM, false);

        if (nBuckets == 0) {
            ctx.drawText(this.textRenderer,
                    Text.literal("Slot a Pasture Upgrade in the wand to unlock breeding pairs."),
                    frameX + 16, BUCKET_Y0 + 2, 0xFFCFC08A, false);
        }
        for (int b = 1; b <= nBuckets; b++) {
            int[] r = bucketRect[b - 1];
            drawZone(ctx, r, PAIR_BORDER);
            int filled = bucketCount(b, null);
            ctx.drawText(this.textRenderer, Text.literal("Pair " + b), r[0] + 5, r[1] + 4, TEXT, false);
            ctx.drawText(this.textRenderer, Text.literal(filled + "/2"), r[0] + r[2] - 22, r[1] + 4,
                    filled == 2 ? PAIR_BORDER : DIM, false);
        }

        long unpaired = roster.stream().filter(m -> !assign.containsKey(m.id())).count();
        drawZone(ctx, poolRect, POOL_BORDER);
        ctx.drawText(this.textRenderer, Text.literal("Unpaired (" + unpaired + ")"),
                poolRect[0] + 5, poolRect[1] + 4, DIM, false);

        for (MonEntry m : roster) {
            int[] bx = chipBox.get(m.id());
            if (bx == null) continue;
            drawChip(ctx, bx[0], bx[1], bx[2], bx[3], m.label(), assign.containsKey(m.id()), false);
        }
        if (dragging != null) {
            MonEntry m = monById(dragging);
            if (m != null) drawChip(ctx, dragX - dragOffX, dragY - dragOffY, BW - 8, CHIP_H, m.label(),
                    assign.containsKey(dragging), true);
        }

        if (flashTicks > 0 && flashMsg != null) {
            ctx.drawText(this.textRenderer, Text.literal(flashMsg), frameX + 150, belowBucketsY + 6, 0xFFCFA0A0, false);
        }

        if (nodeButton != null) {
            nodeButton.x = frameX + 16;
            nodeButton.y = belowBucketsY + 2;
            nodeButton.draw(ctx, this.textRenderer, mouseX, mouseY);
        }
    }

    private void drawZone(DrawContext ctx, int[] r, int border) {
        ctx.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1, border);
        ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], ZONE_BG);
    }

    private void drawChip(DrawContext ctx, int x, int y, int w, int h, String label, boolean paired, boolean drag) {
        int border = paired ? CHIP_PAIRED_BORDER : CHIP_BORDER;
        int fill = drag ? DRAG_BG : (paired ? CHIP_PAIRED : CHIP_BG);
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
        ctx.fill(x, y, x + w, y + h, fill);
        ctx.drawText(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(label, w - 6)), x + 3, y + 4, TEXT, false);
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (nodeButton != null && nodeButton.click(mx, my, button)) return true;
        UUID hit = chipAt(mx, my);
        if (hit != null) {
            if (button == 1) {              // right-click → unpair
                if (assign.remove(hit) != null) dirty = true;
                return true;
            }
            if (button == 0) {              // left-click → pick up
                int[] r = chipBox.get(hit);
                dragging = hit;
                dragOffX = (int) mx - r[0];
                dragOffY = (int) my - r[1];
                dragX = (int) mx;
                dragY = (int) my;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private UUID chipAt(double mx, double my) {
        for (MonEntry m : roster) {
            int[] r = chipBox.get(m.id());
            if (r != null && mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) return m.id();
        }
        return null;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            dragX = (int) mx;
            dragY = (int) my;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging != null) {
            dropAt(mx, my);
            dragging = null;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    private void dropAt(double mx, double my) {
        for (int b = 1; b <= nBuckets; b++) {
            int[] r = bucketRect[b - 1];
            if (r != null && inRect(mx, my, r)) {
                if (bucketCount(b, dragging) >= 2) flash("Pair " + b + " is full (max 2).");
                else { assign.put(dragging, b); dirty = true; }
                return;
            }
        }
        if (inRect(mx, my, poolRect)) {
            if (assign.remove(dragging) != null) dirty = true;
        }
        // else: dropped in dead space → leave the assignment unchanged
    }

    private static boolean inRect(double mx, double my, int[] r) {
        return mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
    }

    private void flash(String msg) {
        flashMsg = msg;
        flashTicks = 60;
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    private void persist() {
        if (persisted) return;       // never overwrite saved pairings twice / if unchanged
        persisted = true;
        if (!dirty) return;
        Map<UUID, Integer> out = new HashMap<>();
        assign.forEach((id, b) -> { if (b != null && b > 0) out.put(id, b); });
        try {
            ClientPlayNetworking.send(new SavePairingsPayload(pos, out));
        } catch (Throwable ignored) {
            // connection gone — nothing to persist to
        }
    }

    @Override
    public void close() {
        persist();
        try { ClientPlayNetworking.send(new OpenPasturePayload(pos)); } catch (Throwable ignored) {}
        super.close();   // server reopens the wand menu — Esc returns to the menu, not out to the world
    }

    @Override
    public void removed() {
        super.removed();
        persist();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
