package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the Specimens tab (mon compression) as one JSON blob - the live party (slot, species,
 * level, shiny, gender) + how many blank Specimen Disks are in the inventory.
 *
 * <p>Shape: {@code {"party":[{"slot","species","level","shiny","gender"}], "blanks":N, "busy":bool}}
 */
public record NotebookSpecimensS2C(String json) implements CustomPayload {
    public static final Id<NotebookSpecimensS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_specimens"));

    public static final PacketCodec<RegistryByteBuf, NotebookSpecimensS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookSpecimensS2C::json,
            NotebookSpecimensS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
