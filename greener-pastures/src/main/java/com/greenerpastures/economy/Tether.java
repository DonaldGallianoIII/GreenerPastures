package com.greenerpastures.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * The data a Soul Tether item carries: which augment {@code function} it amplifies and at what
 * {@code tier} (0 = blank / uninscribed). The item stores only {@code [function, tier]}; the runtime
 * {@link SoulTether} — with its economic class + burn — is derived via {@link #toSoulTether()} (the class
 * comes from {@link AugmentFunction}). Inscribed at the Compiler for Data; re-inscribable.
 */
public record Tether(String function, int tier) {
    public Tether {
        function = function == null ? "" : function;
        tier = Math.max(0, Math.min(SoulTether.MAX_TIER, tier));
    }

    public static Tether blank() { return new Tether("", 0); }

    public boolean isBlank() { return tier <= 0 || function.isBlank(); }

    /** The runtime tether (resolves the economic class from the function); a blank → an inert SoulTether. */
    public SoulTether toSoulTether() {
        AugmentFunction f = AugmentFunction.byId(function);
        TetherClass cls = (f != null) ? f.cls : TetherClass.QUALITY;
        return new SoulTether(function, cls, tier);
    }

    public static final Codec<Tether> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("fn", "").forGetter(Tether::function),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(Tether::tier)
    ).apply(i, Tether::new));

    public static final PacketCodec<RegistryByteBuf, Tether> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, Tether::function,
            PacketCodecs.VAR_INT, Tether::tier,
            Tether::new);
}
