package com.greenerpastures.pasture.breeding.gui;

import com.greenerpastures.client.ui.GpButton;
import com.greenerpastures.pasture.breeding.net.ClaimOperatorPayload;
import com.greenerpastures.pasture.breeding.net.SaveNamePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Client GUI for the Pasture Wand — black/silver, laid out to Deuce's GreenerPastures-Layout.html:
 * heading + an "Arrange" button to the pairing board, a name field, a contiguous 9-wide upgrade slot
 * row (slot 0 = Pasture Upgrade, tinted green), an "Active Mons" summary, then the player inventory.
 * The name is saved to the shared server-side {@link PastureData} when the screen closes.
 */
public class PastureScreen extends HandledScreen<PastureMenu> {
    private static final int BORDER = 0xFF55555E;
    private static final int PANEL  = 0xFF121216;
    private static final int CELL_BORDER = 0xFF6E6E78;
    private static final int UPGRADE_BORDER = 0xFF5A8A6A;   // marks the Pasture Upgrade slot (slot 0)
    private static final int CELL  = 0xFF050507;
    private static final int TEXT  = 0xFFD6D6DE;
    private static final int SILVER = 0xFF9A9AA6;
    private static final int HINT  = 0xFFB9A36A;

    private TextFieldWidget nameField;
    private String initialName = "";
    private final List<GpButton> buttons = new ArrayList<>();

    public PastureScreen(PastureMenu handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
        this.backgroundWidth = 200;
        this.backgroundHeight = 184;   // design canvas 166 + a hotbar row the mock omits
    }

    @Override
    protected void init() {
        super.init();

        // Name field — design nameField (8, 24, 96, 14)
        this.initialName = this.handler.pastureName();
        this.nameField = new TextFieldWidget(this.textRenderer, this.x + 8, this.y + 24, 96, 14,
                Text.literal("Pasture name"));
        this.nameField.setMaxLength(64);
        this.nameField.setText(this.initialName);
        this.nameField.setPlaceholder(Text.literal("Name Here!"));
        addDrawableChild(this.nameField);

        // Arrange (bucket board) + Daemon (node graph) — our own GpButtons (no vanilla ButtonWidget):
        // drawn in render(), hit-tested in mouseClicked().
        buttons.clear();
        buttons.add(new GpButton(this.x + 128, this.y + 8, 64, 14, "Arrange", this::openArrangement));
        buttons.add(new GpButton(this.x + 128, this.y + 23, 64, 14, "Daemon", this::openDaemon));
        buttons.add(new GpButton(this.x + 128, this.y + 38, 64, 14, "Link", this::toggleClaim));
    }

    private void openArrangement() {
        MinecraftClient client = MinecraftClient.getInstance();
        PastureArrangementScreen board = new PastureArrangementScreen(
                this.handler.pasturePos, this.handler.pastureName(),
                this.handler.maxPairs(), this.handler.roster());
        // Cleanly close the server-side container first (commits the upgrade slots), THEN swap screens.
        // setScreen still fires this screen's removed(), which persists the name.
        if (client.player != null) client.player.closeHandledScreen();
        client.setScreen(board);
    }

    private void openDaemon() {
        MinecraftClient client = MinecraftClient.getInstance();
        DaemonScreen d = new DaemonScreen(this.handler.pasturePos, this.handler.pastureName(),
                this.handler.maxPairs(), this.handler.roster());
        // close the container first (same as openArrangement), then swap to the node graph
        if (client.player != null) client.player.closeHandledScreen();
        client.setScreen(d);
    }

    /** Claim/release this pasture as yours — links it to your Notebook (you collect its drops, eggs &
     *  outputs) via the operator-claim packet. The server applies {@code PastureClaim} and replies in chat. */
    private void toggleClaim() {
        try {
            ClientPlayNetworking.send(new ClaimOperatorPayload(this.handler.pasturePos));
        } catch (Throwable ignored) {
            // connection gone (world closing) — nothing to send to
        }
    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        ctx.fill(x - 1, y - 1, x + this.backgroundWidth + 1, y + this.backgroundHeight + 1, BORDER);
        ctx.fill(x, y, x + this.backgroundWidth, y + this.backgroundHeight, PANEL);
        for (int i = 0; i < this.handler.slots.size(); i++) {
            Slot s = this.handler.slots.get(i);
            if (!s.isEnabled()) continue;
            int px = x + s.x, py = y + s.y;
            ctx.fill(px - 1, py - 1, px + 17, py + 17, i == 0 ? UPGRADE_BORDER : CELL_BORDER);
            ctx.fill(px, py, px + 16, py + 16, CELL);
        }
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {
        ctx.drawText(this.textRenderer, Text.literal("Greener Pastures"), 8, 8, TEXT, false);

        int mons = this.handler.roster().size();
        ctx.drawText(this.textRenderer,
                Text.literal("Mons: " + mons + "   ·   Pairs: " + configuredPairs() + "/" + this.handler.maxPairs()),
                8, 64, SILVER, false);
        if (this.handler.unlockedSlots() == 0) {
            ctx.drawText(this.textRenderer, Text.literal("Slot a Kernel ↑"), 8, 76, HINT, false);
        }

        ctx.drawText(this.textRenderer, this.playerInventoryTitle, 8, 94, SILVER, false);
    }

    /** How many pair-buckets currently hold 2 mons (from the roster's saved bucket data). */
    private int configuredPairs() {
        int max = this.handler.maxPairs();
        if (max <= 0) return 0;
        int[] counts = new int[max + 1];
        for (MonEntry m : this.handler.roster()) {
            int b = m.bucket();
            if (b >= 1 && b <= max) counts[b]++;
        }
        int c = 0;
        for (int b = 1; b <= max; b++) if (counts[b] >= 2) c++;
        return c;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (GpButton b : buttons) b.draw(ctx, this.textRenderer, mouseX, mouseY);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (GpButton b : buttons) if (b.click(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While typing in the name field, swallow the inventory key (default 'e') so it edits the name
        // instead of closing the GUI. Escape still closes; the field handles its own control keys.
        if (this.nameField != null && this.nameField.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            if (this.nameField.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        if (this.nameField != null && !this.nameField.getText().equals(this.initialName)) {
            try {
                ClientPlayNetworking.send(new SaveNamePayload(this.handler.pasturePos, this.nameField.getText()));
            } catch (Throwable ignored) {
                // connection gone (world closing) — nothing to persist to
            }
        }
    }
}
