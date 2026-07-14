package com.greenerpastures.display;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Statue pose math - yaw wrap, pixel nudges + clamps, scale preset cycling, NBT-sanitizing constructor. */
class StatueTransformTest {

    @Test
    void defaultIsCenteredLifeSize() {
        assertEquals(0f, StatueTransform.DEFAULT.yawDegrees());
        assertEquals(1.0, StatueTransform.DEFAULT.scale());
        assertEquals(0.0, StatueTransform.DEFAULT.offsetX());
        assertEquals(0.0, StatueTransform.DEFAULT.offsetY());
        assertEquals(0.0, StatueTransform.DEFAULT.offsetZ());
    }

    @Test
    void rotateStepsAndWraps() {
        StatueTransform t = StatueTransform.DEFAULT.rotated();
        assertEquals(22.5f, t.yawDegrees(), "one tap = one armor-stand step");

        for (int i = 1; i < StatueTransform.YAW_STEPS; i++) t = t.rotated();
        assertEquals(0f, t.yawDegrees(), "16 taps come back around");
        assertEquals(StatueTransform.DEFAULT, t, "full circle changes nothing else");
    }

    @Test
    void nudgeMovesOnePixel() {
        StatueTransform t = StatueTransform.DEFAULT.nudged(StatueTransform.Axis.X, 1);
        assertEquals(1.0 / 16.0, t.offsetX());
        assertEquals(0.0, t.offsetZ(), "other axes untouched");

        assertEquals(-1.0 / 16.0, StatueTransform.DEFAULT.nudged(StatueTransform.Axis.Z, -1).offsetZ());
        assertEquals(1.0 / 16.0, StatueTransform.DEFAULT.nudged(StatueTransform.Axis.Y, 5).offsetY(),
                "direction is sign-only");
    }

    @Test
    void nudgeClampsAtPlinthEdgeAndPedestalHeight() {
        StatueTransform t = StatueTransform.DEFAULT;
        for (int i = 0; i < 40; i++) t = t.nudged(StatueTransform.Axis.X, 1);
        assertEquals(StatueTransform.MAX_HORIZONTAL, t.offsetX(), "horizontal stops over the plinth");

        for (int i = 0; i < 40; i++) t = t.nudged(StatueTransform.Axis.Y, -1);
        assertEquals(-StatueTransform.MAX_VERTICAL, t.offsetY(), "vertical stops at pedestal range");
    }

    @Test
    void scaleCyclesPresetsInOrderAndWraps() {
        StatueTransform t = StatueTransform.DEFAULT;   // 1.0×
        double[] expected = {1.5, 2.0, 3.0, 0.5, 1.0};
        for (double want : expected) {
            t = t.scaleCycled(3.0);
            assertEquals(want, t.scale());
        }
    }

    @Test
    void scaleCycleHonorsServerClamp() {
        StatueTransform t = new StatueTransform(0, 0, 0, 0, 3);   // 2.0×
        assertEquals(0.5, t.scaleCycled(2.0).scale(), "3× skipped under statueMaxScale=2.0, wraps to smallest");

        StatueTransform stuck = StatueTransform.DEFAULT;
        assertEquals(stuck, stuck.scaleCycled(0.1), "clamp below every preset changes nothing");
    }

    @Test
    void constructorSanitizesHostileNbt() {
        StatueTransform t = new StatueTransform(37, 9.0, -55.0, -9.0, 99);
        assertEquals(37 % 16 * 22.5f, t.yawDegrees(), "yaw wraps via floorMod");
        assertEquals(StatueTransform.MAX_HORIZONTAL, t.offsetX());
        assertEquals(-StatueTransform.MAX_VERTICAL, t.offsetY());
        assertEquals(-StatueTransform.MAX_HORIZONTAL, t.offsetZ());
        assertEquals(3.0, t.scale(), "scale index clamps to the largest preset");

        assertEquals(15, new StatueTransform(-1, 0, 0, 0, -5).yawStep(), "negative yaw wraps up, not crash");
        assertEquals(0.5, new StatueTransform(-1, 0, 0, 0, -5).scale(), "negative index clamps to smallest");
    }
}
