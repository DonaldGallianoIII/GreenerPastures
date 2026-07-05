package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: save the focused pasture's Daemon graph (the whole graph JSON, authored in the React node
 * editor). The server validates reach + a size cap, then stores it on
 * {@link com.greenerpastures.pasture.breeding.PastureData#graphJson}. Not echoed back - the editor is the
 * authority while open; the persisted copy loads on the next open.
 */
public record NotebookGraphSaveC2S(long pos, String json) implements CustomPayload {
    public static final Id<NotebookGraphSaveC2S> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_graph_save"));

    public static final PacketCodec<RegistryByteBuf, NotebookGraphSaveC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, NotebookGraphSaveC2S::pos,
            PacketCodecs.STRING, NotebookGraphSaveC2S::json,
            NotebookGraphSaveC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
