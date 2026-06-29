package com.greenerpastures.ritual;

import java.util.Locale;

/**
 * Tier-1 type-drop (data, from config): a tethered mon of {@code type} adds {@code item} (count uniform in
 * [{@code minQty}, {@code maxQty}]) to its Harvester roll at {@code chancePercent}. Pure data; the roll
 * happens in the Harvester adapter. {@code type} is lowercased to match {@link Composition}.
 */
public record TypeDrop(String type, String item, double chancePercent, int minQty, int maxQty) {
    public TypeDrop {
        type = type == null ? "" : type.toLowerCase(Locale.ROOT);
        chancePercent = Math.max(0.0, Math.min(100.0, chancePercent));
        minQty = Math.max(0, minQty);
        maxQty = Math.max(minQty, maxQty);
    }
}
