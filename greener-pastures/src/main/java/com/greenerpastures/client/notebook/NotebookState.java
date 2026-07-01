package com.greenerpastures.client.notebook;

import com.greenerpastures.notebook.PastureSnapshot;
import com.greenerpastures.notebook.net.NotebookAugmenterS2C;
import com.greenerpastures.notebook.net.NotebookBioBankS2C;
import com.greenerpastures.notebook.net.NotebookCompilerS2C;
import com.greenerpastures.notebook.net.NotebookGraphS2C;
import com.greenerpastures.notebook.net.NotebookPastureConfigS2C;
import com.greenerpastures.notebook.net.NotebookPasturesS2C;
import com.greenerpastures.notebook.net.NotebookStatusS2C;
import com.greenerpastures.notebook.net.NotebookStorageS2C;

import java.util.List;
import java.util.Map;

/**
 * Client-side cache the {@link NotebookScreen} renders from (NOTEBOOK_INTERACTIVE_SPEC §2.1) — the screen never
 * touches server objects, it reads this. Updated by the S2C receivers (wired in {@code GreenerPasturesClient}),
 * which then call {@link NotebookScreen#refreshIfOpen()} to rebuild the open console.
 *
 * <p>Fields are {@code volatile} because receivers hop onto the client thread but the screen may read mid-hop.
 */
public final class NotebookState {
    private NotebookState() {}

    /** False until the first status push lands — lets the status bar show "…" instead of a bogus 0. */
    public static volatile boolean hasStatus = false;
    public static volatile long data = 0L;
    public static volatile int gpu = 0;
    public static volatile boolean daemonOn = false;

    /** Returns true iff this push actually changed the cache (so the screen only repaints on real change). */
    public static boolean applyStatus(NotebookStatusS2C p) {
        boolean changed = !hasStatus || data != p.data() || gpu != p.gpu() || daemonOn != p.daemonOn();
        data = p.data();
        gpu = p.gpu();
        daemonOn = p.daemonOn();
        hasStatus = true;
        return changed;
    }

    // ── Harvester / Storage tab ──────────────────────────────────────────────
    public static volatile boolean hasStorage = false;
    public static volatile Map<String, Long> storage = Map.of();
    public static volatile long storageCap = 0L;

    public static boolean applyStorage(NotebookStorageS2C p) {
        boolean changed = !hasStorage || storageCap != p.capacity() || !storage.equals(p.items());
        storage = p.items();
        storageCap = p.capacity();
        hasStorage = true;
        return changed;
    }

    // ── Compiler (Daemon) tab ────────────────────────────────────────────────
    public static volatile boolean compilerHasDaemon = false;
    public static volatile boolean compilerDaemonOn = false;
    public static volatile double compilerDrain = 0.0;
    public static volatile List<NotebookCompilerS2C.Buff> compilerCatalog = List.of();
    public static volatile Map<String, Integer> compilerInstalled = Map.of();

    public static boolean applyCompiler(NotebookCompilerS2C p) {
        boolean changed = compilerHasDaemon != p.hasDaemon() || compilerDaemonOn != p.daemonOn()
                || compilerDrain != p.drainPerSec() || !compilerCatalog.equals(p.catalog())
                || !compilerInstalled.equals(p.installed());
        compilerHasDaemon = p.hasDaemon();
        compilerDaemonOn = p.daemonOn();
        compilerDrain = p.drainPerSec();
        compilerCatalog = p.catalog();
        compilerInstalled = p.installed();
        return changed;
    }

    // ── Pastures tab ─────────────────────────────────────────────────────────
    public static volatile List<PastureSnapshot> pastures = List.of();

    public static boolean applyPastures(NotebookPasturesS2C p) {
        boolean changed = !pastures.equals(p.pastures());
        pastures = p.pastures();
        return changed;
    }

    /** The pasture the player right-clicked with the Notebook (its editable config), or null when none is focused. */
    public static volatile NotebookPastureConfigS2C pastureConfig = null;

    public static boolean applyPastureConfig(NotebookPastureConfigS2C p) {
        pastureConfig = p;
        return true;
    }

    /** The focused pasture's Daemon graph JSON (filter/sink nodes + flow edges + mon-node positions), or "" when
     *  none. Rides its own S2C ({@link NotebookGraphS2C}) alongside the config; the bridge folds it into the
     *  {@code pastureConfig} channel as {@code graph}. */
    public static volatile String pastureGraphJson = "";

    public static boolean applyGraph(NotebookGraphS2C p) {
        pastureGraphJson = p.json() == null ? "" : p.json();
        return true;
    }

    // ── Augmenter tab ────────────────────────────────────────────────────────
    public static volatile boolean augHasKernel = false;
    public static volatile String augTier = "";
    public static volatile int augSlotsUsed = 0;
    public static volatile int augSlotCap = 0;
    public static volatile List<NotebookAugmenterS2C.Aug> augCatalog = List.of();

    public static boolean applyAugmenter(NotebookAugmenterS2C p) {
        boolean changed = augHasKernel != p.hasKernel() || !augTier.equals(p.tier())
                || augSlotsUsed != p.slotsUsed() || augSlotCap != p.slotCap() || !augCatalog.equals(p.catalog());
        augHasKernel = p.hasKernel();
        augTier = p.tier();
        augSlotsUsed = p.slotsUsed();
        augSlotCap = p.slotCap();
        augCatalog = p.catalog();
        return changed;
    }

    // ── BioBank tab ──────────────────────────────────────────────────────────
    public static volatile int biobankTotal = 0;
    public static volatile List<NotebookBioBankS2C.Entry> biobank = List.of();

    public static boolean applyBiobank(NotebookBioBankS2C p) {
        boolean changed = biobankTotal != p.total() || !biobank.equals(p.entries());
        biobankTotal = p.total();
        biobank = p.entries();
        return changed;
    }
}
