package com.greenerpastures.client.notebook;

import com.greenerpastures.notebook.net.NotebookCompilerS2C;
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

    public static void applyStatus(NotebookStatusS2C p) {
        data = p.data();
        gpu = p.gpu();
        daemonOn = p.daemonOn();
        hasStatus = true;
    }

    // ── Harvester / Storage tab ──────────────────────────────────────────────
    public static volatile boolean hasStorage = false;
    public static volatile Map<String, Long> storage = Map.of();
    public static volatile long storageCap = 0L;

    public static void applyStorage(NotebookStorageS2C p) {
        storage = p.items();
        storageCap = p.capacity();
        hasStorage = true;
    }

    // ── Compiler (Daemon) tab ────────────────────────────────────────────────
    public static volatile boolean compilerHasDaemon = false;
    public static volatile boolean compilerDaemonOn = false;
    public static volatile double compilerDrain = 0.0;
    public static volatile List<NotebookCompilerS2C.Buff> compilerCatalog = List.of();
    public static volatile Map<String, Integer> compilerInstalled = Map.of();

    public static void applyCompiler(NotebookCompilerS2C p) {
        compilerHasDaemon = p.hasDaemon();
        compilerDaemonOn = p.daemonOn();
        compilerDrain = p.drainPerSec();
        compilerCatalog = p.catalog();
        compilerInstalled = p.installed();
    }
}
