package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the VIBE CHECK cabinet as one JSON blob. Only DRAWN cards ship - the shuffled
 * remainder of the deck never leaves the server, so the "one more draw?" decision is honest.
 */
public record NotebookVibeS2C(String json) implements CustomPayload {
    public static final Id<NotebookVibeS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_vibe"));

    public static final PacketCodec<RegistryByteBuf, NotebookVibeS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookVibeS2C::json,
            NotebookVibeS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
