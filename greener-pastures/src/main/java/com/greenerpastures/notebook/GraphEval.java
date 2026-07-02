package com.greenerpastures.notebook;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.greenerpastures.egg.oracle.cull.EggCard;

import java.util.UUID;

/**
 * Evaluates a pasture's Daemon graph ({@link com.greenerpastures.pasture.breeding.PastureData#graphJson}) for one
 * bred egg → {@link Route#KEEP} (store in the BioBank) or {@link Route#VOID} (render to Data). The graph is a set
 * of <b>threads</b> (named breeding lines); the egg is routed through the thread that owns its parent mon. Within
 * a thread the pipeline is entered at whatever a MON {@code eggs} port (or a {@code SOURCE} {@code out} port) wires
 * into, then walked filter → filter (following the {@code pass} / {@code void} output that matches) until a sink.
 *
 * <p><b>SACRED:</b> a shiny or unreadable egg is ALWAYS kept, regardless of the graph — you can cull hard for IVs
 * without ever risking a shiny (Deuce's rule). An empty / unwired graph keeps everything (back-compat). A pass
 * output with no wire defaults to KEEP; a void output with no wire defaults to VOID (→ Data).
 */
public final class GraphEval {
    private GraphEval() {}

    public enum Route { KEEP, VOID }
    public record Result(Route route, String rejectedBy) {}

    private static final Gson GSON = new Gson();
    private static final String[] STATS = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};

    public static Result route(String graphJson, UUID monId, EggCard card) {
        if (card == null || card.shiny()) return keep(null);            // SACRED — never lose a shiny / unreadable egg
        if (graphJson == null || graphJson.isEmpty()) return keep(null);
        JsonObject g;
        try { g = GSON.fromJson(graphJson, JsonObject.class); } catch (Exception e) { return keep(null); }
        if (g == null) return keep(null);
        JsonObject thread = findThread(g, monId);                      // the breeding line that owns this egg's parent
        if (thread == null) return keep(null);                         // parent not in any wired line → keep
        JsonArray nodes = thread.getAsJsonArray("nodes"), edges = thread.getAsJsonArray("edges");
        if (nodes == null || edges == null) return keep(null);
        return walk(nodes, edges, card);
    }

    /** The thread whose MON nodes include {@code monId}; falls back to the whole object for the legacy single-graph
     *  format (no {@code threads} array). Null when the mon isn't placed in any thread. */
    private static JsonObject findThread(JsonObject g, UUID monId) {
        JsonArray threads = g.getAsJsonArray("threads");
        if (threads == null) return g.has("nodes") ? g : null;         // legacy { nodes, edges }
        if (monId == null) return null;
        String target = monId.toString();
        for (JsonElement el : threads) {
            JsonObject t = el.getAsJsonObject();
            JsonArray tn = t.getAsJsonArray("nodes");
            if (tn == null) continue;
            for (JsonElement ne : tn) {
                JsonObject n = ne.getAsJsonObject();
                if ("MON".equals(str(n, "type")) && target.equals(str(n, "monId"))) return t;
            }
        }
        return null;
    }

    /** Walk one thread's pipeline for the egg: entry (a MON eggs / SOURCE out wire) → filters → sink. */
    private static Result walk(JsonArray nodes, JsonArray edges, EggCard card) {
        String entry = entryNode(edges);                               // node fed by a MON 'eggs' / SOURCE 'out' port
        if (entry == null) return keep(null);                          // nothing wired to eggs → keep-all fallback
        String id = entry, rejectedBy = null;
        for (int guard = 0; id != null && guard < 64; guard++) {
            JsonObject node = findNode(nodes, id);
            if (node == null) return keep(rejectedBy);
            String type = str(node, "type");
            if ("SINK_BIOBANK".equals(type)) return keep(null);
            if ("SINK_DATA".equals(type)) return new Result(Route.VOID, rejectedBy);
            if (type != null && type.startsWith("FILTER_")) {
                boolean pass = evalFilter(type, node.getAsJsonObject("config"), card);
                if (!pass) rejectedBy = label(type);
                String next = edgeTarget(edges, id, pass ? "pass" : "void");
                if (next == null) return pass ? keep(null) : new Result(Route.VOID, label(type));  // unwired output
                id = next;
            } else return keep(rejectedBy);                            // a MON/SOURCE mid-flow → keep
        }
        return keep(rejectedBy);                                       // guard tripped (cycle) → keep
    }

    private static boolean evalFilter(String type, JsonObject cfg, EggCard card) {
        if (cfg == null) cfg = new JsonObject();
        switch (type) {
            case "FILTER_IV": { int[] iv = card.ivs(); for (int i = 0; i < 6; i++) if (iv[i] < intv(cfg, STATS[i])) return false; return true; }
            case "FILTER_EV": { int[] ev = card.evs(); for (int i = 0; i < 6; i++) if (ev[i] < intv(cfg, STATS[i])) return false; return true; }
            case "FILTER_SHINY": {
                String gate = cfg.has("gate") && !cfg.get("gate").isJsonNull() ? cfg.get("gate").getAsString() : "only";
                return switch (gate) { case "any" -> true; case "no" -> !card.shiny(); default -> card.shiny(); };
            }
            case "FILTER_NATURE": {
                JsonArray list = cfg.getAsJsonArray("list");
                if (list == null || list.size() == 0) return true;
                String nat = card.nature() == null ? "" : card.nature();
                for (JsonElement el : list) if (el.getAsString().equalsIgnoreCase(nat)) return true;
                return false;
            }
            default: return true;
        }
    }

    private static String entryNode(JsonArray edges) {
        for (JsonElement el : edges) {
            JsonObject e = el.getAsJsonObject();
            String fromPort = str(e, "fromPort");
            if ("eggs".equals(fromPort) || "out".equals(fromPort)) return str(e, "to");
        }
        return null;
    }

    private static String edgeTarget(JsonArray edges, String from, String fromPort) {
        for (JsonElement el : edges) {
            JsonObject e = el.getAsJsonObject();
            if (from.equals(str(e, "from")) && fromPort.equals(str(e, "fromPort"))) return str(e, "to");
        }
        return null;
    }

    private static JsonObject findNode(JsonArray nodes, String id) {
        for (JsonElement el : nodes) { JsonObject n = el.getAsJsonObject(); if (id.equals(str(n, "id"))) return n; }
        return null;
    }

    private static int intv(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0; } catch (Exception e) { return 0; }
    }
    private static String str(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; } catch (Exception e) { return null; }
    }
    private static String label(String type) {
        return switch (type) {
            case "FILTER_IV" -> "IV"; case "FILTER_EV" -> "EV"; case "FILTER_NATURE" -> "Nature"; case "FILTER_SHINY" -> "Shiny";
            default -> type;
        };
    }
    private static Result keep(String r) { return new Result(Route.KEEP, r); }
}
