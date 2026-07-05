package com.greenerpastures.buff;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The buffs a player has <b>compiled</b> onto a Daemon - its {@code greenerpastures:daemon_loadout} data
 * component, stored as {@code {buff id → level}} (BUG-004). Deliberately mirrors
 * {@link com.greenerpastures.pasture.breeding.Augments} (a Kernel's compiled mods): forward-compat string keys,
 * bounded entry count, level ≥1. The old global Mk-tier-while-held model is gone - a Daemon now grants only the
 * buffs in this loadout, each at its chosen level, and is billed for <i>only</i> those. A cheap
 * Feather-Falling-only Daemon is a valid, minimal build.
 *
 * <p>Pure data (ids/levels only) so the cores + tests stay MC-free; {@link BuffResolver#resolveLoadout} clamps
 * each level against the live {@link BuffConfig} cap + the {@code +3} ceiling at use-site (mirroring how
 * {@code Augments} stores raw levels and the amplification core clamps). Unknown ids survive on the wire
 * (forward-compat) but are dropped by {@link #toLevels()} / the resolver.
 */
public record DaemonLoadout(Map<String, Integer> levels) {
    public static final DaemonLoadout NONE = new DaemonLoadout(Map.of());
    private static final int MAX_ENTRIES = 32;   // bound BOTH the wire AND the disk codec (defense in depth)

    public DaemonLoadout {
        Map<String, Integer> clean = new LinkedHashMap<>();
        if (levels != null) {
            for (Map.Entry<String, Integer> e : levels.entrySet()) {
                if (clean.size() >= MAX_ENTRIES) break;   // bound the codec (decode → ctor) as well as the wire
                Integer v = e.getValue();
                if (e.getKey() != null && v != null && v > 0) clean.put(e.getKey(), v);
            }
        }
        levels = Map.copyOf(clean);
    }

    public static final Codec<DaemonLoadout> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(DaemonLoadout::new, DaemonLoadout::levels);

    public static final PacketCodec<ByteBuf, DaemonLoadout> PACKET_CODEC =
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_INT, MAX_ENTRIES)
                    .xmap(DaemonLoadout::new, l -> new HashMap<>(l.levels));

    /** Compiled level of a buff (0 if absent). */
    public int level(BuffId b) {
        return b == null ? 0 : levels.getOrDefault(b.id, 0);
    }

    /** Copy with {@code b} set to {@code lvl} (≤0 removes it) - one entry per buff, replace-in-place. */
    public DaemonLoadout withLevel(BuffId b, int lvl) {
        if (b == null) return this;
        Map<String, Integer> m = new LinkedHashMap<>(levels);
        if (lvl > 0) m.put(b.id, lvl); else m.remove(b.id);
        return new DaemonLoadout(m);
    }

    /** The compiled buffs as a {@link BuffId} map (for the resolver); unknown / stale ids dropped. */
    public Map<BuffId, Integer> toLevels() {
        Map<BuffId, Integer> out = new EnumMap<>(BuffId.class);
        levels.forEach((id, lvl) -> {
            BuffId b = BuffId.byId(id);
            if (b != null && lvl != null && lvl > 0) out.put(b, lvl);
        });
        return out;
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }
}
