package com.greenerpastures.notebook;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

import java.util.ArrayList;
import java.util.List;

/**
 * One pasture's <b>last-opened snapshot</b> (INTERACTIVE_SPEC §3.2) — pure data, safe on both sides. Captured
 * server-side when the player opens a pasture in-world (via {@code PastureWand}); the console's Pastures tab
 * shows these <b>read-only</b> (you modify a pasture at the pasture, not from the Notebook). Each {@code pairs}
 * line is a preformatted {@code "A × B · Status"} for legible display; {@code pos} is the packed BlockPos.
 */
public record PastureSnapshot(String name, String dim, long pos, String tier, int eggCount, List<String> pairs) {

    /** Stable per-pasture key (dim + packed pos) for upsert in the per-player store. */
    public String key() {
        return dim + "|" + pos;
    }

    public static final PacketCodec<RegistryByteBuf, PastureSnapshot> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, PastureSnapshot::name,
            PacketCodecs.STRING, PastureSnapshot::dim,
            PacketCodecs.VAR_LONG, PastureSnapshot::pos,
            PacketCodecs.STRING, PastureSnapshot::tier,
            PacketCodecs.VAR_INT, PastureSnapshot::eggCount,
            PacketCodecs.STRING.collect(PacketCodecs.toList()), PastureSnapshot::pairs,
            PastureSnapshot::new);

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        c.putString("name", name);
        c.putString("dim", dim);
        c.putLong("pos", pos);
        c.putString("tier", tier);
        c.putInt("eggs", eggCount);
        NbtList list = new NbtList();
        for (String p : pairs) list.add(NbtString.of(p));
        c.put("pairs", list);
        return c;
    }

    public static PastureSnapshot fromNbt(NbtCompound c) {
        List<String> pairs = new ArrayList<>();
        NbtList list = c.getList("pairs", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) pairs.add(list.getString(i));
        return new PastureSnapshot(c.getString("name"), c.getString("dim"), c.getLong("pos"),
                c.getString("tier"), c.getInt("eggs"), pairs);
    }
}
