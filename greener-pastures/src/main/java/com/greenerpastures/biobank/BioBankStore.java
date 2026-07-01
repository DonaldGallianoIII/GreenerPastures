package com.greenerpastures.biobank;

import com.greenerpastures.core.GpLog;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * World-saved store of every player's {@link BioBankData}, keyed by <b>player UUID</b> (INTERACTIVE_SPEC §7.2:
 * BioBank is per-player, block-free — the Notebook is the hub). The eggs live in this overworld save (not in
 * block-entity chunk NBT), so a bank scales to thousands without bloating chunk saves. A BioBank block is now
 * just a deposit station that fills the depositing player's bank; the console's BioBank tab reads it.
 *
 * <p>(Migration note: old block-keyed banks — {@code dim|pos} keys — are dropped on load, since keys are now
 * UUIDs. Fine for the in-dev mod; re-deposit.)
 */
public final class BioBankStore extends PersistentState {
    private static final String ID = "greenerpastures_biobanks";

    private final Map<UUID, BioBankData> banks = new HashMap<>();

    /** The player's bank, or null if they've never banked anything. */
    public BioBankData get(UUID player) {
        return banks.get(player);
    }

    /** The player's bank, created empty on first touch. */
    public BioBankData bankOf(UUID player) {
        return banks.computeIfAbsent(player, u -> { markDirty(); return new BioBankData(); });
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound map = new NbtCompound();
        banks.forEach((u, v) -> map.put(u.toString(), v.writeNbt(new NbtCompound(), lookup)));
        nbt.put("banks", map);
        return nbt;
    }

    private static BioBankStore fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        BioBankStore s = new BioBankStore();
        NbtCompound map = nbt.getCompound("banks");
        for (String k : map.getKeys()) {
            try {
                s.banks.put(UUID.fromString(k), BioBankData.fromNbt(map.getCompound(k), lookup));
            } catch (Exception e) {
                // a malformed / legacy (dim|pos) key must not fail the whole world load — skip + log it
                GpLog.w("biobank", "load_skip_malformed", "key", k, "err", String.valueOf(e));
            }
        }
        return s;
    }

    private static final Type<BioBankStore> TYPE = new Type<>(BioBankStore::new, BioBankStore::fromNbt, null);

    public static BioBankStore get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
