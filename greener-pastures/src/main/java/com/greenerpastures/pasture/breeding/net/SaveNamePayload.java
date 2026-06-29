package com.greenerpastures.pasture.breeding.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client → server: rename a pasture. Sent from the wand GUI when its name field changes. The pasture
 * is identified by position; the server writes it into the shared {@link com.greenerpastures.pasture.breeding.PastureData}.
 */
public record SaveNamePayload(BlockPos pos, String name) implements CustomPayload {
    public static final Id<SaveNamePayload> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "save_pasture_name"));

    public static final PacketCodec<RegistryByteBuf, SaveNamePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, SaveNamePayload::pos,
            PacketCodecs.string(64), SaveNamePayload::name,   // bound at the wire (server also truncates to 64)
            SaveNamePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
