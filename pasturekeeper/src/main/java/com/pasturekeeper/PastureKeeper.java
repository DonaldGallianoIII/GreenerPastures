package com.pasturekeeper;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * PastureKeeper — server-side lag fix for big Cobblemon pasture farms.
 *
 * Per-pasture no-wander: the pasture ticker calls {@code togglePastureOn(viewersInRange > 0)} to
 * spawn the tethered mons as wandering entities when a player is near; for suppressed pastures we
 * force that to stay off, so the mons live as data in the box. Breeding keeps running (Cobbreeding
 * ticks the tethered data) and pasture loot keeps generating — and the entity count near the farm
 * stays low, which also clears Cobblemon's "too many Pokémon nearby" placement cap.
 *
 * Toggle a pasture by sneaking + right-clicking it with an empty hand. State is in-memory for now
 * (resets on restart) — NBT persistence is the next step. Loot collection is handled by
 * {@link PastureCollector}: drop a chest next to a pasture and its loot sweeps into it.
 */
public class PastureKeeper implements ModInitializer {

    /** Pastures with wandering suppressed, keyed by packed BlockPos. In-memory (resets on restart). */
    private static final Set<Long> suppressed = Collections.synchronizedSet(new HashSet<>());

    public static boolean isSuppressed(BlockPos pos) {
        return pos != null && suppressed.contains(pos.asLong());
    }

    /** Flip a pasture's no-wander state; returns the new state (true = suppressed). */
    public static boolean toggle(BlockPos pos) {
        long key = pos.asLong();
        synchronized (suppressed) {
            if (suppressed.remove(key)) return false;
            suppressed.add(key);
            return true;
        }
    }

    @Override
    public void onInitialize() {
        // Sneak + empty main hand + right-click a pasture block => toggle its wandering.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !player.isSneaking() || !player.getMainHandStack().isEmpty()) {
                return ActionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            BlockEntity be = world.getBlockEntity(pos);
            if (!(be instanceof PokemonPastureBlockEntity)) {
                be = world.getBlockEntity(pos.down());   // they clicked the top half
            }
            if (!(be instanceof PokemonPastureBlockEntity)) {
                return ActionResult.PASS;
            }
            if (!world.isClient) {
                boolean now = toggle(be.getPos());
                player.sendMessage(Text.literal("[PastureKeeper] this pasture — wandering "
                        + (now ? "OFF (mons stay in the box)" : "ON")), true);
            }
            return ActionResult.SUCCESS;   // consume the sneak-click so the pasture GUI doesn't open
        });
    }
}
