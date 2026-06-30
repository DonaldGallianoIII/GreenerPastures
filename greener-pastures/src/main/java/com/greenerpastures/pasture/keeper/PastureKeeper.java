package com.greenerpastures.pasture.keeper;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.analytics.Event;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * PastureKeeper — server-side lag fix for big Cobblemon pasture farms (the <b>"ghost pasture"</b>).
 *
 * <p>A suppressed pasture's tethered mons <b>never exist as roaming entities</b>, so the farm's entity count stays
 * near zero (which also clears Cobblemon's "too many Pokémon nearby" placement cap) — while the pasture keeps
 * ticking its tethered <i>data</i>, so breeding + loot run exactly as before. Two non-recurring hooks do it (there
 * is deliberately <b>no per-tick despawn</b> — steady-state cost is zero):
 * <ol>
 *   <li><b>Block the spawn at the source.</b> {@code PokemonPastureBlockEntityMixin} redirects the single
 *       {@code World.spawnEntity} call inside Cobblemon's {@code tether()} — for a suppressed pasture it records
 *       the tether data but skips putting an entity in the world. New mons never spawn.</li>
 *   <li><b>One-time cleanup when you flip suppression ON.</b> {@link #despawnTethered} removes the already-spawned
 *       roamers <i>once</i> with {@link Entity.RemovalReason#DISCARDED} (gone for good — never re-saved or
 *       reloaded) and immediately re-asserts each mon's {@code tetheringId}, undoing the recall that
 *       {@code DISCARDED} would otherwise trigger — so the tether data survives untouched.</li>
 * </ol>
 *
 * <p>The suppressed flag lives on the persistent per-pasture {@link PastureData} (dimension + position keyed), so
 * it survives restarts; since suppressed mons are discarded (not saved), they don't come back on reload — the
 * flag just keeps future tethers from spawning. Toggle by sneak + empty-hand right-click the pasture.
 *
 * <p><b>v1 is suppress-focused</b> (the direction admins care about). Un-suppress clears the flag so future tethers
 * spawn again; re-materialising the already-discarded roamers is a tracked follow-up (it must re-create the
 * entities from the tether data).
 */
public final class PastureKeeper {
    private PastureKeeper() {}

    /** True if the pasture at {@code pos} is a ghost pasture (tethered mons suppressed). Server-side only. */
    public static boolean isSuppressed(World world, BlockPos pos) {
        if (world == null || pos == null || world.isClient) return false;
        MinecraftServer server = world.getServer();
        if (server == null) return false;
        PastureData pd = PastureRegistry.get(server).get(world, pos);
        return pd != null && pd.suppressed;
    }

    /** Registers the sneak + empty-hand right-click toggle. Called from the common entrypoint. */
    public static void init() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !player.isSneaking() || !player.getMainHandStack().isEmpty()) {
                return ActionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof PokemonPastureBlockEntity)) {
                be = world.getBlockEntity(pos.down());   // they clicked the top half
            }
            if (!(be instanceof PokemonPastureBlockEntity pasture)) {
                return ActionResult.PASS;
            }
            if (!world.isClient) {
                boolean now = setSuppressed(world, pasture, !isSuppressed(world, pasture.getPos()));
                BlockPos p = pasture.getPos();
                Analytics.record(world, Event.of("pasture_toggle")
                        .put("x", p.getX()).put("y", p.getY()).put("z", p.getZ())
                        .put("suppressed", now)
                        .player(player.getUuid()));
                player.sendMessage(Text.literal("[Greener Pastures] ghost pasture "
                        + (now ? "ON — mons stay as data, no roamers (lag fix)" : "OFF")), false);
            }
            return ActionResult.SUCCESS;   // consume the sneak-click so the pasture GUI doesn't open
        });
    }

    /** Set + persist a pasture's suppress state; on enable, one-time despawn the existing roamers (data kept). */
    public static boolean setSuppressed(World world, PokemonPastureBlockEntity pasture, boolean suppress) {
        MinecraftServer server = world.getServer();
        if (server == null) return false;
        BlockPos pos = pasture.getPos();
        PastureRegistry reg = PastureRegistry.get(server);
        PastureData pd = reg.getOrCreate(world, pos);
        pd.suppressed = suppress;
        reg.markDirty();
        if (suppress) despawnTethered(world, pasture);
        return suppress;
    }

    /**
     * One-time: discard the pasture's currently-spawned tethered entities, keeping the tether data intact. Each is
     * removed with {@code DISCARDED} (so it is gone for good and never reloads) and its {@code tetheringId} is
     * re-asserted to undo {@code PokemonEntity.remove}'s recall. Runs once on toggle — never on a tick.
     */
    private static void despawnTethered(World world, PokemonPastureBlockEntity pasture) {
        int cleared = 0;
        for (var t : pasture.getTetheredPokemon()) {
            try {
                Entity e = world.getEntityById(t.getEntityId());
                if (e == null || e.isRemoved()) continue;
                UUID keep = t.getTetheringId();
                e.remove(Entity.RemovalReason.DISCARDED);     // gone for good — not saved, won't reload
                t.getPokemon().setTetheringId(keep);          // undo the recall DISCARDED triggers; tether stays
                cleared++;
            } catch (Throwable err) {
                GpLog.w("keeper", "despawn_skip", "err", String.valueOf(err));
            }
        }
        GpLog.i("keeper", "ghost_on", "pos", pasture.getPos().toShortString(), "cleared", cleared);
    }
}
