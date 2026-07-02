package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the viewing player's live breeding analytics for the Dashboard (eggs / shiny / kept / voided /
 * Data earned / by-tier / a per-minute sparkline), as a JSON blob the React charts render. Pushed on the console
 * poll. JSON keeps the shape flexible without a wide codec.
 */
public record NotebookDashboardS2C(String json) implements CustomPayload {
    public static final Id<NotebookDashboardS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_dashboard"));

    public static final PacketCodec<RegistryByteBuf, NotebookDashboardS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookDashboardS2C::json, NotebookDashboardS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
