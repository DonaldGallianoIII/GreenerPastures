package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the focused pasture's <b>Daemon graph</b> (filter/sink/source nodes + flow edges + mon-node
 * positions) as a JSON string. Pushed alongside {@link NotebookPastureConfigS2C} - which is already a full
 * tuple-6, so the graph rides its own packet rather than widening the config. {@code pos} is packed
 * ({@link net.minecraft.util.math.BlockPos#asLong()}); {@code json} is {@code ""} when the pasture has no graph.
 */
public record NotebookGraphS2C(long pos, String json) implements CustomPayload {
    public static final Id<NotebookGraphS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_graph"));

    public static final PacketCodec<RegistryByteBuf, NotebookGraphS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, NotebookGraphS2C::pos,
            PacketCodecs.STRING, NotebookGraphS2C::json,
            NotebookGraphS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
