package com.greenerpastures.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Greener Pastures' own button - drawn and hit-tested entirely by us (a plain rect + centered label),
 * deliberately NOT a vanilla {@code ButtonWidget}/{@code Drawable}/{@code Element}. We avoid the
 * vanilla widget machinery because rendering it dragged the menu-blur path over our custom canvases.
 *
 * <p>Usage: hold these in a list on a screen; call {@link #draw} from {@code render} and {@link #click}
 * from {@code mouseClicked}. No registration, no focus, no blur.
 */
public class GpButton {
    public int x, y, w, h;
    public String label;
    public Runnable onClick;
    public boolean enabled = true;

    private final int bg, hoverBg, border, hoverBorder, textColor, disabledColor;

    public GpButton(int x, int y, int w, int h, String label, Runnable onClick) {
        this(x, y, w, h, label, onClick,
                0xFF1D2530, 0xFF2A3543, 0xFF3A4960, 0xFF5A6B86, 0xFFE7EEF6, 0xFF566273);
    }

    public GpButton(int x, int y, int w, int h, String label, Runnable onClick,
                    int bg, int hoverBg, int border, int hoverBorder, int textColor, int disabledColor) {
        this.x = x; this.y = y; this.w = w; this.h = h; this.label = label; this.onClick = onClick;
        this.bg = bg; this.hoverBg = hoverBg; this.border = border; this.hoverBorder = hoverBorder;
        this.textColor = textColor; this.disabledColor = disabledColor;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void draw(DrawContext ctx, TextRenderer tr, int mouseX, int mouseY) {
        boolean hover = enabled && contains(mouseX, mouseY);
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, hover ? hoverBorder : border);
        ctx.fill(x, y, x + w, y + h, hover ? hoverBg : bg);
        int tw = tr.getWidth(label);
        int tx = x + Math.max(2, (w - tw) / 2);
        int ty = y + (h - 8) / 2;
        ctx.drawText(tr, Text.literal(label), tx, ty, enabled ? textColor : disabledColor, false);
    }

    /** Call from mouseClicked; runs the action and returns true if this button consumed the click. */
    public boolean click(double mx, double my, int button) {
        if (button == 0 && enabled && contains(mx, my)) {
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }
}
