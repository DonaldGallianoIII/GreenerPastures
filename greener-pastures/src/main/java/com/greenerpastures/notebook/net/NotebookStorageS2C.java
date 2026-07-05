package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → client: the player's Notebook item-storage snapshot - {registry-id → count} plus the per-item
 * capacity. Feeds the Harvester/Storage tab grid. Pushed on request and after any pull. (INTERACTIVE_SPEC §3.1)
 */
public record NotebookStorageS2C(Map<String, Long> items, long capacity) implements CustomPayload {
    public static final Id<NotebookStorageS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_storage"));

    public static final PacketCodec<RegistryByteBuf, NotebookStorageS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_LONG), NotebookStorageS2C::items,
            PacketCodecs.VAR_LONG, NotebookStorageS2C::capacity,
            NotebookStorageS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
