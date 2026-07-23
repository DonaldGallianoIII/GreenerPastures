package com.greenerpastures.display;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.properties.UncatchableProperty;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.glitch.Missingno;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Display Suite's single Cobblemon seam - every Cobblemon class this feature touches is touched
 * HERE, and this class is only loaded on first block interaction (never at mod init - the
 * NeoForge/Connector lazy-probe lesson, spec §6).
 *
 * <p>Projection contract (spec §1): the spawned {@code PokemonEntity} is a re-derivable projection of
 * the disk in the pen - invulnerable, uncatchable, unbattleable, undespawnable, and (via
 * {@code EntityNoSaveMixin} on {@link DisplaySuite#PROJECTION_TAG}) never written to the world save.
 * {@code countsTowardsSpawnCap} deliberately stays TRUE: a stocked zoo suppresses wild spawns - the
 * exhibits are the fence (verified vs 1.7.3 {@code Spawner.calculateSpawnActionsForArea}, 2026-07-14).
 */
final class CobblemonProjector {
    private CobblemonProjector() {}

    /** Render scale for a roaming exhibit projection - a touch under life-size so a pen reads as a diorama
     *  rather than a wild encounter (Deuce, 2026-07-22). Statues stay independently scalable via the plinth. */
    static final float EXHIBIT_SCALE = 0.75f;

    /** The insert-gate facts {@link ExhibitRules} wants, read without keeping the mon around. */
    record DiskPeek(boolean loads, boolean glitch, String species, boolean shiny) {}

    /** Parse the SPECIMEN payload far enough to gate an insert. Never throws; a corrupt payload reads
     *  as {@code loads=false} and the disk stays with the player untouched. */
    static DiskPeek peek(ServerWorld world, NbtCompound specimenNbt) {
        try {
            Pokemon mon = new Pokemon().loadFromNBT(world.getRegistryManager(), specimenNbt);
            return new DiskPeek(true, Missingno.isMissingno(mon),
                    mon.getSpecies().getName(), mon.getShiny());
        } catch (Throwable t) {
            GpLog.w("display", "peek_fail", "err", String.valueOf(t));
            return new DiskPeek(false, false, "?", false);
        }
    }

    /** Deserialize the disk and spawn its projection beside the pen. Returns the entity UUID, or null
     *  if the payload refuses to load (pen keeps the disk; the sweep will retry next pass). */
    static UUID project(ServerWorld world, BlockPos penPos, NbtCompound specimenNbt) {
        try {
            Pokemon mon = new Pokemon().loadFromNBT(world.getRegistryManager(), specimenNbt);
            UncatchableProperty.INSTANCE.uncatchable().apply(mon);   // thrown balls refuse - projection copy only, the disk is untouched
            mon.setScaleModifier(EXHIBIT_SCALE);                     // roaming exhibits render a touch smaller than life-size (Deuce, 2026-07-22)

            PokemonEntity entity = new PokemonEntity(world, mon, CobblemonEntities.POKEMON);
            entity.setInvulnerable(true);
            entity.setPersistent();                                   // Cobblemon's despawner requires !isPersistent()
            entity.setCanPickUpLoot(false);
            entity.getDataTracker().set(PokemonEntity.getUNBATTLEABLE(), true);
            entity.addCommandTag(DisplaySuite.PROJECTION_TAG);        // EntityNoSaveMixin: never serialized

            Vec3d spot = Vec3d.ofBottomCenter(penPos.up());
            entity.refreshPositionAndAngles(spot.x, spot.y, spot.z,
                    world.random.nextFloat() * 360f, 0f);
            if (!world.spawnEntity(entity)) return null;
            return entity.getUuid();
        } catch (Throwable t) {
            GpLog.w("display", "project_fail", "pos", penPos.toShortString(), "err", String.valueOf(t));
            return null;
        }
    }

    /** How far a projection may roam before the sweep walks it home - Cobblemon's own pasture leash. */
    static double wanderDistance() {
        try {
            return Math.max(4, Cobblemon.INSTANCE.getConfig().getPastureMaxWanderDistance());
        } catch (Throwable t) {
            return 16.0;
        }
    }

    /** The statue's client payload, distilled from the full specimen - cosmetics only leave the server
     *  (spec §2). Returns {@link RenderSpec#EMPTY} if the payload refuses to load. */
    static RenderSpec renderSpec(ServerWorld world, NbtCompound specimenNbt) {
        try {
            Pokemon mon = new Pokemon().loadFromNBT(world.getRegistryManager(), specimenNbt);
            List<String> aspects = new ArrayList<>(mon.getAspects());
            if (mon.getShiny() && !aspects.contains("shiny")) aspects.add("shiny");
            return new RenderSpec(mon.getSpecies().getName(), mon.getForm().getName(),
                    aspects, mon.getGender().name(), 1.0f);
        } catch (Throwable t) {
            GpLog.w("display", "render_spec_fail", "err", String.valueOf(t));
            return RenderSpec.EMPTY;
        }
    }

    /** True when this entity is one of OUR projections (used by the sweep's orphan check). */
    static boolean isProjection(Entity entity) {
        return entity instanceof PokemonEntity && entity.getCommandTags().contains(DisplaySuite.PROJECTION_TAG);
    }
}
