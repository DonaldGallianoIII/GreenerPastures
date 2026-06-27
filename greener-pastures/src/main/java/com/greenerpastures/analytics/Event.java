package com.greenerpastures.analytics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A single analytics event: a {@code type} plus an ordered bag of flat fields. Modules build these
 * fluently and hand them to {@link Analytics#record}. Deliberately schema-light so any module can
 * emit without a central registry; the aggregation/export layer interprets known types later.
 *
 * <p>Keep field values flat (String / number / boolean) so the JSONL log loads cleanly into
 * pandas or a spreadsheet.
 */
public final class Event {
    final String type;
    final Map<String, Object> fields = new LinkedHashMap<>();

    private Event(String type) {
        this.type = type;
    }

    public static Event of(String type) {
        return new Event(type);
    }

    public Event put(String key, Object value) {
        fields.put(key, value);
        return this;
    }

    /** Convenience for the owning player / OT, stored as a string UUID. */
    public Event player(UUID id) {
        return put("player", id == null ? null : id.toString());
    }
}
