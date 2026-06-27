package com.hydrogrid;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Type-in panel for the field corners (key N). Pairs with the V hotkey for in-world stamping. */
public class FieldScreen extends Screen {
    private TextFieldWidget ax, ay, az, bx, by, bz;
    private int cx, top;

    public FieldScreen() { super(Text.literal("HydroGrid Field")); }

    @Override
    protected void init() {
        cx = width / 2;
        top = height / 2 - 36;
        ax = coord(cx - 112, top, valOr(Field.cornerA, 'x'));
        ay = coord(cx - 57, top, valOr(Field.cornerA, 'y'));
        az = coord(cx - 2, top, valOr(Field.cornerA, 'z'));
        bx = coord(cx - 112, top + 34, valOr(Field.cornerB, 'x'));
        by = coord(cx - 57, top + 34, valOr(Field.cornerB, 'y'));
        bz = coord(cx - 2, top + 34, valOr(Field.cornerB, 'z'));
        addDrawableChild(ax); addDrawableChild(ay); addDrawableChild(az);
        addDrawableChild(bx); addDrawableChild(by); addDrawableChild(bz);

        addDrawableChild(ButtonWidget.builder(Text.literal("Compute"), b -> compute())
                .dimensions(cx + 56, top, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), b -> { Field.clear(); close(); })
                .dimensions(cx + 56, top + 34, 72, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(cx - 36, top + 78, 72, 20).build());
    }

    private TextFieldWidget coord(int x, int y, String val) {
        TextFieldWidget t = new TextFieldWidget(textRenderer, x, y, 51, 20, Text.empty());
        t.setMaxLength(8);
        t.setText(val);
        t.setTextPredicate(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{0,7}"));
        return t;
    }

    private static String valOr(BlockPos p, char c) {
        if (p == null) return "";
        return String.valueOf(c == 'x' ? p.getX() : c == 'y' ? p.getY() : p.getZ());
    }

    private void compute() {
        try {
            Field.setCorners(
                    new BlockPos(parse(ax), parse(ay), parse(az)),
                    new BlockPos(parse(bx), parse(by), parse(bz)));
            close();
        } catch (NumberFormatException ignored) {
            // a box is blank/invalid — leave the panel open
        }
    }

    private static int parse(TextFieldWidget t) { return Integer.parseInt(t.getText().trim()); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("HydroGrid — Field Fill"), cx, top - 28, 0xFF4FD0F0);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Corner A   X / Y / Z"), cx - 112, top - 11, 0xFFBBBBBB);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Corner B   X / Y / Z"), cx - 112, top + 23, 0xFFBBBBBB);
        String info = Field.isSet()
                ? Field.sizeX + " x " + Field.sizeZ + "   ·   " + Field.needed() + " water spots"
                  + (Field.capped ? " (capped at " + Field.MAX_SPOTS + ")" : "")
                : "enter two opposite corners, then Compute   (or press V on each corner in-world)";
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(info), cx, top + 60, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() { return false; }
}
