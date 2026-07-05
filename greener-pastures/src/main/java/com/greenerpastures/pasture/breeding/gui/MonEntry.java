package com.greenerpastures.pasture.breeding.gui;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * One tethered mon as the wand GUI needs it client-side: its tethering id (stable handle), species,
 * a display label, and its current pair bucket (0 = unassigned). Sent from the server when the GUI
 * opens - this is the data Cobblemon doesn't sync to clients, which we deliver ourselves.
 */
public record MonEntry(UUID id, String species, String label, int bucket, String stats) {
    public static final PacketCodec<RegistryByteBuf, MonEntry> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, MonEntry::id,
            PacketCodecs.STRING, MonEntry::species,
            PacketCodecs.STRING, MonEntry::label,
            PacketCodecs.VAR_INT, MonEntry::bucket,
            PacketCodecs.STRING, MonEntry::stats,   // JSON: {ivs[6], nature, gender, shiny, ot} for the parent inspector
            MonEntry::new
    );
}
