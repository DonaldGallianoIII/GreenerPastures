package com.greenerpastures.arcade;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The <b>Game Corner shop</b> (Deuce, 2026-07-06): cabinet winnings pay <b>Game Corner Coins</b> - a
 * separate arcade-only currency, NOT Data - and the shop redeems them mobile-game style: each player
 * sees {@link #SLOTS} offers drawn from the catalog, and the stock ROTATES every {@link #WINDOW_MS}
 * (15 real-time minutes).
 *
 * <p>Minecraft-free and deterministic: an offer window is fully determined by (windowIndex, playerId) -
 * the server never stores stock, it derives it; a relog or server restart shows the same shelves until
 * the window turns. Prices are baked constants. Coins cannot buy Data and Data cannot buy coins - the
 * arcade economy is a closed loop that only leaks ITEMS.
 */
public final class GameShop {
    private GameShop() {}

    public static final long WINDOW_MS = 15 * 60 * 1000L;
    public static final int SLOTS = 6;

    /** One catalog entry: registry item id, display name, coin price, display emoji. */
    public record Ware(String itemId, String name, int price, String emoji, int count) {}

    /** The full catalog - every rotation draws SLOTS distinct entries from here. Prices scale off
     *  cabinet pots (VF clears 24..5184 by level; TREELINE is pocket change by design). */
    public static final String MYSTERY_EGG_ID = "greenerpastures:mystery_egg";

    public static final List<Ware> CATALOG = List.of(
            // consumables
            new Ware("cobblemon:poke_ball",    "Poké Balls ×8",    40,  "⚪", 8),
            new Ware("cobblemon:great_ball",   "Great Balls ×5",   90,  "🔵", 5),
            new Ware("cobblemon:ultra_ball",   "Ultra Balls ×3",   160, "🟡", 3),
            new Ware("cobblemon:potion",       "Potions ×4",       50,  "🧪", 4),
            new Ware("cobblemon:super_potion", "Super Potions ×3", 90,  "🧪", 3),
            new Ware("cobblemon:hyper_potion", "Hyper Potions ×2", 140, "🧪", 2),
            new Ware("cobblemon:full_heal",    "Full Heals ×2",    120, "✨", 2),
            new Ware("cobblemon:revive",       "Revive",           180, "💫", 1),
            new Ware("cobblemon:max_revive",   "Max Revive",       420, "💫", 1),
            new Ware("cobblemon:exp_candy_l",  "Exp. Candy L ×3",  200, "🍬", 3),
            new Ware("cobblemon:rare_candy",   "Rare Candy",       600, "🍭", 1),
            // type boosters (the "Poison Barb category" - Deuce 2026-07-06: ALL the held items)
            new Ware("cobblemon:mystic_water",  "Mystic Water",  300, "💧", 1),
            new Ware("cobblemon:magnet",        "Magnet",        300, "🧲", 1),
            new Ware("cobblemon:miracle_seed",  "Miracle Seed",  300, "🌱", 1),
            new Ware("cobblemon:never_melt_ice","Never-Melt Ice",300, "🧊", 1),
            new Ware("cobblemon:black_belt",    "Black Belt",    300, "🥋", 1),
            new Ware("cobblemon:sharp_beak",    "Sharp Beak",    300, "🐦", 1),
            new Ware("cobblemon:poison_barb",   "Poison Barb",   300, "🟣", 1),
            new Ware("cobblemon:soft_sand",     "Soft Sand",     300, "🏖", 1),
            new Ware("cobblemon:hard_stone",    "Hard Stone",    300, "🪨", 1),
            new Ware("cobblemon:silver_powder", "Silver Powder", 300, "🦋", 1),
            new Ware("cobblemon:spell_tag",     "Spell Tag",     300, "👻", 1),
            new Ware("cobblemon:twisted_spoon", "Twisted Spoon", 300, "🥄", 1),
            new Ware("cobblemon:black_glasses", "Black Glasses", 300, "🕶", 1),
            new Ware("cobblemon:dragon_fang",   "Dragon Fang",   300, "🐉", 1),
            new Ware("cobblemon:metal_coat",    "Metal Coat",    300, "⚙", 1),
            new Ware("cobblemon:silk_scarf",    "Silk Scarf",    300, "🧣", 1),
            new Ware("cobblemon:fairy_feather", "Fairy Feather", 300, "🪶", 1),
            new Ware("cobblemon:charcoal_stick","Charcoal Stick",300, "🔥", 1),
            // EV power items
            new Ware("cobblemon:power_band",   "Power Band",   450, "💪", 1),
            new Ware("cobblemon:power_bracer", "Power Bracer", 450, "💪", 1),
            new Ware("cobblemon:power_belt",   "Power Belt",   450, "💪", 1),
            new Ware("cobblemon:power_lens",   "Power Lens",   450, "💪", 1),
            new Ware("cobblemon:power_anklet", "Power Anklet", 450, "💪", 1),
            new Ware("cobblemon:power_weight", "Power Weight", 450, "💪", 1),
            // utility helds
            new Ware("cobblemon:wide_lens",     "Wide Lens",      350, "🔍", 1),
            new Ware("cobblemon:scope_lens",    "Scope Lens",     400, "🔭", 1),
            new Ware("cobblemon:kings_rock",    "King's Rock",    450, "👑", 1),
            new Ware("cobblemon:razor_claw",    "Razor Claw",     450, "🪝", 1),
            new Ware("cobblemon:razor_fang",    "Razor Fang",     450, "🦷", 1),
            new Ware("cobblemon:bright_powder", "Bright Powder",  400, "✨", 1),
            new Ware("cobblemon:quick_claw",    "Quick Claw",     450, "⚡", 1),
            new Ware("cobblemon:shell_bell",    "Shell Bell",     450, "🐚", 1),
            new Ware("cobblemon:big_root",      "Big Root",       350, "🌿", 1),
            new Ware("cobblemon:white_herb",    "White Herb",     350, "🌾", 1),
            new Ware("cobblemon:mental_herb",   "Mental Herb",    350, "🍃", 1),
            new Ware("cobblemon:light_clay",    "Light Clay",     400, "🧱", 1),
            new Ware("cobblemon:safety_goggles","Safety Goggles", 400, "🥽", 1),
            new Ware("cobblemon:air_balloon",   "Air Balloon",    350, "🎈", 1),
            new Ware("cobblemon:muscle_band",   "Muscle Band",    500, "🎽", 1),
            new Ware("cobblemon:wise_glasses",  "Wise Glasses",   500, "👓", 1),
            new Ware("cobblemon:expert_belt",   "Expert Belt",    550, "🥇", 1),
            new Ware("cobblemon:exp_share",     "Exp. Share",     550, "📿", 1),
            new Ware("cobblemon:flame_orb",     "Flame Orb",      400, "🔴", 1),
            new Ware("cobblemon:toxic_orb",     "Toxic Orb",      400, "🟪", 1),
            // competitive tier
            new Ware("cobblemon:choice_band",     "Choice Band",      850, "🎗", 1),
            new Ware("cobblemon:choice_specs",    "Choice Specs",     850, "🤓", 1),
            new Ware("cobblemon:choice_scarf",    "Choice Scarf",     850, "🧣", 1),
            new Ware("cobblemon:life_orb",        "Life Orb",         900, "🔮", 1),
            new Ware("cobblemon:focus_sash",      "Focus Sash",       700, "🎗", 1),
            new Ware("cobblemon:assault_vest",    "Assault Vest",     750, "🦺", 1),
            new Ware("cobblemon:rocky_helmet",    "Rocky Helmet",     600, "⛑", 1),
            new Ware("cobblemon:leftovers",       "Leftovers",        650, "🍎", 1),
            new Ware("cobblemon:eviolite",        "Eviolite",         800, "💎", 1),
            new Ware("cobblemon:heavy_duty_boots","Heavy-Duty Boots", 600, "🥾", 1),
            new Ware("cobblemon:weakness_policy", "Weakness Policy",  700, "📜", 1),
            // breeding + misc
            new Ware("cobblemon:destiny_knot", "Destiny Knot", 900, "🎀", 1),
            new Ware("cobblemon:everstone",    "Everstone",    250, "🪨", 1),
            new Ware("cobblemon:lucky_egg",    "Lucky Egg",    750, "🥚", 1),
            // the Mystery Egg (Deuce's spec: random species · ≥2 perfect IVs · ALWAYS hidden ability)
            new Ware(MYSTERY_EGG_ID, "Mystery Egg", 1200, "❓", 1));

    /** Current rotation window index for a wall-clock instant. */
    public static long windowIndex(long nowMs) {
        return Math.floorDiv(nowMs, WINDOW_MS);
    }

    /** When the current window's stock rotates away (epoch ms). */
    public static long windowEndsAt(long nowMs) {
        return (windowIndex(nowMs) + 1) * WINDOW_MS;
    }

    /** This player's shelves for the window holding {@code nowMs}: SLOTS distinct wares, order stable.
     *  Deterministic per (window, player) - "the person's shop", rotating together every 15 minutes. */
    public static List<Ware> offersFor(UUID player, long nowMs) {
        return offersFor(player, nowMs, true, 0);
    }

    /** {@code includeEgg=false} when Cobbreeding is absent - offer derivation and purchase validation
     *  MUST use the same flag or a shelf index could point at different wares. */
    public static List<Ware> offersFor(UUID player, long nowMs, boolean includeEgg) {
        return offersFor(player, nowMs, includeEgg, 0);
    }

    /** {@code rolls} = the player's lifetime purchase count (Deuce 2026-07-06: every buy refreshes the
     *  whole shelf, mobile-style, on top of the 15-minute rotation). Persisted in ArcadeStore so the
     *  refreshed shelves survive relog; derivation and validation MUST pass the same value. */
    public static List<Ware> offersFor(UUID player, long nowMs, boolean includeEgg, long rolls) {
        long seed = windowIndex(nowMs) * 1_000_003L
                ^ player.getMostSignificantBits() ^ player.getLeastSignificantBits()
                ^ rolls * 0x9E3779B97F4A7C15L;
        List<Ware> pool = new ArrayList<>(CATALOG);
        if (!includeEgg) pool.removeIf(w -> MYSTERY_EGG_ID.equals(w.itemId()));
        java.util.Collections.shuffle(pool, new Random(seed));
        return List.copyOf(pool.subList(0, Math.min(SLOTS, pool.size())));
    }

    /** Validate a purchase: the ware must be ON this player's current shelves (slot contents are
     *  re-derived server-side - a stale/forged slot index can't buy off-rotation stock). */
    public static Ware wareAt(UUID player, long nowMs, int slot, boolean includeEgg, long rolls) {
        List<Ware> offers = offersFor(player, nowMs, includeEgg, rolls);
        return (slot >= 0 && slot < offers.size()) ? offers.get(slot) : null;
    }

    /** Rolls-free overload kept for the pre-refresh callers/tests. */
    public static Ware wareAt(UUID player, long nowMs, int slot, boolean includeEgg) {
        return wareAt(player, nowMs, slot, includeEgg, 0);
    }
}
