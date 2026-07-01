package com.greenerpastures.client.notebook;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;

/**
 * The Notebook console rendered <b>in-game via MCEF</b> (embedded Chromium) — the very same React app that runs
 * in the dev browser, now painted inside Minecraft. Data flows over the WebSocket bridge
 * ({@code ws://127.0.0.1:25599}, {@code DsBridge}) exactly as it does for the browser; MCEF is only the render
 * surface ("two transports, one app"). Render + input mirror CinemaMod's {@code ExampleScreen} (mcef 2.1.6-1.21.1),
 * translated to Yarn. Instantiated only when the {@code mcef} mod is present (guarded in GreenerPasturesClient);
 * without it, the owo {@link NotebookScreen} is used instead.
 */
public class NotebookBrowserScreen extends Screen {
    /** {@code mod://<id>/<path>} → classpath {@code /assets/<id>/html/<path>} (MCEF's built-in scheme; lowercased). */
    private static final String URL = "mod://greenerpastures/index.html";

    private MCEFBrowser browser;

    public NotebookBrowserScreen() {
        super(Text.literal("Notebook"));
    }

    /** Create the browser once MCEF has finished initializing (Chromium download completes at the title screen). */
    private void tryCreate() {
        if (browser == null && MCEF.isInitialized()) {
            browser = MCEF.createBrowser(URL, true);   // transparent = overlay-friendly
            browser.useBrowserControls(false);          // let Ctrl/Alt/F-keys reach React, don't let CEF hijack them
            resizeBrowser();
        }
    }

    @Override
    protected void init() {
        super.init();
        tryCreate();
    }

    private int px(double logical) {
        return (int) (logical * client.getWindow().getScaleFactor());
    }

    private void resizeBrowser() {
        if (browser != null && width > 0 && height > 0) {
            browser.resize(px(width), px(height));
        }
    }

    @Override
    public void resize(MinecraftClient mc, int w, int h) {
        super.resize(mc, w, h);
        resizeBrowser();
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        super.close();
    }

    /** Don't pause the world — the console must show LIVE data (breeding, Data balance) while it's open. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        if (browser == null) {
            tryCreate();
            if (browser == null) {
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Console initializing… (MCEF / Chromium)"), width / 2, height / 2, 0xFFAAAAAA);
                return;
            }
        }
        int texId = browser.getRenderer().getTextureID();
        if (texId <= 0) return;   // texture id is 0 until the first paint — guard the draw

        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, texId);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        // full-screen quad, V flipped (CEF's BGRA buffer is top-down)
        buffer.vertex(0f, (float) height, 0f).texture(0f, 1f).color(255, 255, 255, 255);
        buffer.vertex((float) width, (float) height, 0f).texture(1f, 1f).color(255, 255, 255, 255);
        buffer.vertex((float) width, 0f, 0f).texture(1f, 0f).color(255, 255, 255, 255);
        buffer.vertex(0f, 0f, 0f).texture(0f, 0f).color(255, 255, 255, 255);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.enableDepthTest();
    }

    // --- input forwarding (coords scaled by GUI scale; focus the browser on interaction) ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (browser != null) { browser.sendMousePress(px(mouseX), px(mouseY), button); browser.setFocus(true); }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (browser != null) { browser.sendMouseRelease(px(mouseX), px(mouseY), button); browser.setFocus(true); }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) browser.sendMouseMove(px(mouseX), px(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (browser != null) browser.sendMouseWheel(px(mouseX), px(mouseY), vertical, 0);
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (browser != null) { browser.sendKeyPress(keyCode, scanCode, modifiers); browser.setFocus(true); }
        return super.keyPressed(keyCode, scanCode, modifiers);   // Esc still closes the screen
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (browser != null) { browser.sendKeyRelease(keyCode, scanCode, modifiers); browser.setFocus(true); }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (chr == '\u0000') return false;
        if (browser != null) { browser.sendKeyTyped(chr, modifiers); browser.setFocus(true); }
        return super.charTyped(chr, modifiers);
    }
}
