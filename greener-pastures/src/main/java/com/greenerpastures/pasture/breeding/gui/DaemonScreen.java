package com.greenerpastures.pasture.breeding.gui;

import com.greenerpastures.client.ui.DaemonController;
import com.greenerpastures.client.ui.McGpCanvas;
import com.greenerpastures.client.ui.NotebookView;
import com.greenerpastures.pasture.breeding.net.OpenPasturePayload;
import com.greenerpastures.pasture.breeding.net.SavePairingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game adapter for the unified <b>Notebook</b>. State + interaction live in the Minecraft-free
 * {@link DaemonController} (shared with the desktop studio); this forwards Minecraft mouse/render
 * events and paints the whole interface via {@link NotebookView} (title bar + tabs + pasture name +
 * Kernel slot + the interactive Daemon canvas), so what we designed in the studio renders 1:1 here.
 *
 * <p>Pairings persist via {@link SavePairingsPayload}; Esc returns to the wand ({@link OpenPasturePayload}).
 * (The augment/filter/collection pipeline shown in the studio is design-preview until its backend
 * exists, so it's not rendered in-game yet — only the real, working units canvas is.)
 */
public class DaemonScreen extends Screen {
    private final BlockPos pos;
    private final DaemonController ctrl;
    private final String pastureName;
    private final String kernelTier;
    private boolean persisted;

    public DaemonScreen(BlockPos pos, String name, int maxPairs, List<MonEntry> roster) {
        super(Text.literal("Daemon"));
        this.pos = pos;
        this.pastureName = name == null ? "" : name;
        this.kernelTier = kernelFromPairs(maxPairs);
        List<DaemonController.Unit> units = new ArrayList<>();
        for (MonEntry m : roster) units.add(new DaemonController.Unit(m.id(), m.species(), m.label(), m.bucket()));
        this.ctrl = new DaemonController(units, maxPairs, name);
    }

    @Override
    protected void init() {
        super.init();
        ctrl.setViewport(this.width, this.height, NotebookView.CHROME_TOP);   // fit-to-viewport (re-runs on resize)
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctrl.tickFlash();
        NotebookView.Frame f = new NotebookView.Frame();
        f.pastureName = pastureName;
        f.kernelTier = kernelTier;
        f.threads = ctrl.pairCount() + "/" + ctrl.maxPairs() + " threads";
        NotebookView.paint(new McGpCanvas(ctx, this.textRenderer), ctrl.buildModel(), f, this.width, this.height);
    }

    @Override public boolean mouseClicked(double mx, double my, int b) {
        if (my < NotebookView.CHROME_TOP) return super.mouseClicked(mx, my, b);   // don't pan when clicking the chrome
        return ctrl.mouseDown(mx, my, b) || super.mouseClicked(mx, my, b);   // pass real button (0 L · 1 R · 2 M)
    }
    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        return ctrl.mouseDrag(mx, my) || super.mouseDragged(mx, my, b, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int b) {
        return ctrl.mouseUp(mx, my, b) || super.mouseReleased(mx, my, b);
    }
    @Override public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (my < NotebookView.CHROME_TOP) return super.mouseScrolled(mx, my, h, v);
        return ctrl.scroll(mx, my, v) || super.mouseScrolled(mx, my, h, v);
    }

    private void persist() {
        if (persisted) return;
        persisted = true;
        if (!ctrl.isDirty()) return;
        try { ClientPlayNetworking.send(new SavePairingsPayload(pos, ctrl.pairings())); } catch (Throwable ignored) {}
    }

    @Override
    public void close() {
        persist();
        try { ClientPlayNetworking.send(new OpenPasturePayload(pos)); } catch (Throwable ignored) {}
        super.close();
    }

    @Override public void removed() { super.removed(); persist(); }
    @Override public boolean shouldPause() { return false; }

    private static String kernelFromPairs(int p) {
        return switch (p) {
            case 2 -> "Copper Kernel";
            case 3 -> "Iron Kernel";
            case 4 -> "Gold Kernel";
            case 5 -> "Diamond Kernel";
            case 6 -> "Netherite Kernel";
            case 8 -> "Greener Kernel";
            default -> p <= 0 ? "no Kernel" : p + " pairs";
        };
    }
}
