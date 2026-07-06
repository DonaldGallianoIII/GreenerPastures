package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the TREELINE cabinet as one JSON blob. Tree LAYOUT ships on round start (not
 * secret); a tree's contents only appear once swept, and the target's tree id only once the round
 * is over (for the escape taunt). Nothing a sniffer reads mid-round says where she hides.
 */
public record NotebookTreelineS2C(String json) implements CustomPayload {
    public static final Id<NotebookTreelineS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_treeline"));

    public static final PacketCodec<RegistryByteBuf, NotebookTreelineS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookTreelineS2C::json,
            NotebookTreelineS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
