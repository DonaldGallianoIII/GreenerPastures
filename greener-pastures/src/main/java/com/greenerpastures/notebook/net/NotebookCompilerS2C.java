package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server → client: the Daemon <b>Compiler</b> tab (INTERACTIVE_SPEC §3.4). Carries the installable buff
 * <b>catalog</b> ({@link Buff}: id · label · category · cap · cost-per-tier - cap/cost come from server config),
 * the held Daemon's installed <b>loadout</b> ({buff-id → tier}), its ON state, and the total <b>drain</b> Data/s.
 * Targets the first Daemon found in the player's inventory.
 */
public record NotebookCompilerS2C(boolean hasDaemon, boolean daemonOn, double drainPerSec,
                                  List<Buff> catalog, Map<String, Integer> installed) implements CustomPayload {

    public record Buff(String id, String label, String category, int cap, double costPerTier, int gpuCost) {
        public static final PacketCodec<RegistryByteBuf, Buff> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Buff::id,
                PacketCodecs.STRING, Buff::label,
                PacketCodecs.STRING, Buff::category,
                PacketCodecs.VAR_INT, Buff::cap,
                PacketCodecs.DOUBLE, Buff::costPerTier,
                PacketCodecs.VAR_INT, Buff::gpuCost,
                Buff::new);
    }

    public static final Id<NotebookCompilerS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_compiler"));

    public static final PacketCodec<RegistryByteBuf, NotebookCompilerS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, NotebookCompilerS2C::hasDaemon,
            PacketCodecs.BOOL, NotebookCompilerS2C::daemonOn,
            PacketCodecs.DOUBLE, NotebookCompilerS2C::drainPerSec,
            Buff.CODEC.collect(PacketCodecs.toList()), NotebookCompilerS2C::catalog,
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_INT), NotebookCompilerS2C::installed,
            NotebookCompilerS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
