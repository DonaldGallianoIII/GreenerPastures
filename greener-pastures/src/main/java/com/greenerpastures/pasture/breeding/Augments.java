package com.greenerpastures.pasture.breeding;

import com.greenerpastures.economy.AugmentFunction;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The data a "Kernel" (slotted Pasture Upgrade) carries as the {@code greenerpastures:augments} data
 * component — the player's compiled base mods, stored as {@code {function id → level/magnitude}}. v1
 * functions: shiny (proc %), speed, iv_floor, ev, enrichment, drop_rate, drop_yield (see
 * {@link AugmentFunction}). Soul Tethers amplify these ({@code economy.EffectiveAugments}); a Kernel
 * alone runs them free, forever.
 *
 * <p><b>Back-compat:</b> the old shiny-only format {@code {shiny:40}} reads unchanged, and
 * {@code new Augments(40)} / {@link #shinyProcPercent()} / {@link #withShiny(int)} still work.
 */
public record Augments(Map<String, Integer> levels) {
    public static final Augments NONE = new Augments(Map.of());
    private static final int MAX_ENTRIES = 32;   // bound the wire/codec (defense in depth)

    public Augments {
        Map<String, Integer> clean = new LinkedHashMap<>();
        if (levels != null) {
            for (Map.Entry<String, Integer> e : levels.entrySet()) {
                if (clean.size() >= MAX_ENTRIES) break;   // bound BOTH the wire AND the disk codec (decode → ctor)
                Integer v = e.getValue();
                if (e.getKey() != null && v != null && v > 0) clean.put(e.getKey(), v);
            }
        }
        levels = Map.copyOf(clean);
    }

    /** Back-compat convenience: a shiny-only Kernel (the pre-catalog constructor). */
    public Augments(int shinyPct) {
        this(shinyPct > 0 ? Map.of(AugmentFunction.SHINY.id, shinyPct) : Map.of());
    }

    public static final Codec<Augments> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(Augments::new, Augments::levels);

    public static final PacketCodec<ByteBuf, Augments> PACKET_CODEC =
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_INT, MAX_ENTRIES)
                    .xmap(Augments::new, a -> new HashMap<>(a.levels));

    /** Level / magnitude of a function (0 if absent). */
    public int level(AugmentFunction f) {
        return f == null ? 0 : levels.getOrDefault(f.id, 0);
    }

    /** Copy with {@code f} set to {@code lvl} (≤0 removes it) — replace-in-place, one per function. */
    public Augments withLevel(AugmentFunction f, int lvl) {
        Map<String, Integer> m = new LinkedHashMap<>(levels);
        if (lvl > 0) m.put(f.id, lvl); else m.remove(f.id);
        return new Augments(m);
    }

    /** Base levels as an {@link AugmentFunction} map (for the amplification core); unknown ids dropped. */
    public Map<AugmentFunction, Integer> toLevels() {
        Map<AugmentFunction, Integer> out = new EnumMap<>(AugmentFunction.class);
        levels.forEach((id, lvl) -> {
            AugmentFunction f = AugmentFunction.byId(id);
            if (f != null && lvl != null && lvl > 0) out.put(f, lvl);
        });
        return out;
    }

    // ── back-compat (shiny-only era) ─────────────────────────────────────────
    public int shinyProcPercent() {
        return levels.getOrDefault(AugmentFunction.SHINY.id, 0);
    }

    public double shinyProcChance() {
        return Math.max(0, Math.min(100, shinyProcPercent())) / 100.0;
    }

    public Augments withShiny(int pct) {
        return withLevel(AugmentFunction.SHINY, pct);
    }
}
