package com.greenerpastures.display;

/**
 * The <b>Specimen Statue</b>'s pose math (Display Suite - Deuce, 2026-07-14): yaw in 22.5° steps (16
 * facings, armor-stand cadence), XZ offset in 1/16-block nudges kept over the plinth, vertical ±1.0 for
 * pedestal centering, preset scale cycling clamped by the server's {@code statueMaxScale}. Pure +
 * immutable so the whole control surface is headless-testable; the BlockEntity just stores five numbers.
 *
 * <p>The canonical constructor SANITIZES - whatever comes out of NBT (hand-edited, older version,
 * corrupt) lands back in range, so the renderer can trust any instance it is handed.
 */
public record StatueTransform(int yawStep, double offsetX, double offsetY, double offsetZ, int scaleIndex) {

    public static final int YAW_STEPS = 16;               // 22.5° each - armor-stand cadence
    public static final float YAW_STEP_DEGREES = 360f / YAW_STEPS;
    public static final double NUDGE = 1.0 / 16.0;        // one pixel per tap
    public static final double MAX_HORIZONTAL = 0.5;      // stays over a 1-block plinth (spec §5 Q6, v1 call)
    public static final double MAX_VERTICAL = 1.0;
    public static final double[] SCALE_PRESETS = {0.5, 1.0, 1.5, 2.0, 3.0};

    /** Fresh statue: centered, unrotated, life-size. */
    public static final StatueTransform DEFAULT = new StatueTransform(0, 0.0, 0.0, 0.0, 1);

    public StatueTransform {
        yawStep = Math.floorMod(yawStep, YAW_STEPS);
        offsetX = clamp(offsetX, MAX_HORIZONTAL);
        offsetY = clamp(offsetY, MAX_VERTICAL);
        offsetZ = clamp(offsetZ, MAX_HORIZONTAL);
        scaleIndex = Math.max(0, Math.min(SCALE_PRESETS.length - 1, scaleIndex));
    }

    public float yawDegrees() {
        return yawStep * YAW_STEP_DEGREES;
    }

    public double scale() {
        return SCALE_PRESETS[scaleIndex];
    }

    /** One rotate tap: next 22.5° facing, wrapping past north. */
    public StatueTransform rotated() {
        return new StatueTransform(yawStep + 1, offsetX, offsetY, offsetZ, scaleIndex);
    }

    public enum Axis { X, Y, Z }

    /** One offset tap: 1/16 block along {@code axis}; {@code direction} sign only. Clamps silently at range. */
    public StatueTransform nudged(Axis axis, int direction) {
        double d = NUDGE * Math.signum(direction);
        return switch (axis) {
            case X -> new StatueTransform(yawStep, offsetX + d, offsetY, offsetZ, scaleIndex);
            case Y -> new StatueTransform(yawStep, offsetX, offsetY + d, offsetZ, scaleIndex);
            case Z -> new StatueTransform(yawStep, offsetX, offsetY, offsetZ + d, scaleIndex);
        };
    }

    /**
     * One scale tap: next preset, skipping any above the server's {@code statueMaxScale}, wrapping back
     * to the smallest. A pathological clamp below every preset leaves the transform unchanged.
     */
    public StatueTransform scaleCycled(double maxScale) {
        int next = scaleIndex;
        for (int i = 0; i < SCALE_PRESETS.length; i++) {
            next = (next + 1) % SCALE_PRESETS.length;
            if (SCALE_PRESETS[next] <= maxScale) {
                return new StatueTransform(yawStep, offsetX, offsetY, offsetZ, next);
            }
        }
        return this;
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }
}
