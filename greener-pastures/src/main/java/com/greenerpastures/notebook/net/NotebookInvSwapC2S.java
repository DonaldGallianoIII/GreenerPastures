package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: swap two of the player's 36 main inventory slots — grab-and-move in the Notebook's native
 * inventory overlay. Own-inventory only; the server validates the indices before swapping.
 */
public record NotebookInvSwapC2S(int a, int b) implements CustomPayload {
    public static final Id<NotebookInvSwapC2S> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_inv_swap"));

    public static final PacketCodec<RegistryByteBuf, NotebookInvSwapC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, NotebookInvSwapC2S::a,
            PacketCodecs.VAR_INT, NotebookInvSwapC2S::b,
            NotebookInvSwapC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
