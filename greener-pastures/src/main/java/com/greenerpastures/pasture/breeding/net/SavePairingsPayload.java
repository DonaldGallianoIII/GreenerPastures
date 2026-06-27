package com.greenerpastures.pasture.breeding.net;

import com.greenerpastures.GreenerPastures;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client → server: the full pair assignment for a pasture (tethering id → pair bucket, 1-based).
 * Sent from the Breeding Arrangement board when it closes. The server replaces the pasture's
 * {@link com.greenerpastures.pasture.breeding.PastureData#pairings}, which drives the breeding engine.
 */
public record SavePairingsPayload(BlockPos pos, Map<UUID, Integer> pairings) implements CustomPayload {
    public static final Id<SavePairingsPayload> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "save_pasture_pairings"));

    /** tethering id → bucket. Declared on ByteBuf (super of RegistryByteBuf) so the map M is Map, not HashMap. */
    private static final PacketCodec<ByteBuf, Map<UUID, Integer>> PAIRINGS_CODEC =
            PacketCodecs.map(HashMap::new, Uuids.PACKET_CODEC, PacketCodecs.VAR_INT);

    public static final PacketCodec<RegistryByteBuf, SavePairingsPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, SavePairingsPayload::pos,
            PAIRINGS_CODEC, SavePairingsPayload::pairings,
            SavePairingsPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
