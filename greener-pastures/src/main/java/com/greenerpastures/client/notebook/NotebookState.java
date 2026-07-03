package com.greenerpastures.client.notebook;

import com.greenerpastures.notebook.PastureSnapshot;
import com.greenerpastures.notebook.net.NotebookAugmenterS2C;
import com.greenerpastures.notebook.net.NotebookBioBankS2C;
import com.greenerpastures.notebook.net.NotebookCompilerS2C;
import com.greenerpastures.notebook.net.NotebookDashboardS2C;
import com.greenerpastures.notebook.net.NotebookEggLogS2C;
import com.greenerpastures.notebook.net.NotebookGoalsS2C;
import com.greenerpastures.notebook.net.NotebookGraphS2C;
import com.greenerpastures.notebook.net.NotebookPastureConfigS2C;
import com.greenerpastures.notebook.net.NotebookPasturesS2C;
import com.greenerpastures.notebook.net.NotebookStatusS2C;
import com.greenerpastures.notebook.net.NotebookStorageS2C;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** True while a pasture's real config is round-tripping — the UI shows a "loading" shell, not stale/empty data. */
    public static volatile boolean pastureConfigLoading = false;

    /** Last-known config/graph per pasture (stale-while-revalidate): a reopened pasture renders its cached view
     *  INSTANTLY and the server refresh lands silently — no loading state for pastures you've seen this session. */
    public static final Map<Long, NotebookPastureConfigS2C> pastureConfigCache = new ConcurrentHashMap<>();
    public static final Map<Long, String> pastureGraphCache = new ConcurrentHashMap<>();

    public static boolean applyPastureConfig(NotebookPastureConfigS2C p) {
        pastureConfigCache.put(p.pos(), p);
        NotebookPastureConfigS2C cur = pastureConfig;
        if (cur != null && cur.pos() == p.pos()) {   // only the FOCUSED pasture updates the live view —
            pastureConfig = p;                        // a background prefetch must never hijack the open screen
            pastureConfigLoading = false;
            return true;
        }
        return false;
    }

    /** The focused pasture's Daemon graph JSON (filter/sink nodes + flow edges + mon-node positions), or "" when
     *  none. Rides its own S2C ({@link NotebookGraphS2C}) alongside the config; the bridge folds it into the
     *  {@code pastureConfig} channel as {@code graph}. */
    public static volatile String pastureGraphJson = "";

    public static boolean applyGraph(NotebookGraphS2C p) {
        String json = p.json() == null ? "" : p.json();
        pastureGraphCache.put(p.pos(), json);
        NotebookPastureConfigS2C cur = pastureConfig;
        if (cur != null && cur.pos() == p.pos()) {   // focused only — prefetches stay cache-only
            pastureGraphJson = json;
            return true;
        }
        return false;
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

    // ── Egg log (the void-log trust feed) ────────────────────────────────────
    public static volatile long eggKept = 0L;
    public static volatile long eggVoided = 0L;
    public static volatile List<NotebookEggLogS2C.Entry> eggLog = List.of();

    public static boolean applyEggLog(NotebookEggLogS2C p) {
        boolean changed = eggKept != p.kept() || eggVoided != p.voided() || !eggLog.equals(p.entries());
        eggKept = p.kept();
        eggVoided = p.voided();
        eggLog = p.entries();
        return changed;
    }

    // ── Dashboard + Goals + Inbox (JSON blobs, parsed in React) ─────────────
    public static volatile String dashboardJson = "";
    public static volatile String goalsJson = "";
    public static volatile String notifsJson = "";

    public static boolean applyNotifs(com.greenerpastures.notebook.net.NotebookNotifsS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !notifsJson.equals(j);
        notifsJson = j;
        return changed;
    }

    public static boolean applyDashboard(NotebookDashboardS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !dashboardJson.equals(j);
        dashboardJson = j;
        return changed;
    }

    public static boolean applyGoals(NotebookGoalsS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !goalsJson.equals(j);
        goalsJson = j;
        return changed;
    }

    /** Wipe the whole client cache — called on world-leave so a new world (or server) never renders the previous
     *  one's data. The pasture caches are keyed by POSITION only, so cross-world they could even collide. */
    public static void clearAll() {
        hasStatus = false; data = 0L; gpu = 0; daemonOn = false;
        hasStorage = false; storage = Map.of(); storageCap = 0L;
        compilerHasDaemon = false; compilerDaemonOn = false; compilerDrain = 0.0;
        compilerCatalog = List.of(); compilerInstalled = Map.of();
        pastures = List.of();
        pastureConfig = null; pastureConfigLoading = false; pastureGraphJson = "";
        pastureConfigCache.clear(); pastureGraphCache.clear();
        augHasKernel = false; augTier = ""; augSlotsUsed = 0; augSlotCap = 0; augCatalog = List.of();
        biobankTotal = 0; biobank = List.of();
        eggKept = 0L; eggVoided = 0L; eggLog = List.of();
        dashboardJson = ""; goalsJson = ""; notifsJson = "";
    }
}
