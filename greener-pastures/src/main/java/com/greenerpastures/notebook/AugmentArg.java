package com.greenerpastures.notebook;

/**
 * The parsed argument of a console <b>APPLY_AUGMENT</b> action (#34/#35) - plain {@code "SHINY"} for the
 * magnitude augments, {@code "NATURE:7"} / {@code "BALL:12"} for the selector pickers (1-based catalog index),
 * {@code "EV:hp,atk,def,spa,spd,spe"} for the EV allocator. <b>Minecraft-free</b> so the whole grammar is
 * unit-tested headless; {@code NotebookNet} maps {@code type} to an {@code AugmentType} and validates ranges
 * against the (pure) catalogs.
 *
 * <p>{@link #parse} returns {@code null} for anything malformed - a bad console payload must reject cleanly,
 * never install garbage on a Kernel.
 */
public record AugmentArg(String type, int index, int[] ev) {

    /** Parse {@code "TYPE"}, {@code "TYPE:<index>"} or {@code "EV:<6 csv ints>"}; {@code null} if malformed. */
    public static AugmentArg parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        int colon = raw.indexOf(':');
        if (colon < 0) return new AugmentArg(raw.trim(), 0, null);

        String type = raw.substring(0, colon).trim();
        String rest = raw.substring(colon + 1).trim();
        if (type.isEmpty() || rest.isEmpty()) return null;

        if (type.equalsIgnoreCase("EV")) {
            String[] parts = rest.split(",");
            if (parts.length != 6) return null;
            int[] ev = new int[6];
            int total = 0;
            for (int i = 0; i < 6; i++) {
                try {
                    ev[i] = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException e) {
                    return null;
                }
                if (ev[i] < 0) return null;
                total += ev[i];
            }
            if (total <= 0) return null;   // an all-zero spread is a REMOVE, not an install
            return new AugmentArg(type, 0, ev);
        }

        try {
            int idx = Integer.parseInt(rest);
            if (idx <= 0) return null;     // selector indexes are 1-based; 0 = "off" comes via REMOVE
            return new AugmentArg(type, idx, null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
