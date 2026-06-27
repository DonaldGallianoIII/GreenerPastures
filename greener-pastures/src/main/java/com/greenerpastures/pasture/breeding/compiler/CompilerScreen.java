package com.greenerpastures.pasture.breeding.compiler;

import com.greenerpastures.client.ui.GpButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * The Compiler bench GUI — the "shape and look" from Deuce's Notebook mockup, on the repo's
 * ground-truth mechanics: Kernel + augment → ▸ Compile → augmented Kernel, with a pip-install
 * ceremony (progress bar + transcript). The augmented-Kernel "result" is a client-side preview;
 * pressing Compile plays the animation, then fires the vanilla button route so the SERVER does the
 * real merge ({@link CompilerMenu#onButtonClick}).
 */
public class CompilerScreen extends HandledScreen<CompilerMenu> {
    private static final int BORDER = 0xFF2A3543;
    private static final int PANEL  = 0xFF12171F;
    private static final int CELL_BORDER = 0xFF3A4960;
    private static final int OUT_BORDER  = 0xFF2E5A47;
    private static final int CELL  = 0xFF0E131A;
    private static final int TEXT  = 0xFFE7EEF6;
    private static final int MUTED = 0xFF8593A4;
    private static final int DIM   = 0xFF566273;
    private static final int DATA  = 0xFF5CC8FF;
    private static final int OK    = 0xFF4FD6A0;

    private static final int COMPILE_TICKS = 24;

    private GpButton compileBtn;
    private int compileTick = -1;        // -1 = idle; 0..COMPILE_TICKS = running
    private boolean lastSuccess = false;
    private String pkg = "augment";      // remembered across the consume so the log can show it

    public CompilerScreen(CompilerMenu handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 230;
        this.backgroundHeight = 208;
    }

    @Override
    protected void init() {
        super.init();
        this.compileBtn = new GpButton(this.x + 58, this.y + 72, 110, 16, "▸ Compile", this::startCompile);
    }

    private void startCompile() {
        if (compileTick >= 0) return;
        ItemStack a = this.handler.augment();
        if (this.handler.preview().isEmpty()) return;     // nothing valid to compile
        if (a.getItem() instanceof AugmentItem ai) pkg = ai.type.pkg();
        compileTick = 0;
        lastSuccess = false;
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        if (this.compileBtn != null) {
            this.compileBtn.enabled = compileTick < 0 && !this.handler.preview().isEmpty();
            this.compileBtn.label = compileTick >= 0 ? "Compiling…" : "▸ Compile";
        }
        if (compileTick >= 0) {
            compileTick++;
            if (compileTick >= COMPILE_TICKS) {
                if (this.client != null && this.client.interactionManager != null) {
                    this.client.interactionManager.clickButton(this.handler.syncId, CompilerMenu.COMPILE_BUTTON);
                }
                compileTick = -1;
                lastSuccess = true;
            }
        }
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        ctx.fill(x - 1, y - 1, x + backgroundWidth + 1, y + backgroundHeight + 1, BORDER);
        ctx.fill(x, y, x + backgroundWidth, y + backgroundHeight, PANEL);
        for (Slot s : this.handler.slots) {
            int px = x + s.x, py = y + s.y;
            ctx.fill(px - 1, py - 1, px + 17, py + 17, CELL_BORDER);
            ctx.fill(px, py, px + 16, py + 16, CELL);
        }
        // result (preview) cell — not a real slot, tinted green
        int ox = x + CompilerMenu.PREVIEW_X, oy = y + CompilerMenu.BENCH_Y;
        ctx.fill(ox - 1, oy - 1, ox + 17, oy + 17, OUT_BORDER);
        ctx.fill(ox, oy, ox + 16, oy + 16, CELL);
        // operators between bench cells
        ctx.drawText(this.textRenderer, Text.literal("+"), x + 80, y + CompilerMenu.BENCH_Y + 4, MUTED, false);
        ctx.drawText(this.textRenderer, Text.literal("→"), x + 127, y + CompilerMenu.BENCH_Y + 4, MUTED, false);
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(this.textRenderer, Text.literal("Compiler"), 8, 6, TEXT, false);
        ctx.drawText(this.textRenderer, Text.literal("pasture.compile() · Kernel + augment → augmented Kernel"),
                8, 17, DIM, false);

        // progress bar
        int bx = 58, by = 92, bw = 110;
        ctx.fill(bx, by, bx + bw, by + 3, 0xFF0B0E13);
        if (compileTick >= 0) {
            int w = (int) (bw * (compileTick / (float) COMPILE_TICKS));
            ctx.fill(bx, by, bx + w, by + 3, DATA);
        } else if (lastSuccess) {
            ctx.fill(bx, by, bx + bw, by + 3, OK);
        }

        // pip-install transcript (two lines)
        String l1, l2; int c1, c2;
        if (compileTick >= 0) {
            l1 = "$ compile " + pkg + " → kernel"; c1 = DATA;
            if (compileTick < 4)       { l2 = "Collecting " + pkg; c2 = MUTED; }
            else if (compileTick < 10) { l2 = "Building wheel for shiny …"; c2 = DIM; }
            else if (compileTick < 16) { l2 = "Installing collected packages …"; c2 = DIM; }
            else                       { l2 = "Linking onto kernel …"; c2 = DIM; }
        } else if (lastSuccess) {
            l1 = "$ compile " + pkg + " → kernel"; c1 = DATA;
            l2 = "Successfully compiled " + pkg + " ✓"; c2 = OK;
        } else {
            l1 = "$ kernel ready"; c1 = DIM;
            l2 = "slot a Kernel + a Shiny Augment, then ▸ Compile"; c2 = DIM;
        }
        ctx.drawText(this.textRenderer, Text.literal(l1), 8, 98, c1, false);
        ctx.drawText(this.textRenderer, Text.literal(l2), 8, 107, c2, false);

        ctx.drawText(this.textRenderer, this.playerInventoryTitle, 34, 114, MUTED, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);   // background + slots
        if (this.compileBtn != null) this.compileBtn.draw(ctx, this.textRenderer, mouseX, mouseY);
        ItemStack preview = this.handler.preview();
        int ox = this.x + CompilerMenu.PREVIEW_X, oy = this.y + CompilerMenu.BENCH_Y;
        if (!preview.isEmpty()) ctx.drawItem(preview, ox, oy);

        if (!preview.isEmpty() && inRect(mouseX, mouseY, ox, oy, 16, 16)) {
            ctx.drawItemTooltip(this.textRenderer, preview, mouseX, mouseY);
        } else {
            this.drawMouseoverTooltip(ctx, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (compileBtn != null && compileBtn.click(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
