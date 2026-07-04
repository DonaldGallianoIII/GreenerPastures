package com.greenerpastures.ritual;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The per-player <b>ritual ledger</b> (Rituals v2 — Deuce, 2026-07-03): rituals are HIDDEN recipes, Steam-
 * hidden-achievement style. A composition is secret until the player first assembles it in a pasture — that
 * moment the ritual is <b>learned</b> (recorded here forever) and its recipe renders in the Notebook's
 * Rituals tab. Ritual outputs land in a <b>dedicated loot pool</b> here too — never in the Harvester storage —
 * so the tab is both the recipe book and the reward chest. World-saved; hits are the lifetime counter.
 */
public final class RitualLedger extends PersistentState {
    private static final String ID = "greenerpastures_rituals";

    private final Map<UUID, Entry> players = new HashMap<>();

    private static final class Entry {
        final Set<String> learned = new HashSet<>();
        final Map<String, Long> hits = new HashMap<>();
        final Map<String, Long> loot = new LinkedHashMap<>();
    }

    private Entry of(UUID player) {
        return players.computeIfAbsent(player, u -> new Entry());
    }

    public boolean isLearned(UUID player, String ritualId) {
        Entry e = players.get(player);
        return e != null && e.learned.contains(ritualId);
    }

    /** Record the discovery; returns true if this was the FIRST time (the "achievement pop"). */
    public boolean learn(UUID player, String ritualId) {
        boolean first = of(player).learned.add(ritualId);
        if (first) markDirty();
        return first;
    }

    public Set<String> learnedOf(UUID player) {
        Entry e = players.get(player);
        return e == null ? Set.of() : Set.copyOf(e.learned);
    }

    public void addHits(UUID player, String ritualId, int n) {
        if (n <= 0) return;
        of(player).hits.merge(ritualId, (long) n, Long::sum);
        markDirty();
    }

    public long hitsOf(UUID player, String ritualId) {
        Entry e = players.get(player);
        return e == null ? 0L : e.hits.getOrDefault(ritualId, 0L);
    }

    public void addLoot(UUID player, String itemId, int n) {
        if (n <= 0) return;
        of(player).loot.merge(itemId, (long) n, Long::sum);
        markDirty();
    }

    public Map<String, Long> lootOf(UUID player) {
        Entry e = players.get(player);
        return e == null ? Map.of() : Map.copyOf(e.loot);
    }

    /** Remove up to {@code n} of {@code itemId} from the pool; returns the amount actually taken. */
    public long takeLoot(UUID player, String itemId, long n) {
        Entry e = players.get(player);
        if (e == null || n <= 0) return 0;
        long have = e.loot.getOrDefault(itemId, 0L);
        long take = Math.min(have, n);
        if (take <= 0) return 0;
        if (have - take <= 0) e.loot.remove(itemId);
        else e.loot.put(itemId, have - take);
        markDirty();
        return take;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound ps = new NbtCompound();
        players.forEach((u, e) -> {
            NbtCompound c = new NbtCompound();
            NbtList learned = new NbtList();
            for (String id : e.learned) learned.add(NbtString.of(id));
            c.put("learned", learned);
            NbtCompound hits = new NbtCompound();
            e.hits.forEach(hits::putLong);
            c.put("hits", hits);
            NbtCompound loot = new NbtCompound();
            e.loot.forEach(loot::putLong);
            c.put("loot", loot);
            ps.put(u.toString(), c);
        });
        nbt.put("players", ps);
        return nbt;
    }

    private static RitualLedger fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        RitualLedger l = new RitualLedger();
        NbtCompound ps = nbt.getCompound("players");
        for (String k : ps.getKeys()) {
            try {
                UUID u = UUID.fromString(k);
                NbtCompound c = ps.getCompound(k);
                Entry e = l.of(u);
                for (NbtElement el : c.getList("learned", NbtElement.STRING_TYPE)) e.learned.add(el.asString());
                NbtCompound hits = c.getCompound("hits");
                for (String id : hits.getKeys()) e.hits.put(id, hits.getLong(id));
                NbtCompound loot = c.getCompound("loot");
                for (String id : loot.getKeys()) e.loot.put(id, loot.getLong(id));
            } catch (IllegalArgumentException ignored) { }
        }
        return l;
    }

    private static final Type<RitualLedger> TYPE = new Type<>(RitualLedger::new, RitualLedger::fromNbt, null);

    public static RitualLedger get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
