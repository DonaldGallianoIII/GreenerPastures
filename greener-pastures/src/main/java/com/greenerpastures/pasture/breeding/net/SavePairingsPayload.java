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

    /** Hard cap on map entries a client may send. Far above any real roster (a pasture tops out around
     *  16 mons / 8 buckets), but bounded so a crafted packet can't decode an unbounded map into the save
     *  (bug-hunt #2). The server also re-sanitizes in {@code PastureNet.onPairings}. */
    public static final int MAX_PAIRINGS = 256;

    /** tethering id → bucket. Declared on ByteBuf (super of RegistryByteBuf) so the map M is Map, not HashMap.
     *  The 4-arg map codec rejects an oversized map at decode time, before any allocation into the save. */
    private static final PacketCodec<ByteBuf, Map<UUID, Integer>> PAIRINGS_CODEC =
            PacketCodecs.map(HashMap::new, Uuids.PACKET_CODEC, PacketCodecs.VAR_INT, MAX_PAIRINGS);

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
