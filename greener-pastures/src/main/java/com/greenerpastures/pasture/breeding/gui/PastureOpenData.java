package com.greenerpastures.pasture.breeding.gui;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Payload sent to the client when the Pasture Wand GUI opens: which pasture, its name, and its mon
 * roster (read server-side — the data Cobblemon doesn't sync to clients). Carried by the extended
 * screen-handler, so the client GUI is built from real server data. (Pair count is derived live from
 * the synced upgrade slot, so it isn't sent here.)
 */
public record PastureOpenData(BlockPos pos, String name, List<MonEntry> roster) {
    public static final PacketCodec<RegistryByteBuf, PastureOpenData> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, PastureOpenData::pos,
            PacketCodecs.STRING, PastureOpenData::name,
            MonEntry.CODEC.collect(PacketCodecs.toList()), PastureOpenData::roster,
            PastureOpenData::new
    );
}
