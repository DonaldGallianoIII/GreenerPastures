package com.greenerpastures.pasture.breeding.net;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.pasture.breeding.PastureClaim;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import com.greenerpastures.pasture.breeding.PastureWand;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wand-GUI networking. The pairing board and name field are client screens, but the pasture record is
 * server-owned ({@link PastureRegistry}); these two C2S payloads carry edits back to it. Registered
 * from the common entrypoint so the codecs exist on both sides and the receivers run server-side.
 *
 * <p>Pastures are shared ("anyone with a wand edits it"); we sanity-check the editor is near the pasture.
 * The ONE ownership bit is the <b>operator claim</b> (who pays the Soul-Tether cost) — an explicit
 * locked-boolean toggle ({@link PastureClaim}), never set implicitly.
 */
public final class PastureNet {
    private PastureNet() {}

    /** Max distance² an editor may be from the pasture they're editing. */
    private static final double REACH_SQ = 64.0 * 64.0;
    /** Highest valid pair bucket (matches the top tier's maxPairs / {@code PastureMenu.MAX_FUNCTIONAL}). */
    private static final int MAX_BUCKET = 8;

    public static void init() {
        PayloadTypeRegistry.playC2S().register(SaveNamePayload.ID, SaveNamePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SavePairingsPayload.ID, SavePairingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenPasturePayload.ID, OpenPasturePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ClaimOperatorPayload.ID, ClaimOperatorPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(SaveNamePayload.ID, PastureNet::onName);
        ServerPlayNetworking.registerGlobalReceiver(SavePairingsPayload.ID, PastureNet::onPairings);
        ServerPlayNetworking.registerGlobalReceiver(OpenPasturePayload.ID, PastureNet::onOpen);
        ServerPlayNetworking.registerGlobalReceiver(ClaimOperatorPayload.ID, PastureNet::onClaim);
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
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity)) return;   // no phantom records at arbitrary pos
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
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity)) return;   // no phantom records at arbitrary pos
            PastureRegistry.get(server).setPairings(world, pos, sanitize(payload.pairings()));
        });
    }

    /** Toggle the operator claim — the locked-boolean tether-cost "box". Server-authoritative + validated;
     *  only the current owner can release their lock (see {@link PastureClaim}). */
    private static void onClaim(ClaimOperatorPayload payload, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            ServerWorld world = player.getServerWorld();
            BlockPos pos = payload.pos();
            if (world == null || !withinReach(player, pos)) return;
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity)) return;
            PastureRegistry reg = PastureRegistry.get(server);
            PastureData pd = reg.getOrCreate(world, pos);
            PastureClaim.Result r = PastureClaim.toggle(pd.owner, player.getUuid());
            if (r.changed()) {
                pd.owner = r.owner();
                reg.markDirty();
                player.sendMessage(Text.literal(r.outcome() == PastureClaim.Outcome.CLAIMED
                        ? "§a[Greener Pastures]§r Linked — this pasture is yours: its drops, eggs & outputs collect into your Notebook (you also pay its tether cost)."
                        : "§a[Greener Pastures]§r Unlinked — this pasture is free to claim; its outputs no longer collect to you."), false);
            } else {
                player.sendMessage(Text.literal(
                        "§c[Greener Pastures]§r This pasture is owned by someone else."), false);
            }
        });
    }

    /** Trust boundary: keep only sane (id → bucket) entries — bucket in [1, MAX_BUCKET], size-capped.
     *  The codec already bounds decode size; this keeps junk buckets / nulls out of the persisted record. */
    private static Map<UUID, Integer> sanitize(Map<UUID, Integer> raw) {
        Map<UUID, Integer> clean = new HashMap<>();
        if (raw == null) return clean;
        for (Map.Entry<UUID, Integer> e : raw.entrySet()) {
            if (clean.size() >= SavePairingsPayload.MAX_PAIRINGS) break;
            UUID id = e.getKey();
            Integer b = e.getValue();
            if (id != null && b != null && b >= 1 && b <= MAX_BUCKET) clean.put(id, b);
        }
        return clean;
    }

    private static boolean withinReach(ServerPlayerEntity player, BlockPos pos) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= REACH_SQ;
    }
}
