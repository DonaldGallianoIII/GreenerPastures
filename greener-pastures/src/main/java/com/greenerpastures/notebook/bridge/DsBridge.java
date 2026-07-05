package com.greenerpastures.notebook.bridge;

import com.google.gson.Gson;
import com.greenerpastures.client.notebook.NotebookBrowserScreen;
import com.greenerpastures.client.notebook.NotebookState;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.notebook.net.NotebookActionC2S;
import com.greenerpastures.notebook.net.NotebookEggLogS2C;
import com.greenerpastures.notebook.net.NotebookGoalC2S;
import com.greenerpastures.notebook.net.NotebookGraphSaveC2S;
import com.greenerpastures.notebook.net.NotebookPastureActionC2S;
import com.greenerpastures.notebook.net.NotebookPastureConfigS2C;
import com.greenerpastures.notebook.net.NotebookRequestC2S;
import com.greenerpastures.pasture.breeding.gui.MonEntry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Notebook console's live data bridge (client-side). Runs a loopback WebSocket server
 * ({@link DsWebSocketServer}) that the React UI connects to - a dev browser (`npm run dev`) or, later, MCEF
 * in-game. It mirrors the client's {@link NotebookState} to the browser as JSON {@code state} frames and maps
 * browser {@code action} frames back to the <b>existing</b> C2S packets. So the React app is just a
 * NotebookState consumer (like the owo screen was) - the whole S2C/C2S sync layer is reused untouched.
 *
 * <p>Client-only: {@link #init} is called from {@code GreenerPasturesClient}, never on a dedicated server.
 */
public final class DsBridge {
    private DsBridge() {}

    private static final int DEV_PORT = 25599;      // matches the React SDK's dev default (VITE_DS_PORT)
    private static final String DEV_TOKEN = "dev";
    private static final Gson GSON = new Gson();

    private static DsWebSocketServer server;
    private static final Map<String, String> lastSent = new LinkedHashMap<>();
    private static int tick;

    public static void init() {
        server = new DsWebSocketServer(DEV_PORT, DEV_TOKEN, DsBridge::onMessage, DsBridge::onConnect);
        server.start();
        ClientTickEvents.END_CLIENT_TICK.register(DsBridge::onClientTick);
    }

    /** {@code -Dgreenerpastures.devbridge=true} keeps the pipeline always-on for the external `npm run dev`
     *  browser workflow (which connects while no in-game console is open). */
    private static final boolean DEV_ALWAYS_ON = Boolean.getBoolean("greenerpastures.devbridge");

    private static void onClientTick(MinecraftClient client) {
        if (server == null || !server.hasClients()) return;
        if (client.getNetworkHandler() == null) return;                  // not in a world
        // Idle-off (perf-audit R3 S1): the warm PRELOAD browser holds a WS connection for the whole session,
        // so "has a client" is always true - gate the serialize+poll pipeline on the console actually being
        // open. Reopening primes instantly (NotebookBrowserScreen.init sends a request + pushNow()).
        if (!DEV_ALWAYS_ON && !NotebookBrowserScreen.consoleOpen) return;
        tick++;
        if (tick % 20 == 0) ClientPlayNetworking.send(new NotebookRequestC2S(0));   // refresh NotebookState ~1/s
        if (tick % 4 == 0) pushChangedChannels();                        // ~5/s, diffed
    }

    /** A browser just connected - pull fresh data + force a full re-push to it. */
    private static void onConnect() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.getNetworkHandler() != null) ClientPlayNetworking.send(new NotebookRequestC2S(0));
            lastSent.clear();
            pushChangedChannels();
        });
    }

    /** Map a browser action frame → the existing C2S action packet (hopped onto the client thread). */
    private static void onMessage(DsWebSocketServer srv, String text) {
        MinecraftClient.getInstance().execute(() -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = GSON.fromJson(text, Map.class);
                if (msg == null || !"action".equals(msg.get("type"))) return;
                String channel = (String) msg.get("channel");
                String action = (String) msg.get("action");
                if ("CLOSE_CONSOLE".equals(action)) {   // the React window's ✕ → close the in-game screen
                    MinecraftClient.getInstance().setScreen(null);
                    return;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) msg.getOrDefault("payload", Map.of());
                if ("INPUT_FOCUS".equals(action)) { NotebookBrowserScreen.browserInputFocused = Boolean.TRUE.equals(p.get("v")); return; }
                if ("PASTURE_READY".equals(action)) { NotebookBrowserScreen.pastureReady(); return; }   // React painted the pasture view → lift the loading overlay
                if ("pasture".equals(channel)) { handlePastureAction(action, p); return; }
                if ("goals".equals(channel)) { handleGoalAction(action, p); return; }
                NotebookActionC2S packet = mapAction(action, p);
                if (packet == null) {
                    GpLog.d("bridge", "action_unmapped", "channel", String.valueOf(channel), "action", String.valueOf(action));
                } else if (MinecraftClient.getInstance().getNetworkHandler() != null) {
                    ClientPlayNetworking.send(packet);
                }
            } catch (Throwable t) {
                GpLog.w("bridge", "action_err", "err", String.valueOf(t));
            }
        });
    }

    private static NotebookActionC2S mapAction(String action, Map<String, Object> p) {
        int tier = (int) num(p, "tier", 0);
        return switch (action == null ? "" : action) {
            case "PULL_ONE"       -> new NotebookActionC2S(NotebookActionC2S.PULL_ONE, str(p, "item", ""), 0);
            case "PULL_STACK"     -> new NotebookActionC2S(NotebookActionC2S.PULL_STACK, str(p, "item", ""), 0);
            case "PULL_ID"        -> new NotebookActionC2S(NotebookActionC2S.PULL_ID, str(p, "item", ""), 0);
            case "SET_BUFF"       -> new NotebookActionC2S(NotebookActionC2S.SET_BUFF, str(p, "buff", ""), tier);
            case "TOGGLE_DAEMON"  -> new NotebookActionC2S(NotebookActionC2S.TOGGLE_DAEMON, "", 0);
            case "APPLY_AUGMENT"  -> new NotebookActionC2S(NotebookActionC2S.APPLY_AUGMENT, str(p, "type", ""), 0);
            case "REMOVE_AUGMENT" -> new NotebookActionC2S(NotebookActionC2S.REMOVE_AUGMENT, str(p, "type", ""), 0);
            case "WITHDRAW"       -> new NotebookActionC2S(NotebookActionC2S.WITHDRAW, "", (int) num(p, "index", 0));
            case "DISMISS_NOTE"   -> new NotebookActionC2S(NotebookActionC2S.DISMISS_NOTE, str(p, "id", "all"), 0);
            case "WRITE_DISK"     -> new NotebookActionC2S(NotebookActionC2S.WRITE_DISK, str(p, "denom", ""), 0);
            case "RITUAL_PULL"    -> new NotebookActionC2S(NotebookActionC2S.RITUAL_PULL, str(p, "item", ""), (int) num(p, "mode", 0));
            case "CORRUPT_KERNEL" -> new NotebookActionC2S(NotebookActionC2S.CORRUPT_KERNEL, "", 0);
            case "COMPRESS_MON"   -> new NotebookActionC2S(NotebookActionC2S.COMPRESS_MON, "", (int) num(p, "slot", -1));
            case "SUMMON_MISSINGNO" -> new NotebookActionC2S(NotebookActionC2S.SUMMON_MISSINGNO, "", 0);
            case "SET_KERNEL_TARGET" -> new NotebookActionC2S(NotebookActionC2S.SET_KERNEL_TARGET, "", (int) num(p, "slot", -1));
            case "SET_DAEMON_TARGET" -> new NotebookActionC2S(NotebookActionC2S.SET_DAEMON_TARGET, "", (int) num(p, "slot", -1));
            default -> null;   // DEPOSIT / inventory land when the real inventory channel is added (EGG_PIPELINE_SPEC)
        };
    }

    /** Route a React {@code pasture} action → the NotebookPastureActionC2S packet (or clear the focus locally). */
    private static void handlePastureAction(String action, Map<String, Object> p) {
        if ("CLOSE".equals(action)) {                          // ← back to the tabbed console
            NotebookState.pastureConfig = null;
            lastSent.remove("pastureConfig");
            pushChangedChannels();
            return;
        }
        if ("GRAPH".equals(action)) {                          // React node editor saved the Daemon graph
            long gpos = (long) num(p, "pos", 0);
            String json = str(p, "json", "");
            if (MinecraftClient.getInstance().getNetworkHandler() != null)
                ClientPlayNetworking.send(new NotebookGraphSaveC2S(gpos, json));
            return;
        }
        long pos = (long) num(p, "pos", 0);
        Map<UUID, Integer> pairings = new HashMap<>();
        String arg = "";
        int act;
        switch (action == null ? "" : action) {
            case "NAME" -> { act = NotebookPastureActionC2S.NAME; arg = str(p, "name", ""); }
            case "PAIRINGS" -> {
                act = NotebookPastureActionC2S.PAIRINGS;
                Object pr = p.get("pairings");
                if (pr instanceof Map<?, ?> pm) {
                    for (Map.Entry<?, ?> e : pm.entrySet()) {
                        try { pairings.put(UUID.fromString(String.valueOf(e.getKey())), ((Number) e.getValue()).intValue()); }
                        catch (Exception ignored) { }
                    }
                }
            }
            case "CLAIM"  -> act = NotebookPastureActionC2S.CLAIM;
            case "KERNEL" -> act = NotebookPastureActionC2S.KERNEL;
            default -> { return; }
        }
        if (MinecraftClient.getInstance().getNetworkHandler() != null)
            ClientPlayNetworking.send(new NotebookPastureActionC2S(pos, act, arg, pairings));
    }

    /** Route a React {@code goals} action → NotebookGoalC2S (set or clear the breeding goal). */
    private static void handleGoalAction(String action, Map<String, Object> p) {
        if (MinecraftClient.getInstance().getNetworkHandler() == null) return;
        Map<String, Object> spec = new LinkedHashMap<>();
        if ("CLEAR".equals(action)) spec.put("clear", true);
        else {
            spec.put("species", str(p, "species", ""));
            spec.put("shiny", (int) num(p, "shiny", -1));
            spec.put("minPerfect", (int) num(p, "minPerfect", 0));
            spec.put("minIvTotal", (int) num(p, "minIvTotal", 0));
            spec.put("count", (int) num(p, "count", 1));
        }
        ClientPlayNetworking.send(new NotebookGoalC2S(GSON.toJson(spec)));
    }

    /** Force an immediate broadcast - used when a pasture is right-clicked so its config view appears at once,
     *  without waiting for the next diff-push tick (so the previously-open tab doesn't linger). */
    public static void pushNow() {
        if (server != null && server.hasClients()) pushChangedChannels();
    }

    /** World-leave: forget the diff baseline so EVERY channel re-broadcasts fresh (post-clear) values - the React
     *  app must not keep rendering the previous world's data into the next one. */
    public static void onWorldLeave() {
        lastSent.clear();
        pushNow();   // pastureConfig present:false etc. reach the (kept-alive) browser immediately
    }

    // ── serialize NotebookState → per-channel JSON, push only what changed ─────────────────────────────────
    private static void pushChangedChannels() {
        try (var span = com.greenerpastures.core.GpProf.begin("bridge.serialize")) {
            pushAllChannels();
        }
    }

    private static void pushAllChannels() {
        push("status", statusData());
        push("storage", storageData());
        push("compiler", compilerData());
        push("pastures", pasturesData());
        push("augmenter", augmenterData());
        push("biobank", biobankData());
        push("inventory", inventoryData());
        push("pastureConfig", pastureConfigData());
        push("eggLog", eggLogData());
        push("dashboard", jsonChannel(NotebookState.dashboardJson));
        push("goals", jsonChannel(NotebookState.goalsJson));
        push("notifications", jsonChannel(NotebookState.notifsJson));
        push("rituals", jsonChannel(NotebookState.ritualsJson));
        push("specimens", jsonChannel(NotebookState.specimensJson));
        push("nav", navData());
    }

    /** One-shot tab-navigation requests (Field Guide item → Guide tab); n bumps so the same tab re-fires. */
    private static Object navData() {
        if (NotebookState.navSeq == 0) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tab", NotebookState.navTab);
        m.put("n", NotebookState.navSeq);
        return m;
    }

    /** Pass a server-built JSON blob (dashboard / goals) straight through as a channel object (React parses it). */
    private static Object jsonChannel(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return GSON.fromJson(s, com.google.gson.JsonObject.class); } catch (Exception e) { return null; }
    }

    /** The viewing player's recent egg-ingest feed (kept/voided + which filter) + totals - the console Log view. */
    private static Object eggLogData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kept", NotebookState.eggKept);
        m.put("voided", NotebookState.eggVoided);
        List<Object> entries = new ArrayList<>();
        for (NotebookEggLogS2C.Entry e : NotebookState.eggLog) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("species", e.species());
            o.put("voided", e.voided());
            o.put("filter", e.filter());
            entries.add(o);
        }
        m.put("entries", entries);
        return m;
    }

    /** The focused pasture's editable config (right-clicked with the Notebook), or {@code present:false} when none. */
    private static Object pastureConfigData() {
        NotebookPastureConfigS2C c = NotebookState.pastureConfig;
        Map<String, Object> m = new LinkedHashMap<>();
        if (c == null) { m.put("present", false); return m; }
        m.put("present", true);
        m.put("loading", NotebookState.pastureConfigLoading);   // real config still round-tripping → UI shows "loading…"
        m.put("pos", c.pos());
        m.put("name", c.name());
        m.put("tier", c.tier());
        m.put("linked", c.linked());
        m.put("maxPairs", c.maxPairs());
        List<Object> roster = new ArrayList<>();
        for (MonEntry mon : c.roster()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", mon.id().toString());
            r.put("species", mon.species());
            r.put("label", mon.label());
            r.put("bucket", mon.bucket());
            r.put("stats", jsonChannel(mon.stats()));   // parent inspector: ivs/nature/gender/shiny/ot
            roster.add(r);
        }
        m.put("roster", roster);
        m.put("graph", NotebookState.pastureGraphJson);   // the Daemon graph JSON (React parses it) - Phase 1
        Object extra = jsonChannel(NotebookState.pastureExtraJson);   // #37/#34/#35: health strip + Kernel loadout
        if (extra instanceof com.google.gson.JsonObject jo) {
            m.put("health", jo.get("health"));
            if (jo.has("kernel")) m.put("kernel", jo.get("kernel"));
        }
        return m;
    }

    /** The player's REAL inventory (read client-side): 36 main slots - [0..8] hotbar, [9..35] main - so the console's
     *  inventory window mirrors what the player actually holds (Deuce, 2026-07-01), not mock data. */
    private static Object inventoryData() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return null;
        var main = client.player.getInventory().main;
        List<Object> slots = new ArrayList<>(36);
        for (int i = 0; i < 36 && i < main.size(); i++) {
            ItemStack s = main.get(i);
            if (s.isEmpty()) { slots.add(null); continue; }
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("id", Registries.ITEM.getId(s.getItem()).toString());
            slot.put("count", s.getCount());
            slots.add(slot);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slots", slots);
        return m;
    }

    private static void push(String channel, Object data) {
        if (data == null) return;
        String json = GSON.toJson(data);
        if (json.equals(lastSent.get(channel))) return;                  // unchanged → don't broadcast
        lastSent.put(channel, json);
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "state");
        frame.put("channel", channel);
        frame.put("data", data);
        server.broadcast(GSON.toJson(frame));
    }

    private static Object statusData() {
        if (!NotebookState.hasStatus) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data", NotebookState.data);
        m.put("gpu", NotebookState.gpu);
        m.put("daemonOn", NotebookState.daemonOn);
        return m;
    }

    private static Object storageData() {
        if (!NotebookState.hasStorage) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("items", NotebookState.storage);
        m.put("capacity", NotebookState.storageCap);
        return m;
    }

    private static Object compilerData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasDaemon", NotebookState.compilerHasDaemon);
        m.put("daemonOn", NotebookState.compilerDaemonOn);
        m.put("drainPerSec", NotebookState.compilerDrain);
        m.put("catalog", NotebookState.compilerCatalog);
        m.put("installed", NotebookState.compilerInstalled);
        return m;
    }

    private static Object pasturesData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pastures", NotebookState.pastures);
        m.put("health", jsonChannel(NotebookState.pasturesHealthJson));   // #37 - {"dim|pos":"flagId,flagId"} badge markers
        return m;
    }

    private static Object augmenterData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasKernel", NotebookState.augHasKernel);
        m.put("tier", NotebookState.augTier);
        m.put("slotsUsed", NotebookState.augSlotsUsed);
        m.put("slotCap", NotebookState.augSlotCap);
        m.put("catalog", NotebookState.augCatalog);
        m.put("meta", jsonChannel(NotebookState.augMetaJson));   // picker meta (#34/#35): current values + nature/ball catalogs
        return m;
    }

    private static Object biobankData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", NotebookState.biobankTotal);
        m.put("entries", NotebookState.biobank);
        return m;
    }

    private static String str(Map<String, Object> p, String k, String def) {
        Object v = p.get(k);
        return v instanceof String ? (String) v : def;
    }

    private static double num(Map<String, Object> p, String k, double def) {
        Object v = p.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : def;
    }
}
