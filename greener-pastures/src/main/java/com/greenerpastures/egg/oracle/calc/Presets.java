package com.greenerpastures.egg.oracle.calc;

import java.util.List;

/** Built-in server profiles. Applying one sets the server-side odds fields only;
 *  the player's pasture inputs (pairs, egg time, horizon) are left untouched. */
public final class Presets {
    public record Preset(String name, double baseRate, double masudaMult, boolean diffOT) {}

    public static final List<Preset> ALL = List.of(
            new Preset("Vanilla 1/8192", 8192, 1, false),
            new Preset("Cobbreeding",     8192, 4, true),
            new Preset("Custom",          0,    0, false) // sentinel: don't overwrite fields
    );

    private Presets() {}

    public static void apply(Preset preset, Profile p) {
        p.presetName = preset.name();
        if ("Custom".equals(preset.name())) return; // keep current values
        p.baseRate = preset.baseRate();
        p.masudaMult = preset.masudaMult();
        p.diffOT = preset.diffOT();
    }
}
