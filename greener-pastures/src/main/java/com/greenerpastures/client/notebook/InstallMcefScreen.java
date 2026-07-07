package com.greenerpastures.client.notebook;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Shown instead of the console when MCEF is not installed (Deuce 2026-07-07: the owo fallback UI is retired -
 * MCEF is THE console). The mod still loads and every server-side system runs without MCEF; only the Notebook
 * item needs it, so this screen says exactly that and links the download. Client-only class, opened only from
 * the client entrypoint.
 */
public final class InstallMcefScreen extends Screen {
    private static final String MCEF_URL = "https://modrinth.com/mod/mcef";

    public InstallMcefScreen() {
        super(Text.literal("Greener Pastures needs MCEF"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2, cy = this.height / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("Get MCEF on Modrinth"),
                b -> Util.getOperatingSystem().open(MCEF_URL)).dimensions(cx - 100, cy + 12, 200, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"),
                b -> close()).dimensions(cx - 100, cy + 38, 200, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2, cy = this.height / 2;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("📓 The Notebook console needs MCEF"), cx, cy - 46, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("The console is a real browser rendered in-game;"), cx, cy - 28, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("MCEF (client-only mod) is the engine that draws it."), cx, cy - 18, 0xAAAAAA);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Install it, restart, and right-click your Notebook again."), cx, cy - 4, 0xAAAAAA);
    }
}
