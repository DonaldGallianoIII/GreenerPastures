package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the QUICK CLAW cabinet as one JSON blob - target species + path + timing for
 * the theater, plus the settled result. Timing is judged by the SERVER's clock on click arrival,
 * so nothing here is a secret worth sniffing; it's choreography.
 */
public record NotebookTagS2C(String json) implements CustomPayload {
    public static final Id<NotebookTagS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_tag"));

    public static final PacketCodec<RegistryByteBuf, NotebookTagS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookTagS2C::json,
            NotebookTagS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
