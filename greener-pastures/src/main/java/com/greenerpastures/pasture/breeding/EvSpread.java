package com.greenerpastures.pasture.breeding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * A per-stat EV allocation (BUG-002) - the targeted EV spread a Kernel applies to every bred egg, replacing the old
 * "flat +N on all six stats" blanket (nobody wants a blanket EV buff; a chosen spread is the whole point of EVs).
 * Pure data + Cobblemon-legal clamps (each stat 0..252, total ≤510), so it's headless-tested and rides on the Kernel
 * as the {@code greenerpastures:ev_spread} component.
 *
 * <p>Authored no-UI via {@code /gp augment ev <hp> <atk> <def> <spa> <spd> <spe>}; the Compiler allocation screen
 * (anvil-preview layout, DAEMON_REDESIGN-style) is the web-dev UI phase. Order matches Cobblemon: HP, Atk, Def,
 * SpA, SpD, Spe.
 */
public record EvSpread(int hp, int atk, int def, int spa, int spd, int spe) {

    public static final int PER_STAT_MAX = 252;
    public static final int TOTAL_MAX = 510;
    public static final EvSpread NONE = new EvSpread(0, 0, 0, 0, 0, 0);

    /** Clamp on construction so a stored / synced / command-set spread is ALWAYS legal: each stat ≤252, total ≤510
     *  (trimmed from the last stat backwards if over budget - preserves earlier per-stat intent). */
    public EvSpread {
        hp = clamp(hp); atk = clamp(atk); def = clamp(def);
        spa = clamp(spa); spd = clamp(spd); spe = clamp(spe);
        int over = (hp + atk + def + spa + spd + spe) - TOTAL_MAX;
        if (over > 0) {
            int[] v = {hp, atk, def, spa, spd, spe};
            for (int i = 5; i >= 0 && over > 0; i--) { int cut = Math.min(v[i], over); v[i] -= cut; over -= cut; }
            hp = v[0]; atk = v[1]; def = v[2]; spa = v[3]; spd = v[4]; spe = v[5];
        }
    }

    private static int clamp(int v) { return Math.max(0, Math.min(PER_STAT_MAX, v)); }

    public int total() { return hp + atk + def + spa + spd + spe; }
    public boolean isEmpty() { return total() == 0; }

    public static final Codec<EvSpread> CODEC = RecordCodecBuilder.create(in -> in.group(
            Codec.INT.optionalFieldOf("hp", 0).forGetter(EvSpread::hp),
            Codec.INT.optionalFieldOf("atk", 0).forGetter(EvSpread::atk),
            Codec.INT.optionalFieldOf("def", 0).forGetter(EvSpread::def),
            Codec.INT.optionalFieldOf("spa", 0).forGetter(EvSpread::spa),
            Codec.INT.optionalFieldOf("spd", 0).forGetter(EvSpread::spd),
            Codec.INT.optionalFieldOf("spe", 0).forGetter(EvSpread::spe)
    ).apply(in, EvSpread::new));

    public static final PacketCodec<ByteBuf, EvSpread> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, EvSpread::hp,
            PacketCodecs.VAR_INT, EvSpread::atk,
            PacketCodecs.VAR_INT, EvSpread::def,
            PacketCodecs.VAR_INT, EvSpread::spa,
            PacketCodecs.VAR_INT, EvSpread::spd,
            PacketCodecs.VAR_INT, EvSpread::spe,
            EvSpread::new);
}
