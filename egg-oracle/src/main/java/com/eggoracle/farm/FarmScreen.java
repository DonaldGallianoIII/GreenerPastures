package com.eggoracle.farm;

import com.eggoracle.calc.OddsEngine;
import com.eggoracle.calc.Profile;
import com.eggoracle.ui.Theme;
import com.eggoracle.ui.widgets.FlatButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Farm dashboard: editable pasture roster + live per-species shiny breakdown. */
public class FarmScreen extends Screen {
    private static final int PW = 404, PH = 236;
    private static final int ROW_H = 15, VISIBLE = 9;

    // column offsets relative to px
    private static final int C_SP = 14, C_MINUS = 146, C_COUNT = 162, C_PLUS = 184,
            C_PCT = 250, C_RATE = 340, C_REMOVE = PW - 24;

    private final Profile profile;
    private final PastureRoster roster;
    private final Screen parent;
    private int px, py, listTop, scroll;
    private TextFieldWidget addField, addCount;

    public FarmScreen(Profile profile, PastureRoster roster, Screen parent) {
        super(Text.literal("EggOracle Farm"));
        this.profile = profile;
        this.roster = roster;
        this.parent = parent;
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        listTop = py + 44;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        addField = new TextFieldWidget(textRenderer, px + C_SP, py + PH - 24, 124, 14, Text.empty());
        addField.setMaxLength(20);
        addCount = new TextFieldWidget(textRenderer, px + 142, py + PH - 24, 30, 14, Text.empty());
        addCount.setText("1");
        addCount.setMaxLength(3);
        addCount.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{0,3}"));
        addDrawableChild(addField);
        addDrawableChild(addCount);
        addDrawableChild(new FlatButton(px + 176, py + PH - 25, 40, 16, Text.literal("Add"), b -> doAdd()));
        addDrawableChild(new FlatButton(px + PW - 92, py + 5, 64, 14, Text.literal("< Calc"), b -> back()));
        addDrawableChild(new FlatButton(px + PW - 22, py + 5, 16, 14, Text.literal("x"), b -> close()));
    }

    private int maxScroll() { return Math.max(0, roster.entries.size() - VISIBLE); }

    private void doAdd() {
        int c = 1;
        try { c = Integer.parseInt(addCount.getText()); } catch (Exception ignored) {}
        roster.add(addField.getText(), c);
        addField.setText("");
        addCount.setText("1");
        scroll = maxScroll();
    }

    private void back() {
        if (client != null) client.setScreen(parent);
        else close();
    }

    // ---- farm math ----
    private double pPerEgg() { return OddsEngine.pPerEgg(profile); }
    private double eggsDayPerPasture() { return profile.eggTimeMin > 0 ? 1440.0 / profile.eggTimeMin : 0; }
    private double speciesShiniesDay(int count) { return count * eggsDayPerPasture() * pPerEgg(); }

    // ---- input ----
    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (my >= listTop && my < listTop + VISIBLE * ROW_H && roster.entries.size() > VISIBLE) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(vAmt)));
            return true;
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        if (button == 0 && my >= listTop && my < listTop + VISIBLE * ROW_H) {
            int idx = scroll + (my - listTop) / ROW_H;
            if (idx >= 0 && idx < roster.entries.size()) {
                int d = hasShiftDown() ? 10 : 1;
                PastureRoster.Entry e = roster.entries.get(idx);
                if (inZone(mx, C_MINUS)) { e.count = Math.max(0, e.count - d); roster.save(); return true; }
                if (inZone(mx, C_PLUS))  { e.count += d; roster.save(); return true; }
                if (inZone(mx, C_REMOVE)) { roster.remove(idx); scroll = Math.min(scroll, maxScroll()); return true; }
                if (mx >= px + C_SP && mx < px + C_MINUS) { Finder.toggle(e.species); return true; }
            }
        }
        return super.mouseClicked(mxd, myd, button);
    }

    private boolean inZone(int mx, int zx) { return mx >= px + zx && mx < px + zx + 12; }

    // ---- render ----
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, Theme.BACKDROP);
        Theme.panel(ctx, px, py, PW, PH, Theme.PANEL, Theme.PANEL_BORDER);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        ctx.fill(px + 12, py + 8, px + 16, py + 12, Theme.GOLD);
        ctx.drawTextWithShadow(textRenderer, Text.literal("EggOracle - Farm"), px + 22, py + 7, Theme.TEXT);
        Theme.hline(ctx, px + 1, py + 22, PW - 2, Theme.DIVIDER);

        int hy = py + 30;
        ctx.drawTextWithShadow(textRenderer, Text.literal("SPECIES"), px + C_SP, hy, Theme.TEXT_FAINT);
        ctx.drawTextWithShadow(textRenderer, Text.literal("PASTURES"), px + C_MINUS - 2, hy, Theme.TEXT_FAINT);
        right(ctx, "% SHINY", px + C_PCT, hy, Theme.TEXT_FAINT);
        right(ctx, "SHINY/DAY", px + C_RATE, hy, Theme.TEXT_FAINT);
        Theme.hline(ctx, px + C_SP, py + 41, PW - 2 * C_SP, Theme.DIVIDER);

        int total = roster.total();
        for (int i = 0; i < VISIBLE; i++) {
            int idx = scroll + i;
            if (idx >= roster.entries.size()) break;
            PastureRoster.Entry e = roster.entries.get(idx);
            int y = listTop + i * ROW_H;
            if (mouseY >= y && mouseY < y + ROW_H)
                ctx.fill(px + C_SP - 4, y - 1, px + PW - 10, y + ROW_H - 2, Theme.CARD);

            boolean finding = Finder.active() && Finder.target.equalsIgnoreCase(e.species);
            ctx.drawTextWithShadow(textRenderer, Text.literal((finding ? "> " : "") + cap(e.species)),
                    px + C_SP, y + 2, finding ? Theme.ACCENT : Theme.TEXT);
            glyph(ctx, "-", px + C_MINUS, y, mouseX, mouseY);
            String cnt = String.valueOf(e.count);
            ctx.drawTextWithShadow(textRenderer, Text.literal(cnt),
                    px + C_COUNT + (16 - textRenderer.getWidth(cnt)) / 2, y + 2, Theme.TEXT);
            glyph(ctx, "+", px + C_PLUS, y, mouseX, mouseY);
            double pct = total > 0 ? 100.0 * e.count / total : 0;
            right(ctx, String.format("%.1f%%", pct), px + C_PCT, y + 2, Theme.GOOD);
            right(ctx, String.format("%.2f", speciesShiniesDay(e.count)), px + C_RATE, y + 2, Theme.GOLD);
            glyph(ctx, "x", px + C_REMOVE, y, mouseX, mouseY);
        }

        if (roster.entries.size() > VISIBLE) {
            int trackH = VISIBLE * ROW_H;
            int thumbH = Math.max(8, trackH * VISIBLE / roster.entries.size());
            int thumbY = listTop + (trackH - thumbH) * scroll / maxScroll();
            ctx.fill(px + PW - 6, listTop, px + PW - 4, listTop + trackH, Theme.CARD_BORDER);
            ctx.fill(px + PW - 6, thumbY, px + PW - 4, thumbY + thumbH, Theme.ACCENT);
        }

        int ty = py + PH - 44;
        Theme.hline(ctx, px + C_SP, ty - 4, PW - 2 * C_SP, Theme.DIVIDER);
        double tot = total * eggsDayPerPasture() * pPerEgg();
        double hrs = tot > 0 ? 24.0 / tot : Double.POSITIVE_INFINITY;
        ctx.drawTextWithShadow(textRenderer, Text.literal(
                total + " pastures   |   " + String.format("%.1f", tot) + " shinies/day   |   1 every " + fmtTime(hrs)),
                px + C_SP, ty, Theme.TEXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal(
                "click a species = find it in-world (K clears)   |   shift-click = +/-10   |   rate 1/"
                        + String.format("%,d", Math.round(OddsEngine.effectiveRate(profile))) + "/egg (set in Calc)"),
                px + C_SP, ty + 12, Theme.TEXT_FAINT);
    }

    private void glyph(DrawContext ctx, String s, int x, int y, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + 12 && mouseY >= y && mouseY < y + ROW_H;
        ctx.drawTextWithShadow(textRenderer, Text.literal(s),
                x + (12 - textRenderer.getWidth(s)) / 2, y + 2, hov ? Theme.ACCENT : Theme.TEXT_DIM);
    }

    private void right(DrawContext ctx, String s, int xRight, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(s), xRight - textRenderer.getWidth(s), y, color);
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String fmtTime(double hours) {
        if (Double.isInfinite(hours) || Double.isNaN(hours)) return "-";
        if (hours >= 48) return String.format("%.1f", hours / 24.0) + "d";
        if (hours >= 1) return String.format("%.1f", hours) + "h";
        return String.format("%.0f", hours * 60.0) + "m";
    }

    @Override
    public boolean shouldPause() { return false; }
}
