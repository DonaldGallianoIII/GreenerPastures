package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Server → client: the player's per-player BioBank contents for the browse accordion (INTERACTIVE_SPEC §3.3).
 * v1 (slice 6a) carries the read-today stats per egg ({@link Entry}: species · shiny · IV total · # perfect);
 * the full competitive card (per-stat IVs / EVs / nature / ability / Tera / ball / OT / moves) lands in 6b via
 * an extended {@code EggReader}.
 */
public record NotebookBioBankS2C(int total, List<Entry> entries) implements CustomPayload {

    public record Entry(String species, boolean shiny, int ivTotal, int perfect) {
        public static final PacketCodec<RegistryByteBuf, Entry> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Entry::species,
                PacketCodecs.BOOL, Entry::shiny,
                PacketCodecs.VAR_INT, Entry::ivTotal,
                PacketCodecs.VAR_INT, Entry::perfect,
                Entry::new);
    }

    public static final Id<NotebookBioBankS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_biobank"));

    public static final PacketCodec<RegistryByteBuf, NotebookBioBankS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, NotebookBioBankS2C::total,
            Entry.CODEC.collect(PacketCodecs.toList()), NotebookBioBankS2C::entries,
            NotebookBioBankS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
