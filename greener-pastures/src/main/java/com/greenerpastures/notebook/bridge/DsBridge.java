package com.greenerpastures.notebook.bridge;

import com.google.gson.Gson;
import com.greenerpastures.client.notebook.NotebookState;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.notebook.net.NotebookActionC2S;
import com.greenerpastures.notebook.net.NotebookRequestC2S;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Notebook console's live data bridge (client-side). Runs a loopback WebSocket server
 * ({@link DsWebSocketServer}) that the React UI connects to — a dev browser (`npm run dev`) or, later, MCEF
 * in-game. It mirrors the client's {@link NotebookState} to the browser as JSON {@code state} frames and maps
 * browser {@code action} frames back to the <b>existing</b> C2S packets. So the React app is just a
 * NotebookState consumer (like the owo screen was) — the whole S2C/C2S sync layer is reused untouched.
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

    private static void onClientTick(MinecraftClient client) {
        if (server == null || !server.hasClients()) return;
        if (client.getNetworkHandler() == null) return;                  // not in a world
        tick++;
        if (tick % 20 == 0) ClientPlayNetworking.send(new NotebookRequestC2S(0));   // refresh NotebookState ~1/s
        if (tick % 4 == 0) pushChangedChannels();                        // ~5/s, diffed
    }

    /** A browser just connected — pull fresh data + force a full re-push to it. */
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
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) msg.getOrDefault("payload", Map.of());
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
            case "PULL_ID"        -> new NotebookActionC2S(NotebookActionC2S.PULL_ID, str(p, "item", ""), 0);
            case "SET_BUFF"       -> new NotebookActionC2S(NotebookActionC2S.SET_BUFF, str(p, "buff", ""), tier);
            case "TOGGLE_DAEMON"  -> new NotebookActionC2S(NotebookActionC2S.TOGGLE_DAEMON, "", 0);
            case "APPLY_AUGMENT"  -> new NotebookActionC2S(NotebookActionC2S.APPLY_AUGMENT, str(p, "type", ""), 0);
            case "REMOVE_AUGMENT" -> new NotebookActionC2S(NotebookActionC2S.REMOVE_AUGMENT, str(p, "type", ""), 0);
            case "WITHDRAW"       -> new NotebookActionC2S(NotebookActionC2S.WITHDRAW, "", (int) num(p, "index", 0));
            default -> null;   // DEPOSIT / inventory land when the real inventory channel is added (EGG_PIPELINE_SPEC)
        };
    }

    // ── serialize NotebookState → per-channel JSON, push only what changed ─────────────────────────────────
    private static void pushChangedChannels() {
        push("status", statusData());
        push("storage", storageData());
        push("compiler", compilerData());
        push("pastures", pasturesData());
        push("augmenter", augmenterData());
        push("biobank", biobankData());
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
        return m;
    }

    private static Object augmenterData() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasKernel", NotebookState.augHasKernel);
        m.put("tier", NotebookState.augTier);
        m.put("slotsUsed", NotebookState.augSlotsUsed);
        m.put("slotCap", NotebookState.augSlotCap);
        m.put("catalog", NotebookState.augCatalog);
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
