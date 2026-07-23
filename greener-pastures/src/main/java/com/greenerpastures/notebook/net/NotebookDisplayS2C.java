package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the <b>Display</b> tab (Display Suite v2 §2, one JSON blob, same shape as the Loom /
 * Dashboard pushes). Carries the block the player just opened with the Notebook plus their whole
 * "My Exhibits" directory:
 * <pre>
 * {
 *   "block": { "pos":long, "type":"Exhibit Pen"|"Specimen Statue", "name":"...", "disguise":"...",
 *              "residents":[ {"species","shiny"} ] },   // block == null when opened from the directory alone
 *   "directory": [ {"pos":long, "dim","x","y","z","type","name","display"} ]
 * }
 * </pre>
 */
public record NotebookDisplayS2C(String json) implements CustomPayload {
    public static final Id<NotebookDisplayS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_display"));

    public static final PacketCodec<RegistryByteBuf, NotebookDisplayS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookDisplayS2C::json, NotebookDisplayS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
