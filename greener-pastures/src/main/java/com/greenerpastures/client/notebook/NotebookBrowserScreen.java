package com.greenerpastures.client.notebook;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.greenerpastures.core.GpLog;
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The Notebook console rendered <b>in-game via MCEF</b> (embedded Chromium) — the very same React app that runs
 * in the dev browser, now painted inside Minecraft. Data flows over the WebSocket bridge
 * ({@code ws://127.0.0.1:25599}, {@code DsBridge}) exactly as it does for the browser; MCEF is only the render
 * surface ("two transports, one app"). Render + input mirror CinemaMod's {@code ExampleScreen} (mcef 2.1.6-1.21.1),
 * translated to Yarn. Instantiated only when the {@code mcef} mod is present (guarded in GreenerPasturesClient);
 * without it, the owo {@link NotebookScreen} is used instead.
 */
public class NotebookBrowserScreen extends Screen {
    /** file:// URL of the extracted single-file console (cached for the session). */
    private static String consoleUrl;

    private static MCEFBrowser browser;   // static → survives screen close: reopening is instant, no reload/blip

    public NotebookBrowserScreen() {
        super(Text.literal("Notebook"));
    }

    /**
     * MCEF's {@code mod://} scheme is broken on Fabric — its handler calls {@code getClassLoader().getResourceAsStream}
     * with a LEADING slash, which is always null — so we extract the bundled single-file console to a temp
     * {@code .html} and load it over {@code file://}. Single-file (viteSingleFile) also dodges file://'s ES-module block.
     */
    private static String consoleUrl() {
        if (consoleUrl != null) return consoleUrl;
        try (InputStream in = NotebookBrowserScreen.class.getClassLoader()
                .getResourceAsStream("assets/greenerpastures/html/index.html")) {
            if (in == null) { GpLog.w("console", "extract_missing"); return null; }
            Path tmp = Files.createTempFile("greenerpastures-console", ".html");
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            tmp.toFile().deleteOnExit();
            consoleUrl = tmp.toUri().toString();   // file:///...
            GpLog.i("console", "extracted", "url", consoleUrl);
            return consoleUrl;
        } catch (Exception e) {
            GpLog.w("console", "extract_err", "err", String.valueOf(e));
            return null;
        }
    }

    /** Create the browser once MCEF has finished initializing (Chromium download completes at the title screen). */
    private void tryCreate() {
        if (browser == null && MCEF.isInitialized()) {
            String url = consoleUrl();
            if (url == null) return;   // extraction failed — render() shows the hint text
            browser = MCEF.createBrowser(url, true);   // transparent = overlay-friendly
            browser.useBrowserControls(false);          // let Ctrl/Alt/F-keys reach React, don't let CEF hijack them
        }
        resizeBrowser();   // always fit the current window (it may have resized while the console was closed)
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
        // Keep the browser ALIVE (static) so reopening the console is instant and preserves its React state — no
        // black blip and no page reload each time. MCEF frees it on game shutdown. (Deuce, 2026-07-01)
        super.close();
    }

    /** Don't pause the world — the console must show LIVE data (breeding, Data balance) while it's open. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (browser == null) tryCreate();
        int texId = browser == null ? 0 : browser.getRenderer().getTextureID();
        if (texId <= 0) {
            // Not painted yet (first-ever open / MCEF still initializing): fill the app's dark bg so there's no
            // black/blur flash. The kept-alive browser means REOPENS skip this entirely (it's already painted).
            context.fill(0, 0, width, height, 0xFF06080C);
            if (browser == null)
                context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("Console initializing… (MCEF / Chromium)"), width / 2, height / 2, 0xFFAAAAAA);
            return;
        }

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
