package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the Augmenter tab's <b>picker meta</b> (#34/#35) as one JSON blob, riding alongside the
 * (tuple-maxed, owo-shared) {@link NotebookAugmenterS2C}: the held Kernel's current selector values + EV
 * spread, and the server-authoritative nature/ball catalogs so the React pickers can never drift from
 * {@code NatureCatalog}/{@code BallCatalog}.
 *
 * <p>Shape: {@code {"values":{"NATURE":{"value":7,"label":"adamant"},"BALL":{...},"EV":{"spread":[6 ints]}},
 * "natures":[25 ids], "balls":[ball ids]}}
 */
public record NotebookAugmenterMetaS2C(String json) implements CustomPayload {
    public static final Id<NotebookAugmenterMetaS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_augmenter_meta"));

    public static final PacketCodec<RegistryByteBuf, NotebookAugmenterMetaS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookAugmenterMetaS2C::json,
            NotebookAugmenterMetaS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
