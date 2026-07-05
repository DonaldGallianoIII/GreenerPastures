package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.notebook.PastureSnapshot;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Server → client: the player's tracked pastures as read-only {@link PastureSnapshot}s (INTERACTIVE_SPEC §3.2)
 * - one per pasture the player has opened in-world. Feeds the Pastures tab (list + selected 8-pair grid).
 *
 * <p>{@code healthJson} (#37): {@code {"<dim|pos>": "flagId,flagId", …}} - the ⚠ badge markers per snapshot,
 * from registry-side state (link/Kernel/tray always; parents/bank only while that pasture's chunk is loaded).
 */
public record NotebookPasturesS2C(List<PastureSnapshot> pastures, String healthJson) implements CustomPayload {
    public static final Id<NotebookPasturesS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_pastures"));

    public static final PacketCodec<RegistryByteBuf, NotebookPasturesS2C> CODEC = PacketCodec.tuple(
            PastureSnapshot.CODEC.collect(PacketCodecs.toList()), NotebookPasturesS2C::pastures,
            PacketCodecs.STRING, NotebookPasturesS2C::healthJson,
            NotebookPasturesS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
