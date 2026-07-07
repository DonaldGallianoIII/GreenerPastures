package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the SLOTS cabinet's last spin as one JSON blob (seq-numbered so the client can
 * tell a fresh spin from a repeat of the same faces). Spins resolve entirely server-side in one
 * action - there is no mid-round secret to protect, just the result.
 */
public record NotebookSlotsS2C(String json) implements CustomPayload {
    public static final Id<NotebookSlotsS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_slots"));

    public static final PacketCodec<RegistryByteBuf, NotebookSlotsS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookSlotsS2C::json,
            NotebookSlotsS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
