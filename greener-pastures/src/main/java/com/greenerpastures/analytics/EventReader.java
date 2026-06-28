package com.greenerpastures.analytics;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses {@code events.jsonl} lines back into {@link EggEvent}s for the Dashboard — Minecraft-free
 * (uses the project's existing Gson), so the whole read → aggregate → export pipeline is unit-tested.
 * The only adapter on top is the command/GUI that supplies the file's lines + the save path.
 */
public final class EventReader {
    private EventReader() {}

    private static final Gson GSON = new Gson();
    private static final String EGG_LAID = "egg_laid";

    /** One log line → an {@link EggEvent}, or {@code null} if it isn't an {@code egg_laid} event / is malformed. */
    public static EggEvent parseEggEvent(String line) {
        if (line == null || line.isBlank()) return null;
        try {
            JsonObject o = GSON.fromJson(line, JsonObject.class);
            if (o == null || !EGG_LAID.equals(asString(o, "type"))) return null;
            return new EggEvent(asString(o, "tier"), asString(o, "mode"), asBool(o, "shiny"), asBool(o, "proc_shiny"));
        } catch (JsonSyntaxException | IllegalStateException e) {
            return null;   // garbage line — skip it
        }
    }

    /** Read many log lines into {@link EggEvent}s, skipping non-egg + malformed lines. */
    public static List<EggEvent> readEggEvents(Iterable<String> lines) {
        List<EggEvent> out = new ArrayList<>();
        for (String line : lines) {
            EggEvent e = parseEggEvent(line);
            if (e != null) out.add(e);
        }
        return out;
    }

    private static String asString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return (e != null && e.isJsonPrimitive()) ? e.getAsString() : null;
    }

    private static boolean asBool(JsonObject o, String key) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() && e.getAsBoolean();
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
