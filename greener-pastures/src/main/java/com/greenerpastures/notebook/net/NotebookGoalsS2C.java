package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the viewing player's active breeding goal + live progress (checked / matched / best IV total /
 * remaining / reached), as a JSON blob for the Dashboard's Goal panel. {@code {"present":false}} when no hunt is set.
 */
public record NotebookGoalsS2C(String json) implements CustomPayload {
    public static final Id<NotebookGoalsS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_goals"));

    public static final PacketCodec<RegistryByteBuf, NotebookGoalsS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookGoalsS2C::json, NotebookGoalsS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
