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
     *  cabinet pots (VF clears 24..5184 by level, TREELINE finds pay ≤600). */
    public static final List<Ware> CATALOG = List.of(
            new Ware("cobblemon:poke_ball",    "Poké Balls ×8",    40,  "⚪", 8),
            new Ware("cobblemon:great_ball",   "Great Balls ×5",   90,  "🔵", 5),
            new Ware("cobblemon:ultra_ball",   "Ultra Balls ×3",   160, "🟡", 3),
            new Ware("cobblemon:potion",       "Potions ×4",       50,  "🧪", 4),
            new Ware("cobblemon:super_potion", "Super Potions ×3", 90,  "🧪", 3),
            new Ware("cobblemon:hyper_potion", "Hyper Potions ×2", 140, "🧪", 2),
            new Ware("cobblemon:full_heal",    "Full Heals ×2",    120, "✨", 2),
            new Ware("cobblemon:revive",       "Revive",           180, "💫", 1),
            new Ware("cobblemon:max_revive",   "Max Revive",       420, "💫", 1),
            new Ware("cobblemon:poison_barb",  "Poison Barb",      500, "🟣", 1),
            new Ware("cobblemon:destiny_knot", "Destiny Knot",     900, "🎀", 1),
            new Ware("cobblemon:everstone",    "Everstone",        250, "🪨", 1),
            new Ware("cobblemon:exp_candy_l",  "Exp. Candy L ×3",  200, "🍬", 3),
            new Ware("cobblemon:rare_candy",   "Rare Candy",       600, "🍭", 1),
            new Ware("cobblemon:lucky_egg",    "Lucky Egg",        750, "🥚", 1),
            new Ware("cobblemon:leftovers",    "Leftovers",        650, "🍎", 1));

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
        long seed = windowIndex(nowMs) * 1_000_003L ^ player.getMostSignificantBits() ^ player.getLeastSignificantBits();
        List<Ware> pool = new ArrayList<>(CATALOG);
        java.util.Collections.shuffle(pool, new Random(seed));
        return List.copyOf(pool.subList(0, Math.min(SLOTS, pool.size())));
    }

    /** Validate a purchase: the ware must be ON this player's current shelves (slot contents are
     *  re-derived server-side - a stale/forged slot index can't buy off-rotation stock). */
    public static Ware wareAt(UUID player, long nowMs, int slot) {
        List<Ware> offers = offersFor(player, nowMs);
        return (slot >= 0 && slot < offers.size()) ? offers.get(slot) : null;
    }
}
