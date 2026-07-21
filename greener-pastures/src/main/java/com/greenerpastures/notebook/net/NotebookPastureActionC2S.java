package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client → server: an edit to the pasture currently focused in the React console ({@code pos} identifies which).
 * One packet for the four pasture-config actions; the server applies it and re-pushes {@link NotebookPastureConfigS2C}
 * so the UI reflects the authoritative result. Reuses the same underlying mutations as the retired owo screen.
 */
public record NotebookPastureActionC2S(long pos, int action, String arg, Map<UUID, Integer> pairings) implements CustomPayload {
    public static final int NAME = 0;      // arg = new name
    public static final int PAIRINGS = 1;  // pairings = mon → bucket (0 = unassigned)
    public static final int CLAIM = 2;     // toggle the operator/link claim (link the pasture to my Notebook)
    public static final int KERNEL = 3;    // slot a Kernel from my inventory, or return the slotted one
    public static final int TETHER = 4;    // arg = "slotIdx:invSlot" slot an inscribed tether into functional slot, "slotIdx:-1" return it

    public static final Id<NotebookPastureActionC2S> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_pasture_action"));

    public static final PacketCodec<RegistryByteBuf, NotebookPastureActionC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, NotebookPastureActionC2S::pos,
            PacketCodecs.VAR_INT, NotebookPastureActionC2S::action,
            PacketCodecs.STRING, NotebookPastureActionC2S::arg,
            PacketCodecs.map(HashMap::new, Uuids.PACKET_CODEC, PacketCodecs.VAR_INT), NotebookPastureActionC2S::pairings,
            NotebookPastureActionC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
