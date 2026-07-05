package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Server → client: the viewing player's recent egg-ingest feed (kept / voided + which filter) + running totals,
 * for the console's Log view - the void-log trust feature made visible. Pushed on the console's ~1×/s poll.
 */
public record NotebookEggLogS2C(long kept, long voided, List<Entry> entries) implements CustomPayload {
    public record Entry(String species, boolean voided, String filter) {
        public static final PacketCodec<RegistryByteBuf, Entry> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Entry::species,
                PacketCodecs.BOOL, Entry::voided,
                PacketCodecs.STRING, Entry::filter,
                Entry::new);
    }

    public static final Id<NotebookEggLogS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_egg_log"));

    public static final PacketCodec<RegistryByteBuf, NotebookEggLogS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, NotebookEggLogS2C::kept,
            PacketCodecs.VAR_LONG, NotebookEggLogS2C::voided,
            Entry.CODEC.collect(PacketCodecs.toList()), NotebookEggLogS2C::entries,
            NotebookEggLogS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
