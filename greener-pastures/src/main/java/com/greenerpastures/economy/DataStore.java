package com.greenerpastures.economy;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-saved per-player <b>Data</b> balances - the dark economy's one currency, earned only from
 * rendered (culled) eggs. This is the source of truth; the Daemon item is the player's handle that
 * displays + spends it (player-bound, so the balance survives losing the item). Mirrors
 * {@code BioBankStore} / {@code PastureRegistry} - persisted in the overworld save.
 *
 * <p>The per-player {@link DataAccount} carries the never-negative / saturating math (unit-tested); this
 * class is just the MC persistence adapter around it.
 */
public final class DataStore extends PersistentState {
    private static final String ID = "greenerpastures_data";

    private final Map<UUID, DataAccount> accounts = new HashMap<>();
    /** Lifetime Data EARNED from rendering (never decremented; disk reads/QA mints deliberately excluded -
     *  a write→read cycle must not pump this). Gates MissingNo. summons: one per million, forever. */
    private final Map<UUID, Long> lifetimeEarned = new HashMap<>();
    private final Map<UUID, Integer> missingnoClaimed = new HashMap<>();

    /** This player's account, creating an empty one on first touch. */
    public DataAccount accountOf(UUID player) {
        return accounts.computeIfAbsent(player, u -> { markDirty(); return new DataAccount(0L); });
    }

    /** Read-only balance - does NOT create an account (returns 0 for an unseen player). */
    public long balanceOf(UUID player) {
        DataAccount a = accounts.get(player);
        return a == null ? 0L : a.balance();
    }

    /** Credit a player's Data (e.g. from a Renderer). No-op for non-positive amounts. */
    public void credit(UUID player, long amount) {
        if (amount <= 0) return;
        accountOf(player).credit(amount);
        markDirty();
    }

    /** Credit RENDER income: balance + the lifetime-earned tally (the MissingNo. odometer). Use this for
     *  egg rendering ONLY - {@link #credit} for neutral flows (disk reads) that must not count. */
    public void creditEarned(UUID player, long amount) {
        if (amount <= 0) return;
        credit(player, amount);
        lifetimeEarned.merge(player, amount, Long::sum);
    }

    public long lifetimeEarnedOf(UUID player) {
        return lifetimeEarned.getOrDefault(player, 0L);
    }

    public int missingnoClaimedOf(UUID player) {
        return missingnoClaimed.getOrDefault(player, 0);
    }

    /** Record one MissingNo. claim (caller validates entitlement via MissingnoMath). */
    public void claimMissingno(UUID player) {
        missingnoClaimed.merge(player, 1, Integer::sum);
        markDirty();
    }

    /** Spend iff affordable; returns whether it was paid. Never goes negative. */
    public boolean tryDebit(UUID player, long amount) {
        boolean paid = accountOf(player).tryDebit(amount);
        if (paid && amount > 0) markDirty();
        return paid;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound accs = new NbtCompound();
        accounts.forEach((u, a) -> accs.putLong(u.toString(), a.balance()));
        nbt.put("accounts", accs);
        NbtCompound life = new NbtCompound();
        lifetimeEarned.forEach((u, v) -> life.putLong(u.toString(), v));
        nbt.put("lifetimeEarned", life);
        NbtCompound mn = new NbtCompound();
        missingnoClaimed.forEach((u, v) -> mn.putInt(u.toString(), v));
        nbt.put("missingnoClaimed", mn);
        return nbt;
    }

    private static DataStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        DataStore s = new DataStore();
        NbtCompound accs = nbt.getCompound("accounts");
        for (String k : accs.getKeys()) {
            try {
                s.accounts.put(UUID.fromString(k), new DataAccount(accs.getLong(k)));
            } catch (IllegalArgumentException ignored) {
                // drop a malformed uuid key
            }
        }
        NbtCompound life = nbt.getCompound("lifetimeEarned");   // absent pre-MissingNo → 0 (back-compat)
        for (String k : life.getKeys()) {
            try { s.lifetimeEarned.put(UUID.fromString(k), life.getLong(k)); } catch (IllegalArgumentException ignored) { }
        }
        NbtCompound mn = nbt.getCompound("missingnoClaimed");
        for (String k : mn.getKeys()) {
            try { s.missingnoClaimed.put(UUID.fromString(k), mn.getInt(k)); } catch (IllegalArgumentException ignored) { }
        }
        return s;
    }

    private static final Type<DataStore> TYPE = new Type<>(DataStore::new, DataStore::fromNbt, null);

    public static DataStore get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
