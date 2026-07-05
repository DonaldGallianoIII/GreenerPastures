package com.greenerpastures.client.notebook;

import com.greenerpastures.notebook.net.NotebookActionC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Right-click-a-Kernel rename (QoL, Deuce 2026-07-04): a one-field screen so multi-kernel farms stay
 * legible ("Shiny Eevee Rig" instead of three identical Gold Kernels). Sends
 * {@link NotebookActionC2S#RENAME_HELD_KERNEL}; the server validates the held item + sets CUSTOM_NAME.
 */
public final class KernelRenameScreen extends Screen {
    private final String initial;
    private TextFieldWidget field;

    public KernelRenameScreen(ItemStack kernel) {
        super(Text.literal("Name this Kernel"));
        this.initial = kernel.contains(DataComponentTypes.CUSTOM_NAME) ? kernel.getName().getString() : "";
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;
        field = new TextFieldWidget(textRenderer, cx - 110, cy - 10, 220, 20, Text.literal("kernel name"));
        field.setMaxLength(32);
        field.setText(initial);
        addSelectableChild(field);
        setInitialFocus(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save())
                .dimensions(cx - 110, cy + 18, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(cx + 4, cy + 18, 106, 20).build());
    }

    private void save() {
        ClientPlayNetworking.send(new NotebookActionC2S(NotebookActionC2S.RENAME_HELD_KERNEL, field.getText(), 0));
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { save(); return true; }   // Enter saves
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 30, 0xFFFFFF);
        field.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("empty = clear the name").copy().formatted(net.minecraft.util.Formatting.DARK_GRAY),
                width / 2, height / 2 + 44, 0x808080);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
