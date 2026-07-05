package com.greenerpastures.ritual;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Snack Repel math (Snack Overdrive pt.1, rev 2 — Deuce, 2026-07-04): the can is CHARGED with typed berries
 * first (count scales magnitude exactly like the ultra cake: per-copy value summed, 6-copy cap = "double a
 * pot cook"), then charged cans merge into the snack (per-type sums, capped at DOUBLE the strongest single
 * can — the double-additive rule everywhere). Pure + tested.
 */
public final class SnackRepelMath {
    private SnackRepelMath() {}

    public static final int COPY_CAP = 6;

    /** Charge magnitude for {@code copies} berries at {@code perCopyValue} each: Σ capped at 6 copies,
     *  rounded; below ÷2 a "repel" does nothing → 0 = invalid charge (recipe refuses). */
    public static int chargeMagnitude(int copies, double perCopyValue) {
        if (copies <= 0 || perCopyValue <= 0) return 0;
        int mag = (int) Math.round(Math.min(copies, COPY_CAP) * perCopyValue);
        return mag < 2 ? 0 : mag;
    }

    /** Merge charged cans into a snack: per-type magnitudes SUM, capped at 2× the strongest single can of
     *  that type (double-additive, just not more). Input order preserved for lore. */
    public static Map<String, Integer> mergeCans(List<Map.Entry<String, Integer>> cans) {
        Map<String, Integer> sum = new LinkedHashMap<>();
        Map<String, Integer> strongest = new LinkedHashMap<>();
        if (cans != null) {
            for (Map.Entry<String, Integer> c : cans) {
                if (c == null || c.getKey() == null || c.getValue() == null || c.getValue() < 2) continue;
                String type = c.getKey().toLowerCase(java.util.Locale.ROOT);
                sum.merge(type, c.getValue(), Integer::sum);
                strongest.merge(type, c.getValue(), Math::max);
            }
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        sum.forEach((t, v) -> out.put(t, Math.min(v, 2 * strongest.get(t))));
        return java.util.Collections.unmodifiableMap(out);
    }
}
