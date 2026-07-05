package com.greenerpastures.pasture.keeper;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.analytics.Event;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * PastureKeeper - server-side lag fix for big Cobblemon pasture farms (the <b>"ghost pasture"</b>).
 *
 * <p>A suppressed pasture's tethered mons <b>never exist as roaming entities</b>, so the farm's entity count stays
 * near zero (which also clears Cobblemon's "too many Pokémon nearby" placement cap) - while the pasture keeps
 * ticking its tethered <i>data</i>, so breeding + loot run exactly as before. The toggle is symmetric (no per-tick
 * work either way):
 * <ol>
 *   <li><b>Block the spawn at the source.</b> {@code PokemonPastureBlockEntityMixin} redirects the single
 *       {@code World.spawnEntity} call inside Cobblemon's {@code tether()} - for a suppressed pasture it records
 *       the tether data but skips putting an entity in the world. New mons never spawn.</li>
 *   <li><b>Suppress ON → one-time despawn.</b> {@link #despawnTethered} removes the already-spawned roamers
 *       <i>once</i> with {@link Entity.RemovalReason#DISCARDED} (gone for good - never re-saved or reloaded) and
 *       re-asserts each mon's {@code tetheringId}, undoing the recall {@code DISCARDED} would otherwise trigger -
 *       so the tether data survives untouched.</li>
 *   <li><b>Suppress OFF → one-time re-materialise (increment 2).</b> {@link #respawnTethered} replays Cobblemon's
 *       own tether-spawn for each tethering that has no live entity - a fresh {@link PokemonEntity} from the stored
 *       {@code Pokemon}, positioned via the pasture's {@code makeSuitableY}, re-linked to the <i>existing</i>
 *       {@code Tethering} (no new tether). Cobblemon never auto-respawns a missing tethered mon (its
 *       {@code checkPokemon} has no spawn path), so we do it.</li>
 * </ol>
 *
 * <p>The suppressed flag lives on the persistent per-pasture {@link PastureData} (dimension + position keyed), so it
 * survives restarts; suppressed mons are discarded (not saved), so they don't come back on reload until un-suppressed.
 * Toggle by sneak + empty-hand right-click the pasture.
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
                        + (now ? "ON - mons stay as data, no roamers (lag fix)" : "OFF - roamers restored")), false);
            }
            return ActionResult.SUCCESS;   // consume the sneak-click so the pasture GUI doesn't open
        });
    }

    /** Set + persist a pasture's suppress state; ON despawns the roamers, OFF re-materialises them (data kept). */
    public static boolean setSuppressed(World world, PokemonPastureBlockEntity pasture, boolean suppress) {
        MinecraftServer server = world.getServer();
        if (server == null) return false;
        BlockPos pos = pasture.getPos();
        PastureRegistry reg = PastureRegistry.get(server);
        PastureData pd = reg.getOrCreate(world, pos);
        pd.suppressed = suppress;        // set BEFORE (re)spawning so the tether @Redirect agrees with our intent
        reg.markDirty();
        if (suppress) despawnTethered(world, pasture);
        else respawnTethered(world, pasture);
        return suppress;
    }

    /**
     * One-time: discard the pasture's currently-spawned tethered entities, keeping the tether data intact. Each is
     * removed with {@code DISCARDED} (gone for good, never reloads) and its {@code tetheringId} re-asserted to undo
     * {@code PokemonEntity.remove}'s recall. Found by scanning (entityId-independent, so it also works on mons we
     * re-materialised). Runs once on toggle - never on a tick.
     */
    private static void despawnTethered(World world, PokemonPastureBlockEntity pasture) {
        if (!(world instanceof ServerWorld sw)) return;
        int cleared = 0;
        for (PokemonEntity e : liveTetheredEntities(sw, pasture)) {
            try {
                PokemonPastureBlockEntity.Tethering teth = e.getTethering();
                UUID keep = (teth != null) ? teth.getTetheringId() : null;
                e.remove(Entity.RemovalReason.DISCARDED);     // gone for good - not saved, won't reload
                if (keep != null) e.getPokemon().setTetheringId(keep);   // undo the recall; tether data stays
                cleared++;
            } catch (Throwable err) {
                GpLog.w("keeper", "despawn_skip", "err", String.valueOf(err));
            }
        }
        GpLog.i("keeper", "ghost_on", "pos", pasture.getPos().toShortString(), "cleared", cleared);
    }

    /**
     * One-time (increment 2): re-materialise each tethering that has no live entity, replaying Cobblemon's own
     * tether-spawn - a fresh {@link PokemonEntity} from the stored {@code Pokemon}, positioned by the pasture's
     * {@code makeSuitableY}, re-linked to the <i>existing</i> {@code Tethering} (we don't create a new one). The
     * direct {@code spawnEntity} here is NOT the redirected one (that's scoped to {@code tether()}), so it always
     * spawns.
     */
    private static void respawnTethered(World world, PokemonPastureBlockEntity pasture) {
        if (!(world instanceof ServerWorld sw)) return;
        Set<UUID> alreadyLive = new HashSet<>();
        for (PokemonEntity e : liveTetheredEntities(sw, pasture)) {
            PokemonPastureBlockEntity.Tethering teth = e.getTethering();
            if (teth != null) alreadyLive.add(teth.getTetheringId());
        }
        BlockPos pos = pasture.getPos();
        int spawned = 0;
        for (PokemonPastureBlockEntity.Tethering t : pasture.getTetheredPokemon()) {
            if (alreadyLive.contains(t.getTetheringId())) continue;   // still has a live entity - leave it
            try {
                Pokemon p = t.getPokemon();
                if (p == null) continue;                          // no stored Pokemon (Cobblemon's checkPokemon releases it)
                PokemonEntity e = new PokemonEntity(sw, p, CobblemonEntities.POKEMON);
                e.calculateDimensions();
                e.setPosition(suitableSpawn(sw, pasture, e, spawned));   // ground them around the pasture (null-safe)
                p.setTetheringId(t.getTetheringId());             // pokemon.tetheringId == tethering.id → checkPokemon keeps it
                e.setTethering(t);                                // re-link to the EXISTING tethering - not a new tether
                sw.spawnEntity(e);
                spawned++;
            } catch (Throwable err) {
                GpLog.w("keeper", "respawn_skip", "err", String.valueOf(err));
            }
        }
        GpLog.i("keeper", "ghost_off", "pos", pos.toShortString(), "spawned", spawned);
    }

    /** Ring of directions we spread re-materialised mons across, so they don't all stack on one side of the pasture. */
    private static final Direction[] SPAWN_RING = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    /**
     * A natural re-materialise position: stand each mon on the ground <b>right next to the pasture</b>, spread around
     * it in a 4-way ring that steps one block further out every 4 mons - instead of dumping them all a few blocks off
     * one side (the first-pass bug Deuce caught). Horizontal offset matches the pasture's Y; the vertical placement
     * reuses Cobblemon's own search ({@code getBoxAt} + {@code makeSuitableY}) to land on the real floor within ±16.
     * {@code makeSuitableY} <b>returns {@code null}</b> when it finds no floor (the original NPE was passing the solid
     * pasture block and dereffing that null) - we fall back to the adjacent column itself, so it never NPEs.
     * {@code -0.5} on Y puts the feet on the floor (matching Cobblemon's tether spawn exactly).
     */
    private static Vec3d suitableSpawn(ServerWorld world, PokemonPastureBlockEntity pasture, PokemonEntity entity, int index) {
        BlockPos pos = pasture.getPos();
        Direction dir = SPAWN_RING[Math.floorMod(index, SPAWN_RING.length)];
        int ring = 1 + index / SPAWN_RING.length;            // first 4 mons hug the pasture; the next 4 step one out…
        int reach = Math.max(1, (int) Math.ceil(entity.getBoundingBox().getLengthX())) * ring;
        BlockPos base = pos.add(dir.getVector().multiply(reach));   // adjacent column at the pasture's own Y
        Box box = entity.getDimensions(EntityPose.STANDING).getBoxAt(base.toCenterPos().subtract(0.0, 0.5, 0.0));
        BlockPos fixed = pasture.makeSuitableY(world, base, entity, box);
        BlockPos at = (fixed != null) ? fixed : base;       // null = no floor within ±16 → fall back (never NPE)
        return at.toCenterPos().subtract(0.0, 0.5, 0.0);
    }

    /** This pasture's currently-spawned tethered mons, found by scanning nearby {@link PokemonEntity}s whose
     *  {@code Tethering} belongs here (robust to stale {@code Tethering.entityId} after a re-materialise). */
    private static List<PokemonEntity> liveTetheredEntities(ServerWorld world, PokemonPastureBlockEntity pasture) {
        Set<UUID> ids = new HashSet<>();
        for (PokemonPastureBlockEntity.Tethering t : pasture.getTetheredPokemon()) ids.add(t.getTetheringId());
        Box box = new Box(pasture.getPos()).expand(64.0);
        return world.getEntitiesByClass(PokemonEntity.class, box, e -> {
            PokemonPastureBlockEntity.Tethering teth = e.getTethering();
            return teth != null && ids.contains(teth.getTetheringId());
        });
    }
}
