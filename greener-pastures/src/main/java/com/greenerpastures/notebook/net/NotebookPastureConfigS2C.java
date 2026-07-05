package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.pasture.breeding.gui.MonEntry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Server → client: the full editable config for the ONE pasture right-clicked with the Notebook, so the React
 * console can render an editable pasture screen (name · mon roster + pairing buckets · Kernel · link). This is the
 * data Cobblemon doesn't sync - the same the owo {@code PastureScreen} used to get through its handled-screen.
 *
 * <p>{@code tier} is {@code ""} when no Kernel is slotted (⇒ no multi-pair breeding). {@code pos} is packed
 * ({@link net.minecraft.util.math.BlockPos#asLong()}). Same tuple-6 shape as {@link com.greenerpastures.notebook.PastureSnapshot}.
 */
public record NotebookPastureConfigS2C(long pos, String name, String tier, boolean linked, int maxPairs,
                                       List<MonEntry> roster) implements CustomPayload {
    public static final Id<NotebookPastureConfigS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_pasture_config"));

    public static final PacketCodec<RegistryByteBuf, NotebookPastureConfigS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, NotebookPastureConfigS2C::pos,
            PacketCodecs.STRING, NotebookPastureConfigS2C::name,
            PacketCodecs.STRING, NotebookPastureConfigS2C::tier,
            PacketCodecs.BOOL, NotebookPastureConfigS2C::linked,
            PacketCodecs.VAR_INT, NotebookPastureConfigS2C::maxPairs,
            MonEntry.CODEC.collect(PacketCodecs.toList()), NotebookPastureConfigS2C::roster,
            NotebookPastureConfigS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
