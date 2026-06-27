package com.greenerpastures.egg.oracle.ui;

import net.minecraft.client.gui.DrawContext;

/** Central palette + a couple of panel helpers so the whole UI shares one look. */
public final class Theme {
    // palette (ARGB)
    public static final int BACKDROP     = 0xC00A0A0F;
    public static final int PANEL        = 0xFF15161C;
    public static final int PANEL_BORDER = 0xFF2B2E3A;
    public static final int CARD         = 0xFF1C1E27;
    public static final int CARD_BORDER  = 0xFF31343F;
    public static final int DIVIDER      = 0xFF2B2E3A;

    public static final int ACCENT       = 0xFF5BD1E6; // cyan
    public static final int ACCENT_DIM   = 0xFF223B41;
    public static final int GOLD         = 0xFFFFC94D; // hero numbers
    public static final int GOOD         = 0xFF54E0A0;

    public static final int TEXT         = 0xFFE7E9F0;
    public static final int TEXT_DIM     = 0xFF8B8F9D;
    public static final int TEXT_FAINT   = 0xFF5C606E;

    private Theme() {}

    /** Filled rounded-ish rectangle (1px corners notched) with a 1px border. */
    public static void panel(DrawContext ctx, int x, int y, int w, int h, int fill, int border) {
        int x2 = x + w, y2 = y + h;
        ctx.fill(x + 1, y, x2 - 1, y2, fill);       // main body
        ctx.fill(x, y + 1, x + 1, y2 - 1, fill);    // left edge (inset corners)
        ctx.fill(x2 - 1, y + 1, x2, y2 - 1, fill);  // right edge
        // border
        ctx.fill(x + 1, y, x2 - 1, y + 1, border);          // top
        ctx.fill(x + 1, y2 - 1, x2 - 1, y2, border);        // bottom
        ctx.fill(x, y + 1, x + 1, y2 - 1, border);          // left
        ctx.fill(x2 - 1, y + 1, x2, y2 - 1, border);        // right
    }

    public static void hline(DrawContext ctx, int x, int y, int w, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
    }

    public static void vline(DrawContext ctx, int x, int y, int h, int color) {
        ctx.fill(x, y, x + 1, y + h, color);
    }
}
