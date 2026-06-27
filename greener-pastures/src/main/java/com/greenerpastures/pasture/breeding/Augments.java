package com.greenerpastures.pasture.breeding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The data carried by a "Kernel" (the slotted upgrade item) as the {@code greenerpastures:augments}
 * data component — the player's compiled program, stored as DATA on the item rather than as a
 * separate item per (function × tier). v1 carries a single augment, the shiny proc; the record is
 * the extension point for future augments (yield, speed, IV filters → the Daemon node graph).
 *
 * <p>{@code shinyProcPercent} = the % chance, per egg, that the upgrade fires <i>one extra</i> shiny
 * reroll (0 = no shiny augment). Stored as a plain int so it's trivial to author by hand for testing,
 * e.g. {@code /give @p greenerpastures:breeding_upgrade_copper[greenerpastures:augments={shiny:40}]},
 * until the Compiler block ships the friendly authoring GUI.
 */
public record Augments(int shinyProcPercent) {
    public static final Augments NONE = new Augments(0);

    public static final Codec<Augments> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("shiny", 0).forGetter(Augments::shinyProcPercent)
    ).apply(i, Augments::new));

    public static final PacketCodec<RegistryByteBuf, Augments> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, Augments::shinyProcPercent,
            Augments::new);

    /** Proc chance as a 0..1 probability, clamped to a sane range. */
    public double shinyProcChance() {
        return Math.max(0, Math.min(100, shinyProcPercent)) / 100.0;
    }

    /** Copy with the shiny proc set to {@code pct} (replace-in-place; one shiny augment per Kernel). */
    public Augments withShiny(int pct) {
        return new Augments(pct);
    }
}
