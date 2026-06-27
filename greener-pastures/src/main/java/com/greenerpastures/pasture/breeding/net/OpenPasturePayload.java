package com.greenerpastures.pasture.breeding.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client → server: "reopen the Greener Pastures wand menu for this pasture." Sent when a sub-screen
 * (Daemon node-graph, Arrangement board) closes on Esc, so the player lands back on the wand GUI
 * instead of dropping all the way out to the world. The server re-opens the same handled screen the
 * wand item would ({@link com.greenerpastures.pasture.breeding.PastureWand#openMenu}).
 */
public record OpenPasturePayload(BlockPos pos) implements CustomPayload {
    public static final Id<OpenPasturePayload> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "open_pasture"));

    public static final PacketCodec<RegistryByteBuf, OpenPasturePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, OpenPasturePayload::pos,
            OpenPasturePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
