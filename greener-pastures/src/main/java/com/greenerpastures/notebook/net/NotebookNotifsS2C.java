package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the viewing player's Inbox (dismissible notifications — catch-up pings etc.) as a JSON blob
 * {@code {"notes":[{"id":n,"icon":"⛏","text":"…","t":ms}]}} for the console's Inbox tab.
 */
public record NotebookNotifsS2C(String json) implements CustomPayload {
    public static final Id<NotebookNotifsS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_notifs"));

    public static final PacketCodec<RegistryByteBuf, NotebookNotifsS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookNotifsS2C::json, NotebookNotifsS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
