package com.greenerpastures.pasture.breeding.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Client → server: toggle the pasture's <b>operator claim</b> - the locked-boolean "box" that decides who
 * pays the Soul-Tether Data cost (shared group pastures). The server applies
 * {@link com.greenerpastures.pasture.breeding.PastureClaim}. Sent by the wand GUI's claim box (the box UI
 * is pending - the functionality + this packet ship first).
 */
public record ClaimOperatorPayload(BlockPos pos) implements CustomPayload {
    public static final Id<ClaimOperatorPayload> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "claim_pasture_operator"));

    public static final PacketCodec<RegistryByteBuf, ClaimOperatorPayload> CODEC =
            PacketCodec.tuple(BlockPos.PACKET_CODEC, ClaimOperatorPayload::pos, ClaimOperatorPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
