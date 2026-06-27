package com.shedmon.shedscope;

/** A kind of block worth finding, with a display name and an ESP color. */
public final class Target {
    public enum Category { ORE, LOOT, DEEP_DARK }

    public final String name;
    public final int color; // 0xRRGGBB
    public final Category category;

    public Target(String name, int color, Category category) {
        this.name = name;
        this.color = color;
        this.category = category;
    }

    public float r() { return ((color >> 16) & 0xFF) / 255f; }
    public float g() { return ((color >> 8) & 0xFF) / 255f; }
    public float b() { return (color & 0xFF) / 255f; }
}
