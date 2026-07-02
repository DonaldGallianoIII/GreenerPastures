package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: set or clear the player's breeding goal, as a JSON spec
 * {@code {"clear":bool,"species":str,"shiny":-1|0|1,"minPerfect":int,"minIvTotal":int,"count":int}}.
 * {@code shiny}: -1 = either, 0 = must be non-shiny, 1 = must be shiny.
 */
public record NotebookGoalC2S(String json) implements CustomPayload {
    public static final Id<NotebookGoalC2S> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_goal_set"));

    public static final PacketCodec<RegistryByteBuf, NotebookGoalC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, NotebookGoalC2S::json, NotebookGoalC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
