package com.greenerpastures.notebook;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-saved per-player <b>Notebook item storage</b> — the digital warehouse harvested loot flows into
 * (see {@code NOTEBOOK_CONSOLE_SPEC.md} §3). MC persistence adapter around the pure {@link NotebookStorage};
 * mirrors {@code DataStore}. Player-bound, so the warehouse survives losing the Notebook item.
 */
public final class NotebookStore extends PersistentState {
    private static final String ID = "greenerpastures_notebook_storage";

    private final Map<UUID, NotebookStorage> stores = new HashMap<>();

    public NotebookStorage storageOf(UUID player) {
        return stores.computeIfAbsent(player, u -> { markDirty(); return new NotebookStorage(); });
    }

    /** Deposit into a player's warehouse; returns the amount actually stored (respects the cap). */
    public long deposit(UUID player, String item, long n) {
        long stored = storageOf(player).add(item, n);
        if (stored > 0) markDirty();
        return stored;
    }

    /** Withdraw from a player's warehouse; returns the amount removed. */
    public long withdraw(UUID player, String item, long n) {
        NotebookStorage s = stores.get(player);
        if (s == null) return 0;
        long taken = s.withdraw(item, n);
        if (taken > 0) markDirty();
        return taken;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound players = new NbtCompound();
        stores.forEach((u, s) -> {
            NbtCompound items = new NbtCompound();
            s.snapshot().forEach((k, v) -> items.putLong(k, v));
            NbtCompound entry = new NbtCompound();
            entry.putLong("cap", s.capacity());
            entry.put("items", items);
            players.put(u.toString(), entry);
        });
        nbt.put("players", players);
        return nbt;
    }

    private static NotebookStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NotebookStore store = new NotebookStore();
        NbtCompound players = nbt.getCompound("players");
        for (String k : players.getKeys()) {
            try {
                UUID id = UUID.fromString(k);
                NbtCompound entry = players.getCompound(k);
                long cap = entry.contains("cap") ? entry.getLong("cap") : NotebookStorage.INT_LIMIT;
                NbtCompound items = entry.getCompound("items");
                Map<String, Long> data = new HashMap<>();
                for (String item : items.getKeys()) data.put(item, items.getLong(item));
                store.stores.put(id, NotebookStorage.fromSnapshot(data, cap));
            } catch (IllegalArgumentException ignored) {
                // drop a malformed uuid key
            }
        }
        return store;
    }

    private static final Type<NotebookStore> TYPE = new Type<>(NotebookStore::new, NotebookStore::fromNbt, null);

    public static NotebookStore get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
