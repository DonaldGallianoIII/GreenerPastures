package com.greenerpastures.egg.oracle.ui.widgets;

import com.greenerpastures.egg.oracle.ui.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/** A pill toggle switch - green/knob-right when on, gray/knob-left when off. */
public class ToggleSwitch extends ClickableWidget {
    private boolean on;

    public ToggleSwitch(int x, int y, boolean on) {
        super(x, y, 30, 14, Text.empty());
        this.on = on;
    }

    public boolean isOn() { return on; }
    public void setOn(boolean v) { this.on = v; }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.on = !this.on;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int track = on ? Theme.GOOD : Theme.CARD_BORDER;
        Theme.panel(ctx, getX(), getY(), getWidth(), getHeight(), track, track);
        int knobX = on ? getX() + getWidth() - 12 : getX() + 2;
        ctx.fill(knobX, getY() + 2, knobX + 10, getY() + getHeight() - 2, 0xFFFFFFFF);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // visual-only toggle; nothing extra to narrate
    }
}
