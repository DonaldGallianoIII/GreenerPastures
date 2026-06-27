package com.greenerpastures.pasture.breeding.net;

import com.greenerpastures.pasture.breeding.PastureRegistry;
import com.greenerpastures.pasture.breeding.PastureWand;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Wand-GUI networking. The pairing board and name field are client screens, but the pasture record is
 * server-owned ({@link PastureRegistry}); these two C2S payloads carry edits back to it. Registered
 * from the common entrypoint so the codecs exist on both sides and the receivers run server-side.
 *
 * <p>Pastures are shared ("anyone with a wand edits it"), so we only sanity-check the editor is near
 * the pasture — no ownership model.
 */
public final class PastureNet {
    private PastureNet() {}

    /** Max distance² an editor may be from the pasture they're editing. */
    private static final double REACH_SQ = 64.0 * 64.0;

    public static void init() {
        PayloadTypeRegistry.playC2S().register(SaveNamePayload.ID, SaveNamePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SavePairingsPayload.ID, SavePairingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenPasturePayload.ID, OpenPasturePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SaveNamePayload.ID, PastureNet::onName);
        ServerPlayNetworking.registerGlobalReceiver(SavePairingsPayload.ID, PastureNet::onPairings);
        ServerPlayNetworking.registerGlobalReceiver(OpenPasturePayload.ID, PastureNet::onOpen);
    }

    private static void onOpen(OpenPasturePayload payload, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            if (withinReach(player, payload.pos())) PastureWand.openMenu(player, payload.pos());
        });
    }

    private static void onName(SaveNamePayload payload, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            ServerWorld world = player.getServerWorld();
            BlockPos pos = payload.pos();
            if (world == null || !withinReach(player, pos)) return;
            String name = payload.name();
            if (name.length() > 64) name = name.substring(0, 64);
            PastureRegistry.get(server).setName(world, pos, name);
        });
    }

    private static void onPairings(SavePairingsPayload payload, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            ServerWorld world = player.getServerWorld();
            BlockPos pos = payload.pos();
            if (world == null || !withinReach(player, pos)) return;
            PastureRegistry.get(server).setPairings(world, pos, payload.pairings());
        });
    }

    private static boolean withinReach(ServerPlayerEntity player, BlockPos pos) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQ;
    }
}
