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
import com.greenerpastures.core.GpLog;
import com.greenerpastures.notebook.PastureSnapshot;
import com.greenerpastures.notebook.net.NotebookActionC2S;
import com.greenerpastures.notebook.net.NotebookAugmenterS2C;
import com.greenerpastures.notebook.net.NotebookBioBankS2C;
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
    private String selectedPastureKey = null;   // Pastures tab selection; survives refreshIfOpen, resets on tab switch
    private String expandedSpecies = null;      // BioBank accordion — the one open species (null = all collapsed)

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
        if (tab() == Tab.PASTURES) return pasturesContent();
        if (tab() == Tab.AUGMENTER) return augmenterContent();
        if (tab() == Tab.BIOBANK) return biobankContent();
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

    // ── Pastures tab — read-only snapshot monitor ───────────────────────────────────────────────────────
    private Component pasturesContent() {
        FlowLayout body = Containers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.surface(Surface.flat(PANEL));
        body.padding(Insets.of(6));
        body.gap(6);

        FlowLayout listCol = Containers.verticalFlow(Sizing.fixed(150), Sizing.fill(100));
        listCol.surface(Surface.flat(INSET));
        listCol.padding(Insets.of(6));
        listCol.gap(3);
        listCol.child(label("PASTURES · " + NotebookState.pastures.size(), MUTED));
        if (NotebookState.pastures.isEmpty()) {
            listCol.child(label("open a pasture in-world to track it here", MUTED));
        } else {
            FlowLayout items = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
            items.gap(2);
            for (PastureSnapshot s : NotebookState.pastures) items.child(pastureListItem(s));
            listCol.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), items));
        }
        body.child(listCol);
        body.child(pastureDetail());
        return body;
    }

    private Component pastureListItem(PastureSnapshot s) {
        boolean sel = s.key().equals(selectedPastureKey);
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(sel ? PANEL_HI : SLOT));
        row.padding(Insets.of(3, 3, 5, 5));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.cursorStyle(CursorStyle.POINTER);
        row.child(label(s.name(), sel ? TEXT : MUTED));
        row.child(expander());
        row.child(label(s.eggCount() + " eggs", AMBER));
        row.mouseDown().subscribe((mx, my, btn) -> {
            selectedPastureKey = s.key();
            MinecraftClient.getInstance().execute(this::rebuild);   // defer: don't dispose the owo tree mid-click
            return true;
        });
        return row;
    }

    private Component pastureDetail() {
        FlowLayout col = Containers.verticalFlow(Sizing.expand(), Sizing.fill(100));
        col.surface(Surface.flat(INSET));
        col.padding(Insets.of(8));
        col.gap(4);
        PastureSnapshot sel = selectedPasture();
        if (sel == null) {
            col.horizontalAlignment(HorizontalAlignment.CENTER);
            col.verticalAlignment(VerticalAlignment.CENTER);
            col.child(label(NotebookState.pastures.isEmpty() ? "no pastures tracked yet" : "select a pasture", MUTED));
            return col;
        }
        col.child(label(sel.name(), GREEN));
        col.child(label(sel.tier() + " · " + sel.eggCount() + " eggs queued · " + sel.pairs().size() + " pairs", MUTED));
        col.child(label("read-only — modify at the pasture in-world", MUTED));
        FlowLayout pairs = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        pairs.gap(2);
        if (sel.pairs().isEmpty()) {
            pairs.child(label("no pairs arranged", MUTED));
        } else {
            for (String p : sel.pairs()) {
                FlowLayout pr = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                pr.surface(Surface.flat(SLOT));
                pr.padding(Insets.of(2, 2, 5, 5));
                pr.child(label(p, statusColor(p)));
                pairs.child(pr);
            }
        }
        col.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), pairs));
        return col;
    }

    private PastureSnapshot selectedPasture() {
        for (PastureSnapshot s : NotebookState.pastures) if (s.key().equals(selectedPastureKey)) return s;
        return null;
    }

    private static int statusColor(String pairLine) {
        if (pairLine.endsWith("Breeding")) return CYAN;
        if (pairLine.endsWith("Ready")) return GREEN;
        if (pairLine.endsWith("Incomplete")) return MUTED;
        return TEXT;
    }

    // ── Augmenter (Kernel) tab — KERNEL slots + AUGMENT catalog ─────────────────────────────────────────
    private Component augmenterContent() {
        if (!NotebookState.augHasKernel) {
            FlowLayout empty = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
            empty.surface(Surface.flat(PANEL));
            empty.horizontalAlignment(HorizontalAlignment.CENTER);
            empty.verticalAlignment(VerticalAlignment.CENTER);
            empty.gap(6);
            empty.child(label("no Kernel in your inventory", GREEN));
            empty.child(label("hold a Kernel (a Pasture Upgrade) to augment it", MUTED));
            return empty;
        }
        FlowLayout body = Containers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.surface(Surface.flat(PANEL));
        body.padding(Insets.of(6));
        body.gap(6);
        body.child(kernelCol());
        body.child(augCatalogCol());
        return body;
    }

    private Component kernelCol() {
        FlowLayout col = Containers.verticalFlow(Sizing.fixed(112), Sizing.fill(100));
        col.surface(Surface.flat(INSET));
        col.padding(Insets.of(6));
        col.gap(4);
        col.child(label("KERNEL", MUTED));
        col.child(label(NotebookState.augTier, GREEN));
        col.child(label("slots " + NotebookState.augSlotsUsed + "/" + NotebookState.augSlotCap, TEXT));
        FlowLayout pips = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        pips.gap(2);
        for (int i = 0; i < NotebookState.augSlotCap; i++) pips.child(dot(i < NotebookState.augSlotsUsed ? GREEN : MUTED));
        col.child(pips);
        return col;
    }

    private Component augCatalogCol() {
        FlowLayout col = Containers.verticalFlow(Sizing.expand(), Sizing.fill(100));
        col.surface(Surface.flat(INSET));
        col.padding(Insets.of(6));
        col.gap(4);
        col.child(label("AUGMENTS", MUTED));
        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(2);
        for (NotebookAugmenterS2C.Aug a : NotebookState.augCatalog) list.child(augRow(a));
        col.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), list));
        return col;
    }

    private Component augRow(NotebookAugmenterS2C.Aug a) {
        boolean applied = a.appliedLevel() > 0;   // leveled payload (2026-07-05); the owo fallback just shows on/off
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);
        row.surface(Surface.flat(applied ? PANEL_HI : SLOT));
        row.padding(Insets.of(2, 2, 4, 4));
        row.child(label(a.label(), applied ? TEXT : MUTED));
        row.child(expander());
        row.child(label(a.slotCost() + (a.slotCost() == 1 ? " slot" : " slots"), MUTED));
        if (applied) {
            ButtonComponent btn = Components.button(Text.literal("REMOVE").styled(s -> s.withColor(AMBER)),
                    b -> removeAug(a.type()));
            btn.renderer(ButtonComponent.Renderer.flat(0xFF241318, 0xFF3A1D24, 0xFF241318));
            btn.sizing(Sizing.fixed(54), Sizing.fixed(14));
            row.child(btn);
        } else {
            boolean canApply = NotebookState.augSlotsUsed + a.slotCost() <= NotebookState.augSlotCap;
            ButtonComponent btn = Components.button(Text.literal("APPLY").styled(s -> s.withColor(canApply ? GREEN : MUTED)),
                    b -> applyAug(a.type()));
            btn.renderer(ButtonComponent.Renderer.flat(0xFF0E2417, 0xFF17401F, 0xFF0A1219));
            btn.sizing(Sizing.fixed(54), Sizing.fixed(14));
            btn.active(canApply);
            row.child(btn);
        }
        return row;
    }

    private void applyAug(String type) {
        ClientPlayNetworking.send(new NotebookActionC2S(NotebookActionC2S.APPLY_AUGMENT, type, 0));
    }

    private void removeAug(String type) {
        ClientPlayNetworking.send(new NotebookActionC2S(NotebookActionC2S.REMOVE_AUGMENT, type, 0));
    }

    // ── BioBank tab — species accordion (browse; slice 6a stats) ────────────────────────────────────────
    private Component biobankContent() {
        FlowLayout body = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(6);
        body.surface(Surface.flat(PANEL));
        body.padding(Insets.of(8));

        java.util.LinkedHashMap<String, java.util.List<NotebookBioBankS2C.Entry>> groups = new java.util.LinkedHashMap<>();
        for (NotebookBioBankS2C.Entry e : NotebookState.biobank) {
            groups.computeIfAbsent(e.species(), s -> new java.util.ArrayList<>()).add(e);
        }
        body.child(label("BioBank · " + NotebookState.biobankTotal + " kept · " + groups.size() + " species", MUTED));
        if (groups.isEmpty()) {
            FlowLayout empty = Containers.verticalFlow(Sizing.fill(100), Sizing.expand());
            empty.horizontalAlignment(HorizontalAlignment.CENTER);
            empty.verticalAlignment(VerticalAlignment.CENTER);
            empty.child(label("empty — deposit eggs at a BioBank block (right-click)", MUTED));
            body.child(empty);
            return body;
        }
        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(2);
        for (var g : groups.entrySet()) {
            boolean open = g.getKey().equals(expandedSpecies);
            list.child(speciesHeader(g.getKey(), g.getValue().size(), open));
            if (open) for (NotebookBioBankS2C.Entry e : g.getValue()) list.child(bioEntryRow(e));
        }
        body.child(Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), list));
        return body;
    }

    private Component speciesHeader(String species, int count, boolean open) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(open ? PANEL_HI : SLOT));
        row.padding(Insets.of(3, 3, 5, 5));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.cursorStyle(CursorStyle.POINTER);
        row.child(label((open ? "▾ " : "▸ ") + capitalize(species), open ? TEXT : GREEN));
        row.child(expander());
        row.child(label("×" + count, MUTED));
        row.mouseDown().subscribe((mx, my, btn) -> {
            expandedSpecies = open ? null : species;
            MinecraftClient.getInstance().execute(this::rebuild);   // defer: don't dispose the owo tree mid-click
            return true;
        });
        return row;
    }

    private Component bioEntryRow(NotebookBioBankS2C.Entry e) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.flat(INSET));
        card.padding(Insets.of(3, 3, 6, 6));
        card.gap(2);

        // meta line: ★ · gender · nature · ability · IV total
        FlowLayout meta = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        meta.verticalAlignment(VerticalAlignment.CENTER);
        meta.gap(8);
        if (e.shiny()) meta.child(label("★", AMBER));
        if (!e.gender().isEmpty()) meta.child(label(genderGlyph(e.gender()), genderColor(e.gender())));
        if (!e.nature().isEmpty()) meta.child(label(capitalize(e.nature()), TEXT));
        if (!e.ability().isEmpty()) meta.child(label(capitalize(e.ability()), CYAN));
        meta.child(expander());
        int ivt = ivTotalOf(e.ivs());
        meta.child(label("Σ" + ivt + "/186", ivt >= 160 ? GREEN : MUTED));
        card.child(meta);

        // IV chips (green = 31); EV chips only if the egg carries any (e.g. from the EV augment)
        card.child(statRow("IV", e.ivs(), 31, GREEN));
        if (anyPositive(e.evs())) card.child(statRow("EV", e.evs(), 252, AMBER));
        return card;
    }

    private Component statRow(String tag, int[] stats, int perfect, int perfectColor) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(5);
        row.child(label(tag, MUTED));
        String[] names = {"HP", "At", "Df", "SA", "SD", "Sp"};
        for (int i = 0; i < 6; i++) {
            int v = i < stats.length ? stats[i] : 0;
            int color = v >= perfect ? perfectColor : (v == 0 ? MUTED : TEXT);
            row.child(label(names[i] + " " + v, color));
        }
        return row;
    }

    private static int ivTotalOf(int[] a) {
        int t = 0;
        for (int v : a) t += v;
        return t;
    }

    private static boolean anyPositive(int[] a) {
        for (int v : a) if (v > 0) return true;
        return false;
    }

    private static String genderGlyph(String g) {
        String l = g.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("female")) return "♀";
        if (l.contains("male")) return "♂";
        return "⚲";
    }

    private static int genderColor(String g) {
        String l = g.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("female")) return 0xF2A7C4;   // pink
        if (l.contains("male")) return CYAN;
        return MUTED;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
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

    /** Rebuild the console in place when fresh {@link NotebookState} arrives. Called from the S2C receivers
     *  (client thread) — NOT the constructor, so no re-request, no loop. */
    public static void refreshIfOpen() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof NotebookScreen ns) ns.rebuild();
    }

    /** Force an owo rebuild. {@link BaseOwoScreen#init()} only calls {@link #build} when {@code uiAdapter == null};
     *  a plain {@code clearAndInit()} REUSES the already-built (empty) component tree, so the console would freeze
     *  on its construction-time paint (before the async data push lands). Dispose + null the adapter so init()
     *  re-runs build() against the current {@link NotebookState}. (Verified against owo-lib 0.12.15 bytecode.) */
    private void rebuild() {
        GpLog.d("notebook", "rebuild", "tab", tab().name());   // breadcrumb: proves the repaint fired (perf-audit observability)
        if (this.uiAdapter != null) {
            this.uiAdapter.dispose();
            this.uiAdapter = null;
        }
        this.clearAndInit();
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
