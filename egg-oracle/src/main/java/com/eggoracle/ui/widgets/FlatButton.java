package com.eggoracle.ui.widgets;

import com.eggoracle.ui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** A flat, accent-bordered button — none of the vanilla gray-bevel look. */
public class FlatButton extends ButtonWidget {
    public FlatButton(int x, int y, int w, int h, Text msg, PressAction action) {
        super(x, y, w, h, msg, action, DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        boolean hov = this.isHovered();
        int fill = hov ? Theme.ACCENT_DIM : Theme.CARD;
        int border = hov ? Theme.ACCENT : Theme.CARD_BORDER;
        Theme.panel(ctx, getX(), getY(), getWidth(), getHeight(), fill, border);
        int tc = hov ? Theme.TEXT : Theme.TEXT_DIM;
        MinecraftClient mc = MinecraftClient.getInstance();
        ctx.drawCenteredTextWithShadow(mc.textRenderer, getMessage(),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, tc);
    }
}
