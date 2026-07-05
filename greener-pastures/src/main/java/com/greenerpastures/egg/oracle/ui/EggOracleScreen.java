package com.greenerpastures.egg.oracle.ui;

import com.greenerpastures.egg.oracle.calc.OddsEngine;
import com.greenerpastures.egg.oracle.calc.Presets;
import com.greenerpastures.egg.oracle.calc.Profile;
import com.greenerpastures.egg.oracle.ui.widgets.FlatButton;
import com.greenerpastures.egg.oracle.ui.widgets.ToggleSwitch;
import com.greenerpastures.egg.oracle.EggOracleClient;
import com.greenerpastures.egg.oracle.farm.FarmScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** The EggOracle popup: profile inputs on the left, live odds on the right. */
public class EggOracleScreen extends Screen {
    private static final int PW = 360, PH = 200;

    private final Profile profile;
    private int px, py;
    private int presetIdx = 1; // "Cobbreeding"
    private boolean applyingPreset = false;

    private FlatButton presetButton;
    private TextFieldWidget baseField, masudaField, pairsField, eggField, windowField;
    private ToggleSwitch otToggle;

    // left-column row layout
    private int contentY, ctrlRight, step;

    public EggOracleScreen(Profile profile) {
        super(Text.literal("EggOracle"));
        this.profile = profile;
    }

    @Override
    protected void init() {
        px = (this.width - PW) / 2;
        py = (this.height - PH) / 2;
        ctrlRight = px + 156;
        contentY = py + 38;
        step = 22;

        presetButton = new FlatButton(ctrlRight - 84, contentY, 84, 16,
                Text.literal(profile.presetName), b -> cyclePreset());
        baseField = numField(ctrlRight - 64, contentY + step, fmtRate(profile.baseRate));
        masudaField = numField(ctrlRight - 64, contentY + 2 * step, fmtNum(profile.masudaMult, 1));
        otToggle = new ToggleSwitch(ctrlRight - 30, contentY + 3 * step + 1, profile.diffOT);
        pairsField = numField(ctrlRight - 64, contentY + 4 * step, String.valueOf(profile.pairs));
        eggField = numField(ctrlRight - 64, contentY + 5 * step, fmtNum(profile.eggTimeMin, 1));
        windowField = numField(ctrlRight - 64, contentY + 6 * step, fmtNum(profile.horizonHours, 0));

        addDrawableChild(presetButton);
        addDrawableChild(baseField);
        addDrawableChild(masudaField);
        addDrawableChild(otToggle);
        addDrawableChild(pairsField);
        addDrawableChild(eggField);
        addDrawableChild(windowField);
        addDrawableChild(new FlatButton(px + PW - 90, py + 5, 64, 14, Text.literal("Farm >"),
                b -> { if (client != null) client.setScreen(new FarmScreen(profile, EggOracleClient.ROSTER, this)); }));
        addDrawableChild(new FlatButton(px + PW - 22, py + 5, 16, 14, Text.literal("x"), b -> this.close()));
    }

    private TextFieldWidget numField(int x, int y, String text) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, 64, 14, Text.empty());
        f.setMaxLength(8);
        f.setText(text);
        f.setTextPredicate(s -> s.isEmpty() || s.matches("\\d*\\.?\\d*"));
        f.setChangedListener(s -> onEdit());
        return f;
    }

    private void onEdit() {
        if (!applyingPreset) {
            profile.presetName = "Custom";
            presetButton.setMessage(Text.literal("Custom"));
        }
    }

    private void cyclePreset() {
        presetIdx = (presetIdx + 1) % Presets.ALL.size();
        Presets.Preset pr = Presets.ALL.get(presetIdx);
        applyingPreset = true;
        Presets.apply(pr, profile);
        baseField.setText(fmtRate(profile.baseRate));
        masudaField.setText(fmtNum(profile.masudaMult, 1));
        otToggle.setOn(profile.diffOT);
        presetButton.setMessage(Text.literal(profile.presetName));
        applyingPreset = false;
    }

    // ---- input -> profile ----
    private void syncFromWidgets() {
        profile.baseRate = parse(baseField, profile.baseRate);
        profile.masudaMult = parse(masudaField, profile.masudaMult);
        profile.pairs = (int) Math.round(parse(pairsField, profile.pairs));
        profile.eggTimeMin = parse(eggField, profile.eggTimeMin);
        profile.horizonHours = parse(windowField, profile.horizonHours);
        profile.diffOT = otToggle.isOn();
    }

    private double parse(TextFieldWidget f, double fallback) {
        try {
            String t = f.getText();
            return t.isEmpty() ? fallback : Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ---- rendering ----
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, Theme.BACKDROP);
        Theme.panel(ctx, px, py, PW, PH, Theme.PANEL, Theme.PANEL_BORDER);

        // title bar
        ctx.fill(px + 12, py + 8, px + 16, py + 12, Theme.GOLD); // logo mark
        ctx.drawTextWithShadow(textRenderer, Text.literal("EggOracle"), px + 22, py + 7, Theme.TEXT);
        Theme.hline(ctx, px + 1, py + 22, PW - 2, Theme.DIVIDER);

        // section headers + column divider
        ctx.drawTextWithShadow(textRenderer, Text.literal("PROFILE"), px + 14, py + 27, Theme.TEXT_FAINT);
        ctx.drawTextWithShadow(textRenderer, Text.literal("RESULTS"), px + 182, py + 27, Theme.TEXT_FAINT);
        Theme.vline(ctx, px + 168, py + 36, PH - 46, Theme.DIVIDER);

        // left-column labels
        label(ctx, "Preset", contentY, 16);
        label(ctx, "Base rate", contentY + step, 14);
        label(ctx, "Masuda x", contentY + 2 * step, 14);
        label(ctx, "Different OT?", contentY + 3 * step, 14);
        label(ctx, "Pairs", contentY + 4 * step, 14);
        label(ctx, "Egg time (m)", contentY + 5 * step, 14);
        label(ctx, "Window (h)", contentY + 6 * step, 14);
    }

    private void label(DrawContext ctx, String s, int y, int ctrlH) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(s), px + 14, y + (ctrlH - 8) / 2, Theme.TEXT_DIM);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // renderBackground + widgets
        syncFromWidgets();
        drawResults(ctx);
    }

    private void drawResults(DrawContext ctx) {
        int rx = px + 182;
        int valRight = px + PW - 16;
        int ry = contentY;
        int gap = 20;

        double rate = OddsEngine.effectiveRate(profile);
        resultRow(ctx, rx, valRight, ry, "Per egg", "1 / " + fmtRate(rate), Theme.GOLD);
        resultRow(ctx, rx, valRight, ry + gap, "Eggs / hr", fmtNum(OddsEngine.eggsPerHour(profile), 1), Theme.TEXT);
        resultRow(ctx, rx, valRight, ry + 2 * gap, "Shinies / day", fmtNum(OddsEngine.shiniesPerDay(profile), 2), Theme.TEXT);
        resultRow(ctx, rx, valRight, ry + 3 * gap, "Avg to shiny", fmtTime(OddsEngine.hoursPerShiny(profile)), Theme.TEXT);

        int dy = ry + 4 * gap + 2;
        Theme.hline(ctx, rx, dy, valRight - rx, Theme.DIVIDER);

        double chance = OddsEngine.chanceWithin(profile, profile.horizonHours);
        int by = dy + 10;
        resultRow(ctx, rx, valRight, by, "By " + fmtNum(profile.horizonHours, 0) + "h", fmtPct(chance), Theme.GOOD);

        // progress bar
        int barY = by + 13, barH = 8, barW = valRight - rx;
        Theme.panel(ctx, rx, barY, barW, barH, Theme.CARD, Theme.CARD_BORDER);
        int fillW = (int) Math.round((barW - 2) * Math.max(0, Math.min(1, chance)));
        if (fillW > 0) ctx.fill(rx + 1, barY + 1, rx + 1 + fillW, barY + barH - 1, Theme.ACCENT);
    }

    private void resultRow(DrawContext ctx, int rx, int valRight, int y, String label, String value, int valueColor) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(label), rx, y, Theme.TEXT_DIM);
        int vw = textRenderer.getWidth(value);
        ctx.drawTextWithShadow(textRenderer, Text.literal(value), valRight - vw, y, valueColor);
    }

    @Override
    public boolean shouldPause() {
        return false; // keep the world ticking behind the tool
    }

    // ---- formatting ----
    private static String fmtRate(double v) {
        return String.format("%,d", Math.round(v));
    }

    private static String fmtNum(double v, int decimals) {
        return String.format("%." + decimals + "f", v);
    }

    private static String fmtPct(double p) {
        return String.format("%.1f%%", p * 100.0);
    }

    private static String fmtTime(double hours) {
        if (Double.isInfinite(hours) || Double.isNaN(hours)) return "-";
        if (hours >= 48) return fmtNum(hours / 24.0, 1) + " days";
        if (hours >= 1) return fmtNum(hours, 1) + " hrs";
        return fmtNum(hours * 60.0, 0) + " min";
    }
}
