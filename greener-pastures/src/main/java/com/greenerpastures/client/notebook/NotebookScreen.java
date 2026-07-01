package com.greenerpastures.client.notebook;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import com.greenerpastures.notebook.net.NotebookActionC2S;
import com.greenerpastures.notebook.net.NotebookCompilerS2C;
import com.greenerpastures.notebook.net.NotebookRequestC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The <b>Notebook console</b> — the mod's unified data-science shell (see {@code NOTEBOOK_CONSOLE_SPEC.md}),
 * rendered natively in <b>owo-ui</b> to match the reference JSX mock
 * ({@code design/design_reference/notebook-console.NOTES.md}).
 *
 * <p>This is the <b>shell</b> (Phase 4): the window chrome — title bar · tab strip · {@code gp://} command bar ·
 * content area · status bar — with working tab switching. Per-tab content is Phase 5; each tab currently shows an
 * intent blurb so the structure is legible. Status-bar figures (Data/GPU/Kernel/Daemon) are placeholders until the
 * live server sync lands.
 *
 * <p>Opened client-side from {@link com.greenerpastures.pasture.breeding.NotebookItem} (air / non-pasture
 * right-click) through its {@code CONSOLE_OPENER} hook, wired in {@code GreenerPasturesClient}.
 */
public class NotebookScreen extends BaseOwoScreen<FlowLayout> {

    // ── palette (notebook-console.NOTES.md) — surfaces are 0xAARRGGBB, text is 0xRRGGBB ───────────────
    private static final int BG = 0xFF070C11, PANEL = 0xFF0C141B, PANEL_HI = 0xFF0F1A22,
            TABBAR = 0xFF0A1219, INSET = 0xFF070E13, LINE = 0xFF1B2A36, SCRIM = 0xB8030609, SLOT = 0xFF060B0F;
    private static final int GREEN = 0x43D869, AMBER = 0xF5B234, CYAN = 0x5BE0E0,
            TEXT = 0xCFE3DA, MUTED = 0x688089, RED = 0xF2555A;

    /** The console tabs, in mock order. {@code path} feeds the {@code gp://} bar; {@code blurb} is the shell stub. */
    private enum Tab {
        BIOBANK("BioBank", "gp://biobank", "kept mons · IVs / EVs · search + sort"),
        HARVESTER("Harvester", "gp://harvester", "auto-collected drops · pull one / pull all"),
        PASTURES("Pastures", "gp://pastures", "your pastures · 8-pair grid · link + status"),
        COMPILER("Compiler", "gp://daemon/compiler", "build the Daemon's buff loadout"),
        AUGMENTER("Augmenter", "gp://kernel/augmenter", "apply GPU augments to Kernels"),
        DASHBOARD("Dashboard", "gp://dashboard", "farm analytics — coming soon");
        final String label, path, blurb;
        Tab(String l, String p, String b) { label = l; path = p; blurb = b; }
    }

    private final int activeTab;

    public NotebookScreen() { this(Tab.PASTURES.ordinal()); }

    public NotebookScreen(int tab) {
        super();
        this.activeTab = Math.floorMod(tab, Tab.values().length);
        ClientPlayNetworking.send(new NotebookRequestC2S(this.activeTab));  // ask the server to push our status/tab data
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.flat(SCRIM))
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER);

        FlowLayout window = Containers.verticalFlow(Sizing.fill(90), Sizing.fill(86));
        window.surface(Surface.flat(BG).and(Surface.outline(LINE)));
        window.child(titleBar())
              .child(divider())
              .child(tabStrip())
              .child(divider())
              .child(commandBar())
              .child(content())          // expands to fill the middle
              .child(divider())
              .child(statusBar());
        root.child(window);
    }

    // ── chrome sections ───────────────────────────────────────────────────────────────────────────────

    private Component titleBar() {
        FlowLayout bar = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        bar.gap(5);   // gap() is FlowLayout-only — call it on the typed var, not after surface()/padding() (→ ParentComponent)
        bar.surface(Surface.flat(TABBAR))
           .verticalAlignment(VerticalAlignment.CENTER)
           .padding(Insets.of(5, 5, 8, 8));
        bar.child(dot(RED)).child(dot(AMBER)).child(dot(GREEN))
           .child(label("greener-pastures :: notebook", TEXT).margins(Insets.left(4)))
           .child(expander())
           .child(label("● kernel: connected", GREEN))
           .child(Components.button(Text.literal("✕").styled(s -> s.withColor(MUTED)), b -> this.close())
                   .renderer(ButtonComponent.Renderer.flat(0x00000000, 0x33F2555A, 0x00000000))
                   .sizing(Sizing.fixed(14), Sizing.fixed(14)));
        return bar;
    }

    private Component tabStrip() {
        FlowLayout strip = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        strip.gap(2);
        strip.surface(Surface.flat(TABBAR)).padding(Insets.of(3, 0, 4, 4));
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            final boolean on = i == activeTab;
            final int idx = i;
            final int bg = on ? PANEL_HI : 0x00000000;
            final int hover = on ? PANEL_HI : 0x22102030;
            strip.child(Components.button(
                        Text.literal(tabs[i].label).styled(s -> s.withColor(on ? TEXT : MUTED)),
                        b -> selectTab(idx))
                    .renderer(ButtonComponent.Renderer.flat(bg, hover, bg))
                    .sizing(Sizing.content(), Sizing.fixed(18)));
        }
        return strip;
    }

    private Component commandBar() {
        FlowLayout bar = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        bar.gap(2);
        bar.surface(Surface.flat(INSET))
           .verticalAlignment(VerticalAlignment.CENTER)
           .padding(Insets.of(4, 4, 8, 8));
        bar.child(label(tab().path, MUTED)).child(label("▏", GREEN));
        return bar;
    }

    private Component content() {
        if (tab() == Tab.HARVESTER) return storageContent();
        if (tab() == Tab.COMPILER) return compilerContent();
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(6);
        body.surface(Surface.flat(PANEL))
            .horizontalAlignment(HorizontalAlignment.CENTER)
            .verticalAlignment(VerticalAlignment.CENTER)
            .padding(Insets.of(16));
        body.child(label(tab().label, GREEN))
            .child(label(tab().blurb, MUTED))
            .child(label("shell is live — this tab's content lands next", TEXT).margins(Insets.top(4)));
        return body;
    }

    // ── Harvester / Storage tab ───────────────────────────────────────────────────────────────────────
    private Component storageContent() {
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(6);
        body.surface(Surface.flat(PANEL)).padding(Insets.of(8)).horizontalAlignment(HorizontalAlignment.CENTER);

        long total = NotebookState.storage.values().stream().mapToLong(Long::longValue).sum();
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.gap(8);
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.child(label("Storage", GREEN))
              .child(label(NotebookState.storage.size() + " types · " + fmt(total) + " items", MUTED))
              .child(expander())
              .child(label("L-click: pull a stack    R-click: pull all", MUTED));
        body.child(header);

        java.util.List<java.util.Map.Entry<String, Long>> entries =
                new java.util.ArrayList<>(NotebookState.storage.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        if (entries.isEmpty()) {
            FlowLayout empty = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
            empty.horizontalAlignment(HorizontalAlignment.CENTER);
            empty.verticalAlignment(VerticalAlignment.CENTER);
            empty.child(label("empty — harvested loot from your linked pastures collects here", MUTED));
            body.child(empty);
            return body;
        }
        int cols = 9;
        int rows = (entries.size() + cols - 1) / cols;
        GridLayout grid = Containers.grid(Sizing.content(), Sizing.content(), rows, cols);
        for (int i = 0; i < entries.size(); i++) {
            java.util.Map.Entry<String, Long> e = entries.get(i);
            grid.child(cell(e.getKey(), e.getValue()), i / cols, i % cols);
        }
        body.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), grid));
        return body;
    }

    private Component cell(String id, long count) {
        Item item = Registries.ITEM.get(Identifier.of(id));
        FlowLayout c = Containers.verticalFlow(Sizing.fixed(30), Sizing.fixed(34));
        c.surface(Surface.flat(SLOT));
        c.horizontalAlignment(HorizontalAlignment.CENTER);
        c.verticalAlignment(VerticalAlignment.CENTER);
        c.margins(Insets.of(1));
        c.cursorStyle(CursorStyle.POINTER);
        c.child(Components.item(new ItemStack(item)).showOverlay(false).setTooltipFromStack(true));
        c.child(label(compact(count), TEXT).margins(Insets.top(1)));
        c.mouseDown().subscribe((mouseX, mouseY, button) -> {
            ClientPlayNetworking.send(new NotebookActionC2S(
                    button == 1 ? NotebookActionC2S.PULL_ID : NotebookActionC2S.PULL_ONE, id, 0));
            return true;
        });
        return c;
    }

    private static String compact(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 1_000_000) return String.format("%.1fk", n / 1000.0);
        if (n < 1_000_000_000) return String.format("%.1fM", n / 1_000_000.0);
        return String.format("%.1fB", n / 1_000_000_000.0);
    }

    // ── Compiler (Daemon) tab — triptych: DAEMON · EFFECT · LOADOUT ─────────────────────────────────────
    private Component compilerContent() {
        if (!NotebookState.compilerHasDaemon) {
            FlowLayout empty = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
            empty.surface(Surface.flat(PANEL));
            empty.horizontalAlignment(HorizontalAlignment.CENTER);
            empty.verticalAlignment(VerticalAlignment.CENTER);
            empty.gap(6);
            empty.child(label("no Daemon in your inventory", GREEN));
            empty.child(label("hold a Daemon (anywhere in your pack) to compile its buffs", MUTED));
            return empty;
        }
        FlowLayout body = Containers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.surface(Surface.flat(PANEL));
        body.padding(Insets.of(6));
        body.gap(6);
        body.child(daemonCol());
        body.child(effectCol());
        body.child(loadoutCol());
        return body;
    }

    private Component daemonCol() {
        FlowLayout col = Containers.verticalFlow(Sizing.fixed(92), Sizing.fill(100));
        col.surface(Surface.flat(INSET));
        col.padding(Insets.of(6));
        col.gap(4);
        boolean on = NotebookState.compilerDaemonOn;
        col.child(label("DAEMON", MUTED));
        col.child(label(on ? "● running" : "○ idle", on ? GREEN : MUTED));
        col.child(label("Data " + (NotebookState.hasStatus ? fmt(NotebookState.data) : "…"), AMBER));
        col.child(label(NotebookState.compilerInstalled.size() + "/32 buffs", TEXT));
        return col;
    }

    private Component effectCol() {
        FlowLayout col = Containers.verticalFlow(Sizing.expand(), Sizing.fill(100));
        col.surface(Surface.flat(INSET));
        col.padding(Insets.of(6));
        col.gap(4);
        col.child(label("EFFECT", MUTED));
        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(2);
        for (NotebookCompilerS2C.Buff b : NotebookState.compilerCatalog) list.child(buffRow(b));
        col.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), list));
        return col;
    }

    private Component buffRow(NotebookCompilerS2C.Buff b) {
        int tier = NotebookState.compilerInstalled.getOrDefault(b.id(), 0);
        boolean active = tier > 0;
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(3);
        row.surface(Surface.flat(active ? PANEL_HI : SLOT));
        row.padding(Insets.of(2, 2, 4, 4));
        row.child(label(b.label(), active ? TEXT : MUTED));
        row.child(expander());
        row.child(stepBtn("−", b.id(), tier - 1, tier > 0));
        row.child(label("L" + tier + "/" + b.cap(), active ? GREEN : MUTED));
        row.child(stepBtn("+", b.id(), tier + 1, tier < b.cap()));
        row.child(label(String.format("%.2f/s", tier * b.costPerTier()), AMBER));
        return row;
    }

    private Component stepBtn(String glyph, String buffId, int toTier, boolean enabled) {
        ButtonComponent btn = Components.button(
                Text.literal(glyph).styled(s -> s.withColor(enabled ? TEXT : MUTED)),
                b -> setBuff(buffId, toTier));
        btn.renderer(ButtonComponent.Renderer.flat(0xFF0A1219, 0xFF16303C, 0xFF0A1219));
        btn.sizing(Sizing.fixed(13), Sizing.fixed(13));
        btn.active(enabled);
        return btn;
    }

    private Component loadoutCol() {
        FlowLayout col = Containers.verticalFlow(Sizing.fixed(120), Sizing.fill(100));
        col.surface(Surface.flat(INSET));
        col.padding(Insets.of(6));
        col.gap(4);
        col.child(label("LOADOUT", MUTED));
        double drain = NotebookState.compilerDrain;
        col.child(label(String.format("drain %.2f/s", drain), AMBER));
        String runtime = drain <= 0 ? "∞" : (NotebookState.hasStatus ? fmtTime((long) (NotebookState.data / drain)) : "…");
        col.child(label("runtime " + runtime, MUTED));
        boolean on = NotebookState.compilerDaemonOn;
        ButtonComponent power = Components.button(
                Text.literal(on ? "Power OFF" : "Power ON").styled(s -> s.withColor(on ? MUTED : GREEN)),
                b -> togglePower());
        power.renderer(ButtonComponent.Renderer.flat(on ? 0xFF201217 : 0xFF0E2417, on ? 0xFF33202A : 0xFF17401F, 0xFF0A1219));
        power.sizing(Sizing.fill(100), Sizing.fixed(16));
        col.child(power);
        return col;
    }

    private void setBuff(String id, int tier) {
        ClientPlayNetworking.send(new NotebookActionC2S(NotebookActionC2S.SET_BUFF, id, tier));
    }

    private void togglePower() {
        ClientPlayNetworking.send(new NotebookActionC2S(NotebookActionC2S.TOGGLE_DAEMON, "", 0));
    }

    private static String fmtTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        return (seconds / 86400) + "d";
    }

    private Component statusBar() {
        FlowLayout bar = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        bar.gap(12);
        bar.surface(Surface.flat(TABBAR))
           .verticalAlignment(VerticalAlignment.CENTER)
           .padding(Insets.of(4, 4, 8, 8));
        boolean up = NotebookState.hasStatus;
        bar.child(label("Data " + (up ? fmt(NotebookState.data) : "…"), AMBER))
           .child(label("GPU " + (up ? Integer.toString(NotebookState.gpu) : "…"), CYAN))
           .child(label("⌬ Kernel", MUTED))
           .child(expander())
           .child(label("● Daemon " + (NotebookState.daemonOn ? "ON" : "OFF"), NotebookState.daemonOn ? GREEN : MUTED));
        return bar;
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────────

    private Tab tab() { return Tab.values()[activeTab]; }

    private void selectTab(int i) {
        MinecraftClient.getInstance().setScreen(new NotebookScreen(i));
    }

    /** Rebuild the console in place when fresh {@link NotebookState} arrives (re-runs init/build, not the
     *  constructor → no re-request → no loop). Called from the S2C receiver on the client thread. */
    public static void refreshIfOpen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof NotebookScreen ns) ns.clearAndInit();
    }

    private static String fmt(long n) { return String.format("%,d", n); }

    private static LabelComponent label(String s, int rgb) {
        return Components.label(Text.literal(s)).color(Color.ofRgb(rgb)).shadow(false);
    }

    private static Component dot(int rgb) {
        return Components.box(Sizing.fixed(6), Sizing.fixed(6)).color(Color.ofRgb(rgb)).fill(true);
    }

    private static Component divider() {
        return Components.box(Sizing.fill(100), Sizing.fixed(1)).color(Color.ofArgb(LINE)).fill(true);
    }

    /** An invisible, width-greedy box — the flexbox "space-between" trick in a horizontal flow. */
    private static Component expander() {
        return Components.box(Sizing.expand(), Sizing.fixed(1)).color(Color.ofArgb(0)).fill(true);
    }
}
