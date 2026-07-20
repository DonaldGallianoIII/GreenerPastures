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
 * Each {@link Entry} carries an egg's full competitive card - species · shiny · per-stat IVs[6] · per-stat
 * EVs[6] (HP·Atk·Def·SpA·SpD·Spe order) · nature · gender · ability (best-effort, "" if unavailable).
 * (Tera / ball / OT / moves are post-hatch, so not part of an egg's spec - §7.3.)
 */
public record NotebookBioBankS2C(int total, List<Entry> entries, List<Press> presses) implements CustomPayload {

    /** One Compression-press ledger row: normalized species key → total eggs ever pressed (100 eggs = one
     *  press = a permanent +5% drop-proc multiplier for that species). */
    public record Press(String species, long eggs) {
        public static final PacketCodec<RegistryByteBuf, Press> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Press::species,
                PacketCodecs.VAR_LONG, Press::eggs,
                Press::new);
    }

    public record Entry(String species, boolean shiny, int[] ivs, int[] evs,
                        String nature, String gender, String ability) {

        // 10+ fields exceed PacketCodec.tuple's 6-arg limit, so a small manual codec (fixed 6 IVs + 6 EVs).
        public static final PacketCodec<RegistryByteBuf, Entry> CODEC = PacketCodec.ofStatic(
                (buf, e) -> {
                    buf.writeString(e.species);
                    buf.writeBoolean(e.shiny);
                    for (int i = 0; i < 6; i++) buf.writeVarInt(i < e.ivs.length ? e.ivs[i] : 0);
                    for (int i = 0; i < 6; i++) buf.writeVarInt(i < e.evs.length ? e.evs[i] : 0);
                    buf.writeString(e.nature);
                    buf.writeString(e.gender);
                    buf.writeString(e.ability);
                },
                buf -> {
                    String species = buf.readString();
                    boolean shiny = buf.readBoolean();
                    int[] ivs = new int[6];
                    for (int i = 0; i < 6; i++) ivs[i] = buf.readVarInt();
                    int[] evs = new int[6];
                    for (int i = 0; i < 6; i++) evs[i] = buf.readVarInt();
                    String nature = buf.readString();
                    String gender = buf.readString();
                    String ability = buf.readString();
                    return new Entry(species, shiny, ivs, evs, nature, gender, ability);
                });
    }

    public static final Id<NotebookBioBankS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_biobank"));

    public static final PacketCodec<RegistryByteBuf, NotebookBioBankS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, NotebookBioBankS2C::total,
            Entry.CODEC.collect(PacketCodecs.toList()), NotebookBioBankS2C::entries,
            Press.CODEC.collect(PacketCodecs.toList()), NotebookBioBankS2C::presses,
            NotebookBioBankS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
