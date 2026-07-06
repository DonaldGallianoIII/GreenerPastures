package com.greenerpastures.arcade;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-saved Game Corner ledger: each player's machine level + how much the house has paid them
 * TODAY (real-world UTC date - a Minecraft day is 20 minutes, which would make the kB cap a joke).
 * Live {@link VoltorbFlip.Board}s are deliberately NOT persisted - a relog mid-round deals a fresh
 * board at the same level, which also closes the "peek then relog" save-scum before it exists.
 */
public final class ArcadeStore extends PersistentState {
    private static final String ID = "greenerpastures_arcade";

    /** Per-player ledger row. {@code coins} = Game Corner Coins (Deuce 2026-07-06): the arcade's OWN
     *  currency - never convertible to/from Data; spent only at the rotating shop. */
    public static final class Ledger {
        public int level = 1;
        public long earnedToday = 0;
        public String day = "";
        public long coins = 0;
    }

    private final Map<UUID, Ledger> ledgers = new HashMap<>();

    /** The player's row, day-rolled: a new UTC date zeroes {@code earnedToday}. */
    public Ledger of(UUID player, String today) {
        Ledger l = ledgers.computeIfAbsent(player, u -> new Ledger());
        if (!today.equals(l.day)) {
            l.day = today;
            l.earnedToday = 0;
            markDirty();
        }
        return l;
    }

    public void record(UUID player, String today, long paid, int newLevel) {
        Ledger l = of(player, today);
        l.earnedToday += Math.max(0, paid);
        l.coins += Math.max(0, paid);
        l.level = newLevel;
        markDirty();
    }

    /** Spend coins at the shop; false (and no change) when the balance can't cover it. */
    public boolean trysSpend(UUID player, String today, long price) {
        Ledger l = of(player, today);
        if (price < 0 || l.coins < price) return false;
        l.coins -= price;
        markDirty();
        return true;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound all = new NbtCompound();
        ledgers.forEach((u, l) -> {
            NbtCompound row = new NbtCompound();
            row.putInt("level", l.level);
            row.putLong("earned", l.earnedToday);
            row.putString("day", l.day);
            row.putLong("coins", l.coins);
            all.put(u.toString(), row);
        });
        nbt.put("ledgers", all);
        return nbt;
    }

    private static ArcadeStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        ArcadeStore s = new ArcadeStore();
        NbtCompound all = nbt.getCompound("ledgers");
        for (String k : all.getKeys()) {
            try {
                NbtCompound row = all.getCompound(k);
                Ledger l = new Ledger();
                l.level = Math.max(1, Math.min(VoltorbFlip.MAX_LEVEL, row.getInt("level")));
                l.earnedToday = Math.max(0, row.getLong("earned"));
                l.day = row.getString("day");
                l.coins = Math.max(0, row.getLong("coins"));
                s.ledgers.put(UUID.fromString(k), l);
            } catch (IllegalArgumentException ignored) { }
        }
        return s;
    }

    private static final Type<ArcadeStore> TYPE = new Type<>(ArcadeStore::new, ArcadeStore::fromNbt, null);

    public static ArcadeStore get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
