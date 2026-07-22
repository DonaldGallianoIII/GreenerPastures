package com.greenerpastures.notebook;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.DataStore;
import com.greenerpastures.economy.TetherRuntime;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Soul-Tether <b>rent clock</b> (Deuce, 2026-07-21: "a soul tether should only be charging data per
 * second, while attached to a pasture that is linked, with units inside of it"). Once a second, every
 * slotted tether on a LINKED pasture with a non-empty roster in a LOADED chunk accrues its
 * {@code upkeepCentiPerSecond} (quality 0.5 × tier · throughput 0.2 × tier Data/s); fractions accumulate
 * per owner and debit as whole Data. A failed debit clears the tab instead of going negative - the
 * starvation fallback ({@link TetherRuntime#resolve} gating on balance) is the whole penalty.
 *
 * <p>This clock bills LIVE seconds only. Away windows are billed by the CONSUMERS' own catch-ups
 * (breeder broods · harvest sweeps), <b>pre-paid</b>: each catch-up checks the owner can afford its
 * window's rent FIRST and only then applies the boost - can't pay = no charge, base mods (Deuce,
 * 2026-07-21: "check if the user has enough data first, then if they do, apply the buff"). Empty pens
 * rent nothing, unlinked pastures rent nothing.
 */
public final class TetherUpkeep {
    private TetherUpkeep() {}

    /** Per-owner centi-Data tab (in-memory; resets with the session - rent never survives a restart). */
    private static final Map<UUID, Long> TAB = new HashMap<>();

    public static void init() {
        ServerTickEvents.END_WORLD_TICK.register(TetherUpkeep::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        if (world.getTime() % 20L != 0L) return;   // one accrual pass per second
        MinecraftServer server = world.getServer();
        PastureRegistry reg = PastureRegistry.get(server);
        Map<BlockPos, PastureData> pastures = reg.inWorld(world);
        if (pastures.isEmpty()) return;
        DataStore data = DataStore.get(server);
        for (Map.Entry<BlockPos, PastureData> e : pastures.entrySet()) {
            PastureData pd = e.getValue();
            if (pd.owner == null) continue;                       // rent needs a LINKED pasture
            BlockPos pos = e.getKey();
            if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;   // away rent = the consumers' pre-paid catch-ups
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity pasture)) continue;
            try {
                if (pasture.getTetheredPokemon().isEmpty()) continue;   // "units inside" - empty pens rent nothing
                long centi = TetherRuntime.upkeepCentiPerSecond(pd.slottedTethers());
                if (centi <= 0) continue;
                long owed = TAB.merge(pd.owner, centi, Long::sum);
                long whole = owed / 100;
                if (whole <= 0) continue;
                if (data.tryDebit(pd.owner, whole)) {
                    TAB.put(pd.owner, owed % 100);
                    GpLog.d("tether", "rent", "owner", pd.owner.toString(), "data", whole,
                            "pos", pos.toShortString());
                } else {
                    TAB.put(pd.owner, 0L);   // broke: clear the tab - starvation is the penalty, never debt
                }
            } catch (Throwable t) {
                // a Cobblemon API edge must never crash the world tick (mirrors the breeder/harvest guards)
                GpLog.w("tether", "rent_skip", "pos", pos.toShortString(), "err", String.valueOf(t));
            }
        }
    }

    /** SERVER_STARTED hygiene - a new world starts with clean tabs. */
    public static void resetSession() {
        TAB.clear();
    }
}
