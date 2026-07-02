package com.greenerpastures.client.notebook;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.notebook.net.NotebookInvSwapC2S;
import com.greenerpastures.notebook.net.NotebookPastureActionC2S;
import com.greenerpastures.notebook.net.NotebookPastureConfigS2C;
import com.greenerpastures.pasture.breeding.BetterPasture;
import com.greenerpastures.pasture.breeding.BreedingTier;
import com.greenerpastures.pasture.breeding.BreedingUpgradeItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.ItemStack;
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
    private static long curtainUntil = 0L;   // brief dark cover after a view-switch → hides the kept-alive browser's stale frame

    /** Start a short transition curtain — call on an air↔pasture / pasture↔pasture switch so the previous view
     *  (still in the kept-alive browser) doesn't flash before the new one paints. */
    public static void curtain() { curtainUntil = System.currentTimeMillis() + 220L; }

    /** Pre-warm the browser BEFORE the console is first opened (called from the client tick once MCEF + a world are
     *  ready) so the first open shows an already-painted page instead of a black/loading blip. Safe to call every
     *  tick: no-ops once created, or while MCEF is still downloading Chromium at the title screen. */
    public static void preload() {
        if (browser != null || !MCEF.isInitialized()) return;
        String url = consoleUrl();
        if (url == null) return;
        browser = MCEF.createBrowser(url, true);
        browser.useBrowserControls(false);
        var win = MinecraftClient.getInstance().getWindow();
        browser.resize(Math.max(1, win.getFramebufferWidth()), Math.max(1, win.getFramebufferHeight()));
        GpLog.i("console", "preloaded");
    }

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

        if (System.currentTimeMillis() < curtainUntil) {   // view-switch curtain: cover the stale frame until the new view paints
            context.fill(0, 0, width, height, 0xFF06080C);
            return;
        }
        drawInventory(context, mouseX, mouseY);   // native MC inventory (real icons) painted OVER the browser
    }

    // ── native MC inventory overlay (real item icons — the browser can't draw MC textures) ───────────────────

    private static final int SLOT = 18, INV_PAD = 8, HEADER = 16;
    private static int invX = -1, invY = -1;       // panel top-left (px); -1 = default to bottom-right on first layout
    private static boolean invCollapsed = false;    // header-only when true
    private static boolean invShown = true;         // E toggles the whole panel
    public static boolean browserInputFocused = false;   // set by DsBridge when a React text field has focus (don't steal 'e')
    private boolean draggingInv;
    private double grabX, grabY;
    private int heldSlot = -1;   // inventory slot currently "picked up" (its item follows the cursor), or -1

    private boolean pastureFocused() { return NotebookState.pastureConfig != null; }
    private int panelW() { return 9 * SLOT + INV_PAD * 2; }
    private int panelH() {
        if (invCollapsed) return HEADER;
        int kernelRow = pastureFocused() ? SLOT + 6 : 0;
        return HEADER + kernelRow + 4 * SLOT + 4 + INV_PAD;
    }
    private void placeInv() {
        if (invX < 0) { invX = width - panelW() - 10; invY = height - panelH() - 10; }
        invX = Math.max(0, Math.min(width - panelW(), invX));
        invY = Math.max(0, Math.min(height - panelH(), invY));
    }
    private int gridTop() { return invY + HEADER + (pastureFocused() ? SLOT + 6 : 0); }
    private int[] slotXY(int i) {
        int gx = invX + INV_PAD, gt = gridTop();
        if (i < 9) return new int[]{ gx + i * SLOT, gt + 3 * SLOT + 4 };   // hotbar row
        int idx = i - 9;
        return new int[]{ gx + (idx % 9) * SLOT, gt + (idx / 9) * SLOT };  // 3 main rows
    }
    private int[] kernelXY() { return new int[]{ invX + INV_PAD, invY + HEADER + 2 }; }   // top of the panel body
    private boolean inSlot(double mx, double my, int x, int y) { return mx >= x && mx < x + SLOT && my >= y && my < y + SLOT; }

    private void drawInventory(DrawContext ctx, int mouseX, int mouseY) {
        if (!invShown || client == null || client.player == null) return;
        placeInv();
        int pw = panelW(), ph = panelH();
        ctx.fill(invX, invY, invX + pw, invY + ph, 0xE60E131A);
        ctx.drawBorder(invX, invY, pw, ph, 0xFF2A3543);
        ctx.drawText(textRenderer, Text.literal("Inventory"), invX + INV_PAD, invY + 4, 0xFF8593A4, false);
        ctx.drawText(textRenderer, Text.literal(invCollapsed ? "▢" : "—"), invX + pw - 13, invY + 4, 0xFF8593A4, false);
        if (invCollapsed) return;

        ItemStack hover = null;
        NotebookPastureConfigS2C cfg = NotebookState.pastureConfig;
        if (cfg != null) {   // Kernel slot INSIDE the panel, top row, label to its right (stays on-screen)
            int[] k = kernelXY();
            ctx.fill(k[0], k[1], k[0] + SLOT - 1, k[1] + SLOT - 1, 0xFF0E131A);
            ctx.drawBorder(k[0], k[1], SLOT, SLOT, 0xFF5A8A6A);
            ItemStack kernel = kernelStack(cfg.tier());
            if (!kernel.isEmpty()) ctx.drawItem(kernel, k[0] + 1, k[1] + 1);
            ctx.drawText(textRenderer, Text.literal(kernel.isEmpty() ? "Kernel — click one" : "Kernel — click to pop"), k[0] + SLOT + 5, k[1] + 5, 0xFF8593A4, false);
            if (!kernel.isEmpty() && inSlot(mouseX, mouseY, k[0], k[1])) hover = kernel;
        }
        var main = client.player.getInventory().main;
        for (int i = 0; i < 36 && i < main.size(); i++) {
            int[] xy = slotXY(i);
            ctx.fill(xy[0], xy[1], xy[0] + SLOT - 1, xy[1] + SLOT - 1, 0xFF080B10);
            ItemStack s = main.get(i);
            if (!s.isEmpty() && i != heldSlot) { ctx.drawItem(s, xy[0] + 1, xy[1] + 1); ctx.drawItemInSlot(textRenderer, s, xy[0] + 1, xy[1] + 1); }
            if (!s.isEmpty() && i != heldSlot && inSlot(mouseX, mouseY, xy[0], xy[1])) hover = s;
        }
        if (heldSlot >= 0 && heldSlot < main.size() && !main.get(heldSlot).isEmpty()) {
            ctx.drawItem(main.get(heldSlot), mouseX - 8, mouseY - 8);   // picked-up item follows the cursor
        } else if (hover != null) {
            ctx.drawItemTooltip(textRenderer, hover, mouseX, mouseY);
        }
    }

    private ItemStack kernelStack(String tier) {
        if (tier == null || tier.isEmpty()) return ItemStack.EMPTY;
        try { return new ItemStack(BetterPasture.ITEMS.get(BreedingTier.valueOf(tier))); }
        catch (Exception e) { return ItemStack.EMPTY; }
    }

    /** Returns true if the click landed inside the panel (consume it — don't pass to the browser). Also starts a
     *  drag on the header and toggles collapse. */
    private boolean handleInventoryClick(double mx, double my) {
        if (!invShown) return false;
        int pw = panelW(), ph = panelH();
        if (!(mx >= invX && mx < invX + pw && my >= invY && my < invY + ph)) return false;
        if (my < invY + HEADER) {                         // header row
            if (mx >= invX + pw - 18) { invCollapsed = !invCollapsed; placeInv(); }   // collapse toggle
            else { draggingInv = true; grabX = mx - invX; grabY = my - invY; }         // drag
            return true;
        }
        if (invCollapsed) return true;
        var main = client.player.getInventory().main;
        NotebookPastureConfigS2C cfg = NotebookState.pastureConfig;
        if (cfg != null) {                                 // Kernel slot
            int[] k = kernelXY();
            if (inSlot(mx, my, k[0], k[1])) {
                boolean holdingKernel = heldSlot >= 0 && heldSlot < main.size() && main.get(heldSlot).getItem() instanceof BreedingUpgradeItem;
                if (holdingKernel) { sendKernel(cfg, heldSlot); heldSlot = -1; }        // slot the held Kernel
                else if (heldSlot < 0) sendKernel(cfg, -1);                             // empty-handed → first Kernel / pop
                return true;
            }
        }
        for (int i = 0; i < 36 && i < main.size(); i++) {  // inventory slot → pick up / place-swap
            int[] xy = slotXY(i);
            if (inSlot(mx, my, xy[0], xy[1])) {
                if (heldSlot < 0) { if (!main.get(i).isEmpty()) heldSlot = i; }          // pick up
                else { if (i != heldSlot) swapSlots(heldSlot, i); heldSlot = -1; }       // place / swap
                return true;
            }
        }
        return true;   // consume any click inside the panel
    }

    private void swapSlots(int a, int b) {
        if (client != null && client.getNetworkHandler() != null)
            ClientPlayNetworking.send(new NotebookInvSwapC2S(a, b));
    }

    private void sendKernel(NotebookPastureConfigS2C cfg, int srcSlot) {
        if (client != null && client.getNetworkHandler() != null)
            ClientPlayNetworking.send(new NotebookPastureActionC2S(cfg.pos(), NotebookPastureActionC2S.KERNEL, String.valueOf(srcSlot), java.util.Map.of()));
    }

    // --- input forwarding (coords scaled by GUI scale; focus the browser on interaction) ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleInventoryClick(mouseX, mouseY)) return true;   // clicks on the native inventory don't reach the browser
        if (browser != null) { browser.sendMousePress(px(mouseX), px(mouseY), button); browser.setFocus(true); }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingInv) { draggingInv = false; return true; }
        if (browser != null) { browser.sendMouseRelease(px(mouseX), px(mouseY), button); browser.setFocus(true); }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) browser.sendMouseMove(px(mouseX), px(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (draggingInv) {
            invX = (int) (mouseX - grabX);
            invY = (int) (mouseY - grabY);
            placeInv();
            return true;
        }
        if (browser != null) browser.sendMouseMove(px(mouseX), px(mouseY));   // browser drags (scrollbars, sliders)
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (browser != null) browser.sendMouseWheel(px(mouseX), px(mouseY), vertical, 0);
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_E && !browserInputFocused) {
            invShown = !invShown;   // E shows/hides the inventory panel (unless a browser text field is focused)
            return true;
        }
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
