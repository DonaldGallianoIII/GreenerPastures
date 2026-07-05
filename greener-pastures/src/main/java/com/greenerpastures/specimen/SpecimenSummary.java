package com.greenerpastures.specimen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The tooltip-sized digest of the mon on a Specimen Disk — rendered every frame, so the full specimen
 * NBT is never parsed for display. Written once at compression alongside the lossless payload.
 */
public record SpecimenSummary(String species, int level, boolean shiny, String gender) {

    public SpecimenSummary {
        species = species == null ? "?" : species;
        gender = gender == null ? "" : gender;
        level = Math.max(1, level);
    }

    public static final Codec<SpecimenSummary> CODEC = RecordCodecBuilder.create(in -> in.group(
            Codec.STRING.optionalFieldOf("species", "?").forGetter(SpecimenSummary::species),
            Codec.INT.optionalFieldOf("level", 1).forGetter(SpecimenSummary::level),
            Codec.BOOL.optionalFieldOf("shiny", false).forGetter(SpecimenSummary::shiny),
            Codec.STRING.optionalFieldOf("gender", "").forGetter(SpecimenSummary::gender)
    ).apply(in, SpecimenSummary::new));

    public static final PacketCodec<ByteBuf, SpecimenSummary> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SpecimenSummary::species,
            PacketCodecs.VAR_INT, SpecimenSummary::level,
            PacketCodecs.BOOL, SpecimenSummary::shiny,
            PacketCodecs.STRING, SpecimenSummary::gender,
            SpecimenSummary::new);
}
