package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: "push me the console data for tab {@code tab}" - sent when the Notebook console opens and
 * on every tab switch. The server replies with {@link NotebookStatusS2C} (always) plus, later, the tab's own
 * S2C payload. Part of the shared sync layer (NOTEBOOK_INTERACTIVE_SPEC §2).
 */
public record NotebookRequestC2S(int tab) implements CustomPayload {
    public static final Id<NotebookRequestC2S> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_request"));

    public static final PacketCodec<RegistryByteBuf, NotebookRequestC2S> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, NotebookRequestC2S::tab, NotebookRequestC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
