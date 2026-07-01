package com.greenerpastures.notebook;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.pasture.breeding.BreedingTier;
import com.greenerpastures.pasture.breeding.CobbreedingBridge;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.gui.MonEntry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * World-saved per-player <b>pasture snapshots</b> (INTERACTIVE_SPEC §3.2) — each pasture's last-opened contents
 * + breeding status, captured server-side when the player opens a pasture in-world ({@code PastureWand}) and
 * read <b>remotely + read-only</b> by the console's Pastures tab (Deuce: you can't modify a pasture from the
 * Notebook). Per-player + keyed by pasture, so it survives relog. Mirrors {@link NotebookStore}.
 */
public final class PastureSnapshotStore extends PersistentState {
    private static final String ID = "greenerpastures_pasture_snapshots";

    /** Cap per-player snapshots so the store — and the NBT rewritten on every autosave — can't grow unbounded
     *  as a farm sprawls (perf-audit H1). Access-order LRU keeps the 64 most-recently-opened pastures per player. */
    private static final int MAX_PER_PLAYER = 64;

    private final Map<UUID, LinkedHashMap<String, PastureSnapshot>> byPlayer = new LinkedHashMap<>();

    /** A per-player bucket that self-evicts its eldest (least-recently-opened) entry past {@link #MAX_PER_PLAYER}. */
    private static LinkedHashMap<String, PastureSnapshot> newBucket() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, PastureSnapshot> eldest) {
                return size() > MAX_PER_PLAYER;
            }
        };
    }

    public List<PastureSnapshot> snapshotsOf(UUID player) {
        LinkedHashMap<String, PastureSnapshot> m = byPlayer.get(player);
        return m == null ? List.of() : new ArrayList<>(m.values());
    }

    /** Build + store a fresh snapshot of the pasture at {@code pos} for {@code player} (called on open). */
    public void capture(UUID player, World world, BlockPos pos, PastureData pd, PokemonPastureBlockEntity be) {
        PastureSnapshot snap = build(world, pos, pd, be);
        byPlayer.computeIfAbsent(player, u -> newBucket()).put(snap.key(), snap);
        markDirty();
    }

    /** Forget a pasture's snapshot for every player — called when its block is reclaimed, so the console can't
     *  show a destroyed pasture and the store shrinks with the world (perf-audit H1). */
    public void removeAt(String dim, long posLong) {
        String key = dim + "|" + posLong;
        boolean changed = false;
        for (LinkedHashMap<String, PastureSnapshot> m : byPlayer.values()) {
            if (m.remove(key) != null) changed = true;
        }
        if (changed) markDirty();
    }

    private static PastureSnapshot build(World world, BlockPos pos, PastureData pd, PokemonPastureBlockEntity be) {
        String dim = world.getRegistryKey().getValue().toString();
        String name = (pd.name == null || pd.name.isEmpty()) ? "Unnamed pasture" : pd.name;
        BreedingTier tier = pd.tier();
        String tierLabel = tier != null ? tier.name() : "no Kernel";
        int maxPairs = tier != null ? tier.maxPairs : 0;
        int eggCount = pd.eggQueue.size();
        try {   // eggQueue is only our OVERFLOW buffer (usually 0) — the real eggs sit in the pasture's own tray
            var tray = CobbreedingBridge.eggsAt(pos);
            if (tray != null) for (net.minecraft.item.ItemStack egg : tray) if (!egg.isEmpty()) eggCount++;
        } catch (Throwable ignored) { }

        boolean activated = CobbreedingBridge.isBreedingActivated(be.getCachedState());
        boolean breeding = activated && world.getTime() < pd.nextBreedTick;
        String pairStatus = !activated ? "Idle" : (breeding ? "Breeding" : "Ready");

        Map<Integer, List<MonEntry>> byBucket = new LinkedHashMap<>();
        for (MonEntry m : CobbreedingBridge.rosterOf(be, pd)) {
            if (m.bucket() >= 1) byBucket.computeIfAbsent(m.bucket(), k -> new ArrayList<>()).add(m);
        }
        List<String> pairs = new ArrayList<>();
        for (int b = 1; b <= maxPairs; b++) {
            List<MonEntry> mons = byBucket.get(b);
            if (mons == null || mons.isEmpty()) continue;
            String a = mons.get(0).label();
            String other = mons.size() > 1 ? mons.get(1).label() : "—";
            String st = mons.size() > 1 ? pairStatus : "Incomplete";
            pairs.add(a + " × " + other + " · " + st);
        }
        return new PastureSnapshot(name, dim, pos.asLong(), tierLabel, eggCount, pairs);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound players = new NbtCompound();
        byPlayer.forEach((u, snaps) -> {
            NbtList list = new NbtList();
            snaps.values().forEach(s -> list.add(s.toNbt()));
            players.put(u.toString(), list);
        });
        nbt.put("players", players);
        return nbt;
    }

    private static PastureSnapshotStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        PastureSnapshotStore store = new PastureSnapshotStore();
        NbtCompound players = nbt.getCompound("players");
        for (String k : players.getKeys()) {
            try {
                UUID id = UUID.fromString(k);
                NbtList list = players.getList(k, NbtElement.COMPOUND_TYPE);
                LinkedHashMap<String, PastureSnapshot> m = newBucket();   // self-trims if a legacy save is over cap
                for (int i = 0; i < list.size(); i++) {
                    PastureSnapshot s = PastureSnapshot.fromNbt(list.getCompound(i));
                    m.put(s.key(), s);
                }
                store.byPlayer.put(id, m);
            } catch (IllegalArgumentException ignored) {
                // drop a malformed uuid key
            }
        }
        return store;
    }

    private static final Type<PastureSnapshotStore> TYPE =
            new Type<>(PastureSnapshotStore::new, PastureSnapshotStore::fromNbt, null);

    public static PastureSnapshotStore get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
