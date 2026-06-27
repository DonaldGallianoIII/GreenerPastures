package com.shedmon.shedscope;

/** Runtime toggles, flipped by keybinds (see {@link Keys}). */
public final class State {
    public static boolean enabled = true;
    public static boolean ores = true;
    public static boolean loot = true;
    public static boolean deepDark = true;

    private State() {}

    public static boolean isEnabled(Target.Category c) {
        return switch (c) {
            case ORE -> ores;
            case LOOT -> loot;
            case DEEP_DARK -> deepDark;
        };
    }
}
