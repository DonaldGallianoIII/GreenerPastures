package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: an edit on a display block (Display Suite v2 §2). {@code pos} is the block's packed
 * position; {@code action} is the verb (e.g. {@code "RENAME"}, later {@code "DISGUISE"}); {@code arg}
 * carries the action's string argument (the new name, the disguise choice, …). The server validates
 * reach + block type, applies the edit, mirrors it into the owner's {@link com.greenerpastures.display.ExhibitStore}
 * directory where relevant, and re-pushes the Display tab.
 */
public record NotebookDisplayActionC2S(long pos, String action, String arg) implements CustomPayload {
    public static final Id<NotebookDisplayActionC2S> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_display_action"));

    public static final PacketCodec<RegistryByteBuf, NotebookDisplayActionC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, NotebookDisplayActionC2S::pos,
            PacketCodecs.STRING, NotebookDisplayActionC2S::action,
            PacketCodecs.STRING, NotebookDisplayActionC2S::arg,
            NotebookDisplayActionC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
