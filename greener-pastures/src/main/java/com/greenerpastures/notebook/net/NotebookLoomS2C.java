package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the <b>Loom</b> tab (Soul-Tether inscription - Deuce, 2026-07-20: tethers get their own
 * bench, like the Compiler is the Daemon's and the Augmenter is the Kernel's) as one JSON blob:
 * {@code {"balance":N, "tethers":[{"slot","count","fn","tier"}], "catalog":[{"id","label","cls",
 * "costs":[c1,c2,c3]}], "refundRate":0.5}}.
 */
public record NotebookLoomS2C(String json) implements CustomPayload {
    public static final Id<NotebookLoomS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_loom"));

    public static final PacketCodec<RegistryByteBuf, NotebookLoomS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookLoomS2C::json, NotebookLoomS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
