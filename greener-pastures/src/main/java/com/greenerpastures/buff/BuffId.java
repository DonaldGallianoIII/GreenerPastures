package com.greenerpastures.buff;

/**
 * The catalog of Daemon "root" buffs — the <b>worker-not-fighter</b> QOL/farming boosts a held, fed Daemon
 * grants (rented via Data: lose fuel → lose the buff). Each constant is one buff: an {@link BuffCategory#ENCHANT}
 * that rides a vanilla enchant {@code tier} levels past its normal max, an {@link BuffCategory#EFFECT} status
 * effect, or a mod-implemented {@link BuffCategory#HOOK}.
 *
 * <p>Pure data (ids/strings only) so the cores + tests stay MC-free; the MC adapter maps {@link #registryId} to
 * the actual {@code Enchantment}/{@code StatusEffect}. <b>By design this catalog contains ONLY the included
 * buffs</b> — every combat enchant (Sharpness, Protection, Power, Knockback, Thorns, trident, crossbow…) and
 * every binary/already-capped one (Silk Touch, Mending, Infinity, Flame, Channeling, Aqua Affinity, Depth
 * Strider) is simply absent, so a "+N" can never apply to them. This keeps the mod PvP-neutral.
 *
 * <p>Source of truth: {@code ~/pokemonthink/AUGMENTS_AND_BUFFS.md}.
 */
public enum BuffId {
    // ── ENCHANT buffs: ride a vanilla enchant +tier beyond its vanilla max ──
    // gathering (economy-impacting → flagged so shop servers know to cap these individually)
    EFFICIENCY     ("efficiency",      "Efficiency",      BuffCategory.ENCHANT, "minecraft:efficiency",      5, true),
    FORTUNE        ("fortune",         "Fortune",         BuffCategory.ENCHANT, "minecraft:fortune",         3, true),
    LUCK_OF_THE_SEA("luck_of_the_sea", "Luck of the Sea", BuffCategory.ENCHANT, "minecraft:luck_of_the_sea", 3, true),
    LURE           ("lure",            "Lure",            BuffCategory.ENCHANT, "minecraft:lure",            3, true),
    UNBREAKING     ("unbreaking",      "Unbreaking",      BuffCategory.ENCHANT, "minecraft:unbreaking",      3, true),
    LOOTING        ("looting",         "Looting",         BuffCategory.ENCHANT, "minecraft:looting",         3, true),
    // QOL / movement (no economy impact)
    RESPIRATION    ("respiration",     "Respiration",     BuffCategory.ENCHANT, "minecraft:respiration",     3, false),
    SOUL_SPEED     ("soul_speed",      "Soul Speed",      BuffCategory.ENCHANT, "minecraft:soul_speed",      3, false),
    SWIFT_SNEAK    ("swift_sneak",     "Swift Sneak",     BuffCategory.ENCHANT, "minecraft:swift_sneak",     3, false),
    FROST_WALKER   ("frost_walker",    "Frost Walker",    BuffCategory.ENCHANT, "minecraft:frost_walker",    2, false),
    FEATHER_FALLING("feather_falling", "Feather Falling", BuffCategory.ENCHANT, "minecraft:feather_falling", 4, false),

    // ── EFFECT buffs: vanilla status effects applied while the Daemon is held ──
    HASTE          ("haste",           "Haste",           BuffCategory.EFFECT,  "minecraft:haste",           0, false),
    SATURATION     ("saturation",      "Saturation",      BuffCategory.EFFECT,  "minecraft:saturation",      0, false),

    // ── HOOK buffs: mod-implemented mechanics (no vanilla registry entry) ──
    AUTO_SMELT     ("auto_smelt",      "Auto-Smelt",      BuffCategory.HOOK,    null,                        0, true),
    VEIN_MINE      ("vein_mine",       "Vein Miner",      BuffCategory.HOOK,    null,                        0, true),
    MAGNET         ("magnet",          "Item Magnet",     BuffCategory.HOOK,    null,                        0, false),
    XP_BOOST       ("xp_boost",        "XP Boost",        BuffCategory.HOOK,    null,                        0, false),
    POTION_DURATION("potion_duration", "Potion Duration", BuffCategory.HOOK,    null,                        0, false);

    /** Stable string key used in config JSON / NBT — lowercase, matches everything else in the mod. */
    public final String id;
    /** Human label for tooltips / GpLog. */
    public final String label;
    public final BuffCategory category;
    /** Vanilla enchant id (ENCHANT) or status-effect id (EFFECT); {@code null} for HOOK. An MC-free string. */
    public final String registryId;
    /** Vanilla max level of the rode enchant (ENCHANT only) — the adapter adds the resolved tier on top. 0 otherwise. */
    public final int vanillaMax;
    /** Economy-impacting "gathering" buff (Fortune/Looting/auto-smelt/…) — admins may cap these on shop servers. */
    public final boolean gathering;

    BuffId(String id, String label, BuffCategory category, String registryId, int vanillaMax, boolean gathering) {
        this.id = id;
        this.label = label;
        this.category = category;
        this.registryId = registryId;
        this.vanillaMax = vanillaMax;
        this.gathering = gathering;
    }

    /** Look up a buff by its stable id; null if unknown (e.g. a stale config key). */
    public static BuffId byId(String id) {
        if (id == null) return null;
        for (BuffId b : values()) {
            if (b.id.equals(id)) return b;
        }
        return null;
    }
}
