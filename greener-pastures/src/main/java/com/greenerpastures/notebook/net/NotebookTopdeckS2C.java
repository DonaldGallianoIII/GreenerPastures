package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the TOP DECK cabinet as one JSON blob. The 20 fanned species are public (they
 * fanned face-up); the drawn card's index NEVER ships mid-round - only after the round settles,
 * for the reveal. A sniffer mid-round learns nothing the table doesn't show.
 */
public record NotebookTopdeckS2C(String json) implements CustomPayload {
    public static final Id<NotebookTopdeckS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_topdeck"));

    public static final PacketCodec<RegistryByteBuf, NotebookTopdeckS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookTopdeckS2C::json,
            NotebookTopdeckS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
