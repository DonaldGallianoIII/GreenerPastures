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
 * Client-side cache the console renders from (NOTEBOOK_INTERACTIVE_SPEC §2.1) - the UI never
 * touches server objects, it reads this. Updated by the S2C receivers (wired in {@code GreenerPasturesClient}),
 * which then nudge {@code DsBridge.pushNow()} to re-serve the browser channels.
 *
 * <p>Fields are {@code volatile} because receivers hop onto the client thread but the screen may read mid-hop.
 */
public final class NotebookState {
    private NotebookState() {}

    /** False until the first status push lands - lets the status bar show "…" instead of a bogus 0. */
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
    /** #37 - {@code {"<dim|pos>":"flagId,flagId"}} badge markers per snapshot (server-built JSON). */
    public static volatile String pasturesHealthJson = "";

    public static boolean applyPastures(NotebookPasturesS2C p) {
        String health = p.healthJson() == null ? "" : p.healthJson();
        boolean changed = !pastures.equals(p.pastures()) || !pasturesHealthJson.equals(health);
        pastures = p.pastures();
        pasturesHealthJson = health;
        return changed;
    }

    /** The pasture the player right-clicked with the Notebook (its editable config), or null when none is focused. */
    public static volatile NotebookPastureConfigS2C pastureConfig = null;

    /** True while a pasture's real config is round-tripping - the UI shows a "loading" shell, not stale/empty data. */
    public static volatile boolean pastureConfigLoading = false;

    /** Last-known config/graph per pasture (stale-while-revalidate): a reopened pasture renders its cached view
     *  INSTANTLY and the server refresh lands silently - no loading state for pastures you've seen this session.
     *  Keyed by {@link #posKey} (dim + pos - position alone collides across dimensions, R3 BUG-1) and LRU-capped
     *  so a mega-farm session can't grow the client heap unbounded (R3 client #6). */
    public static final Map<String, NotebookPastureConfigS2C> pastureConfigCache = lru(128);
    public static final Map<String, String> pastureGraphCache = lru(128);
    public static final Map<String, String> pastureExtraCache = lru(128);

    private static <V> Map<String, V> lru(int cap) {
        return java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, V> eldest) { return size() > cap; }
        });
    }

    /** Cache key for a pasture the LOCAL player is looking at: current dimension + packed pos. All pasture
     *  pushes are same-dim (focused + prefetch both filter), so the client's world is the right namespace. */
    public static String posKey(long pos) {
        var w = net.minecraft.client.MinecraftClient.getInstance().world;
        return (w == null ? "?" : w.getRegistryKey().getValue().toString()) + "|" + pos;
    }

    public static boolean applyPastureConfig(NotebookPastureConfigS2C p) {
        String key = posKey(p.pos());
        p = withStatsBackfill(p, pastureConfigCache.get(key));
        pastureConfigCache.put(key, p);
        NotebookPastureConfigS2C cur = pastureConfig;
        if (cur != null && cur.pos() == p.pos()) {   // only the FOCUSED pasture updates the live view -
            pastureConfig = p;                        // a background prefetch must never hijack the open screen
            pastureConfigLoading = false;
            return true;
        }
        return false;
    }

    /** Stale-while-revalidate, done properly (BUG-012): the 1-min prefetch sweep sends the SAME pasture with a
     *  stats-less roster shape, and it used to overwrite the focused view - genders vanished and every 2-parent
     *  line read "this pair can't breed". A stats-less incoming entry keeps the cached entry's stats; entries
     *  that arrive WITH stats always win (a real full push still refreshes everything). */
    private static NotebookPastureConfigS2C withStatsBackfill(NotebookPastureConfigS2C p,
                                                              NotebookPastureConfigS2C prev) {
        if (prev == null || p.roster() == null || p.roster().isEmpty()) return p;
        boolean upgraded = false;
        java.util.List<com.greenerpastures.pasture.breeding.gui.MonEntry> merged = new java.util.ArrayList<>(p.roster().size());
        for (com.greenerpastures.pasture.breeding.gui.MonEntry m : p.roster()) {
            if (m.stats() == null || m.stats().isEmpty()) {
                com.greenerpastures.pasture.breeding.gui.MonEntry old = null;
                for (com.greenerpastures.pasture.breeding.gui.MonEntry o : prev.roster()) {
                    if (o.id().equals(m.id())) { old = o; break; }
                }
                if (old != null && old.stats() != null && !old.stats().isEmpty()) {
                    merged.add(new com.greenerpastures.pasture.breeding.gui.MonEntry(
                            m.id(), m.species(), m.label(), m.bucket(), old.stats()));
                    upgraded = true;
                    continue;
                }
            }
            merged.add(m);
        }
        return upgraded
                ? new NotebookPastureConfigS2C(p.pos(), p.name(), p.tier(), p.linked(), p.maxPairs(), merged)
                : p;
    }

    /** The focused pasture's Daemon graph JSON (filter/sink nodes + flow edges + mon-node positions), or "" when
     *  none. Rides its own S2C ({@link NotebookGraphS2C}) alongside the config; the bridge folds it into the
     *  {@code pastureConfig} channel as {@code graph}. */
    public static volatile String pastureGraphJson = "";

    /** The focused pasture's extras JSON (health strip + Kernel breeding-meta loadout), "" when none.
     *  Same focus-aware, pos-keyed caching as the config/graph. */
    public static volatile String pastureExtraJson = "";

    public static boolean applyPastureExtra(com.greenerpastures.notebook.net.NotebookPastureExtraS2C p) {
        String json = p.json() == null ? "" : p.json();
        pastureExtraCache.put(posKey(p.pos()), json);
        NotebookPastureConfigS2C cur = pastureConfig;
        if (cur != null && cur.pos() == p.pos()) {   // focused only - prefetches stay cache-only
            pastureExtraJson = json;
            return true;
        }
        return false;
    }

    public static boolean applyGraph(NotebookGraphS2C p) {
        String json = p.json() == null ? "" : p.json();
        pastureGraphCache.put(posKey(p.pos()), json);
        NotebookPastureConfigS2C cur = pastureConfig;
        if (cur != null && cur.pos() == p.pos()) {   // focused only - prefetches stay cache-only
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

    /** The picker meta JSON (#34/#35) riding beside the augmenter push: current selector values + EV spread
     *  on the held Kernel, plus the server-authoritative nature/ball catalogs. */
    public static volatile String augMetaJson = "";

    public static boolean applyAugMeta(com.greenerpastures.notebook.net.NotebookAugmenterMetaS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !augMetaJson.equals(j);
        augMetaJson = j;
        return changed;
    }

    // ── BioBank tab ──────────────────────────────────────────────────────────
    public static volatile int biobankTotal = 0;
    public static volatile List<NotebookBioBankS2C.Entry> biobank = List.of();
    public static volatile List<NotebookBioBankS2C.Press> biobankPresses = List.of();
    public static volatile List<NotebookBioBankS2C.Press> biobankServerPresses = List.of();

    public static boolean applyBiobank(NotebookBioBankS2C p) {
        boolean changed = biobankTotal != p.total() || !biobank.equals(p.entries())
                || !biobankPresses.equals(p.presses()) || !biobankServerPresses.equals(p.serverPresses());
        biobankTotal = p.total();
        biobank = p.entries();
        biobankPresses = p.presses();
        biobankServerPresses = p.serverPresses();
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

    // ── Rituals tab (JSON blob, parsed in React) ─────────────────────────────
    public static volatile String ritualsJson = "";
    public static volatile String specimensJson = "";
    public static volatile String arcadeJson = "";
    public static volatile String treelineJson = "";
    public static volatile String topdeckJson = "";
    public static volatile String slotsJson = "";
    public static volatile String vibeJson = "";
    public static volatile String tagJson = "";

    public static boolean applySpecimens(com.greenerpastures.notebook.net.NotebookSpecimensS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !specimensJson.equals(j);
        specimensJson = j;
        return changed;
    }

    public static boolean applyArcade(com.greenerpastures.notebook.net.NotebookArcadeS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !arcadeJson.equals(j);
        arcadeJson = j;
        return changed;
    }

    public static boolean applyTreeline(com.greenerpastures.notebook.net.NotebookTreelineS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !treelineJson.equals(j);
        treelineJson = j;
        return changed;
    }

    public static boolean applyTopdeck(com.greenerpastures.notebook.net.NotebookTopdeckS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !topdeckJson.equals(j);
        topdeckJson = j;
        return changed;
    }

    public static boolean applySlots(com.greenerpastures.notebook.net.NotebookSlotsS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !slotsJson.equals(j);
        slotsJson = j;
        return changed;
    }

    public static boolean applyVibe(com.greenerpastures.notebook.net.NotebookVibeS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !vibeJson.equals(j);
        vibeJson = j;
        return changed;
    }

    public static boolean applyTag(com.greenerpastures.notebook.net.NotebookTagS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !tagJson.equals(j);
        tagJson = j;
        return changed;
    }

    public static boolean applyRituals(com.greenerpastures.notebook.net.NotebookRitualsS2C p) {
        String j = p.json() == null ? "" : p.json();
        boolean changed = !ritualsJson.equals(j);
        ritualsJson = j;
        return changed;
    }

    // ── Nav (one-shot "open this tab" requests, e.g. the Field Guide item) ──
    public static volatile String navTab = "";
    public static volatile int navSeq = 0;

    /** Ask the React app to switch to {@code tab} on its next frame (seq-bumped so repeats re-fire). */
    public static void navigate(String tab) {
        navTab = tab;
        navSeq++;
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

    /** Wipe the whole client cache - called on world-leave so a new world (or server) never renders the previous
     *  one's data. The pasture caches are keyed by POSITION only, so cross-world they could even collide. */
    public static void clearAll() {
        hasStatus = false; data = 0L; gpu = 0; daemonOn = false;
        hasStorage = false; storage = Map.of(); storageCap = 0L;
        compilerHasDaemon = false; compilerDaemonOn = false; compilerDrain = 0.0;
        compilerCatalog = List.of(); compilerInstalled = Map.of();
        pastures = List.of(); pasturesHealthJson = "";
        pastureConfig = null; pastureConfigLoading = false; pastureGraphJson = ""; pastureExtraJson = "";
        pastureConfigCache.clear(); pastureGraphCache.clear(); pastureExtraCache.clear();
        augHasKernel = false; augTier = ""; augSlotsUsed = 0; augSlotCap = 0; augCatalog = List.of(); augMetaJson = "";
        biobankTotal = 0; biobank = List.of(); biobankPresses = List.of(); biobankServerPresses = List.of();
        eggKept = 0L; eggVoided = 0L; eggLog = List.of();
        dashboardJson = ""; goalsJson = ""; notifsJson = ""; ritualsJson = ""; specimensJson = ""; arcadeJson = ""; treelineJson = ""; topdeckJson = ""; slotsJson = ""; vibeJson = ""; tagJson = "";
        navTab = ""; navSeq = 0;
    }
}
