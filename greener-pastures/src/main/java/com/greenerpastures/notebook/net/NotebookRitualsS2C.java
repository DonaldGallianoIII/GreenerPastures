package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the Rituals tab (Rituals v2) as one JSON blob — the player's LEARNED hidden recipes
 * (full reveal: species headcounts, type gates, output, lifetime hits), the count of rituals still
 * undiscovered (the Steam-style "N hidden" teaser), and the dedicated ritual loot pool.
 *
 * <p>Shape: {@code {"learned":[{"id","name","species":{..},"types":{..},"minDistinct","signature":[..],
 * "output","qty","hits"}], "hidden":N, "loot":{"minecraft:...":count}}}
 */
public record NotebookRitualsS2C(String json) implements CustomPayload {
    public static final Id<NotebookRitualsS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_rituals"));

    public static final PacketCodec<RegistryByteBuf, NotebookRitualsS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookRitualsS2C::json,
            NotebookRitualsS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
