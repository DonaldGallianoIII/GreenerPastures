package com.greenerpastures.arcade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * <b>TREELINE</b> - Game Corner cabinet #2 (Deuce's artifact, 2026-07-06): critters bolt off-screen,
 * the camera pans, and you sweep trees to find the Spark before your 10 sweeps run out. Decoys snitch
 * with an 8-way arrow toward the target; payout scales with sweeps left when you find her.
 *
 * <p><b>Minecraft-free and server-authoritative</b>: the artifact chose the hiding tree client-side -
 * on the live WS that's a Data printer. Here the server owns tree contents; the client only ever
 * learns what a sweep revealed. Tree LAYOUT (positions/scales) is not secret and ships on round start
 * so the client can draw the forest; {@code contains} never leaves this class unrevealed.
 *
 * <p>Payout = {@link #PAY_PER_SWEEP} × (sweeps remaining AFTER the finding sweep + 1) - find on the
 * first sweep for the max pot. Credits flow through the same neutral path as Voltorb Flip.
 */
public final class Treeline {
    private Treeline() {}

    public static final int CLICK_BUDGET = 10;
    public static final int DECOYS = 3;
    public static final int PAY_PER_SWEEP = 60;   // max pot: 60 × 10 = 600

    /** Rows: {yCenter%, scale%, count} - the artifact's fake-depth grid. */
    static final double[][] ROWS = { {22, 0.72, 7}, {42, 0.85, 6}, {63, 1.00, 7}, {84, 1.12, 6} };

    public static final class Tree {
        public final int id;
        public final double x, y, scale;
        public String contains;   // null | "target" | "decoy1".."decoyN" - SERVER-SECRET until searched
        public boolean searched;
        Tree(int id, double x, double y, double scale) { this.id = id; this.x = x; this.y = y; this.scale = scale; }
    }

    /** Meadow theater data - not secret, the client animates the scatter with it. */
    public record Critter(boolean isTarget, double startX, double startY, double exitY) {}

    public static final class Round {
        public final List<Tree> trees = new ArrayList<>();
        public final List<Critter> critters = new ArrayList<>();
        public int targetTreeId = -1;
        public int clicksLeft = CLICK_BUDGET;
        public boolean over = false;
        public boolean won = false;
        public long payout = 0;
        public Tree tree(int id) { return id >= 0 && id < trees.size() ? trees.get(id) : null; }
    }

    public static Round generate(Random rng) {
        Round r = new Round();
        int id = 0;
        for (double[] row : ROWS) {
            double gap = 100.0 / (row[2] + 1);
            for (int i = 1; i <= (int) row[2]; i++) {
                r.trees.add(new Tree(id++,
                        gap * i + lerp(rng, -gap * 0.28, gap * 0.28),
                        row[0] + lerp(rng, -3.5, 3.5),
                        row[1] * lerp(rng, 0.92, 1.08)));
            }
        }
        for (int i = 0; i <= DECOYS; i++) {
            double sx = lerp(rng, 15, 60), sy = lerp(rng, 25, 82);
            double exitY = Math.max(12, Math.min(90, sy + lerp(rng, -12, 12)));
            r.critters.add(new Critter(i == 0, sx, sy, exitY));
        }
        List<Tree> shuffled = new ArrayList<>(r.trees);
        Collections.shuffle(shuffled, rng);
        double targetExit = r.critters.get(0).exitY();
        Tree targetTree = shuffled.stream().filter(t -> Math.abs(t.y - targetExit) < 22).findFirst()
                .orElse(shuffled.get(0));
        targetTree.contains = "target";
        r.targetTreeId = targetTree.id;
        int placed = 0;
        for (int i = 1; i < r.critters.size() && placed < DECOYS; i++) {
            double exit = r.critters.get(i).exitY();
            Tree cand = shuffled.stream().filter(t -> t.contains == null && Math.abs(t.y - exit) < 30).findFirst()
                    .orElseGet(() -> shuffled.stream().filter(t -> t.contains == null).findFirst().orElse(null));
            if (cand != null) { cand.contains = "decoy" + i; placed++; }
        }
        return r;
    }

    public enum Outcome { INVALID, MISS, DECOY, FOUND, LOST }

    public record Sweep(Outcome outcome, int treeId, String arrow, long payout, int clicksLeft) {}

    /** Sweep a tree. FOUND ends the round with the pot; running out of sweeps returns LOST (the last
     *  sweep's own result folds in: a final-sweep DECOY/MISS still reports LOST with its arrow intact). */
    public static Sweep search(Round r, int treeId) {
        Tree t = r.tree(treeId);
        if (r.over || t == null || t.searched) return new Sweep(Outcome.INVALID, treeId, null, 0, r.clicksLeft);
        t.searched = true;
        r.clicksLeft--;
        if ("target".equals(t.contains)) {
            r.over = true;
            r.won = true;
            r.payout = (long) PAY_PER_SWEEP * (r.clicksLeft + 1);
            return new Sweep(Outcome.FOUND, treeId, null, r.payout, r.clicksLeft);
        }
        String arrow = null;
        Outcome o = Outcome.MISS;
        if (t.contains != null && t.contains.startsWith("decoy")) {
            Tree target = r.tree(r.targetTreeId);
            arrow = arrowToward(target.x - t.x, target.y - t.y);
            o = Outcome.DECOY;
        }
        if (r.clicksLeft <= 0) {
            r.over = true;
            return new Sweep(Outcome.LOST, treeId, arrow, 0, 0);
        }
        return new Sweep(o, treeId, arrow, 0, r.clicksLeft);
    }

    /** 8-way arrow from a screen-space delta (+y = down), snapped at 45° - the artifact's formula. */
    public static String arrowToward(double dx, double dy) {
        double deg = Math.toDegrees(Math.atan2(dy, dx));
        int idx = (int) Math.round(((deg + 360) % 360) / 45.0) % 8;
        return new String[]{"→", "↘", "↓", "↙", "←", "↖", "↑", "↗"}[idx];
    }

    private static double lerp(Random rng, double min, double max) {
        return min + rng.nextDouble() * (max - min);
    }
}
