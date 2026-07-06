package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the Game Corner tab as one JSON blob. Server-authoritative on purpose: unflipped
 * tile values are NEVER in this packet (tiles read -1 until flipped; the full board is only revealed
 * once the round is over) - there is nothing for a packet sniffer to learn mid-round.
 *
 * <p>Shape: {@code {"level","coins","playing","over","cleared","dailyLeft",
 * "rows":[{"sum","volts"}×5], "cols":[{"sum","volts"}×5], "tiles":[25 × -1|0|1|2|3]}}
 */
public record NotebookArcadeS2C(String json) implements CustomPayload {
    public static final Id<NotebookArcadeS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_arcade"));

    public static final PacketCodec<RegistryByteBuf, NotebookArcadeS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookArcadeS2C::json,
            NotebookArcadeS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
