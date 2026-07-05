package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the always-on status-bar figures - the player's Data <b>balance</b>, their GPU
 * <b>inventory count</b> (GPU is a physical item, not a balance), and whether any Daemon in their inventory
 * is ON. Pushed on console open and after every console action. Part of the sync layer (INTERACTIVE_SPEC §2).
 */
public record NotebookStatusS2C(long data, int gpu, boolean daemonOn) implements CustomPayload {
    public static final Id<NotebookStatusS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_status"));

    public static final PacketCodec<RegistryByteBuf, NotebookStatusS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, NotebookStatusS2C::data,
            PacketCodecs.VAR_INT, NotebookStatusS2C::gpu,
            PacketCodecs.BOOL, NotebookStatusS2C::daemonOn,
            NotebookStatusS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
