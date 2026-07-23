package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: an edit on a display block (Display Suite v2 §2). The block position rides as separate
 * {@code x/y/z} ints — NOT a packed {@code long} — because the position crosses the JS/React bridge as a
 * JSON number, and a packed {@code BlockPos.asLong()} loses its low bits past 2^53 (any negative-X block),
 * silently resolving to the wrong position. {@code action} is the verb ({@code "RENAME"} / {@code "DISGUISE"});
 * {@code arg} is its string argument (new name, disguise direction / {@code "CLEAR"}). The server validates
 * reach + ownership + block type, applies the edit, mirrors it into the owner's
 * {@link com.greenerpastures.display.ExhibitStore} directory, and re-pushes the tab.
 */
public record NotebookDisplayActionC2S(int x, int y, int z, String action, String arg) implements CustomPayload {
    public static final Id<NotebookDisplayActionC2S> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_display_action"));

    public static final PacketCodec<RegistryByteBuf, NotebookDisplayActionC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, NotebookDisplayActionC2S::x,
            PacketCodecs.VAR_INT, NotebookDisplayActionC2S::y,
            PacketCodecs.VAR_INT, NotebookDisplayActionC2S::z,
            PacketCodecs.STRING, NotebookDisplayActionC2S::action,
            PacketCodecs.STRING, NotebookDisplayActionC2S::arg,
            NotebookDisplayActionC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
