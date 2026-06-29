package com.greenerpastures.goal;

import com.greenerpastures.biobank.EggSummary;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.pasture.breeding.CobbreedingBridge;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The MC side of the breeding-goal tracker — <b>non-destructive</b> (track-only): each egg a pasture lays is folded
 * into the pasture owner's {@link GoalProgress}, and the moment the goal is first reached the owner gets a ping. No
 * culling, no item changes. Fully wrapped so a tracking hiccup can never break egg-gen.
 */
public final class GoalTracker {
    private GoalTracker() {}

    /** Record one laid egg against the owner's goal (if any); ping the owner the instant the goal is reached. */
    public static void recordLaid(World world, UUID owner, CobbreedingBridge.BredEgg egg) {
        try {
            if (owner == null || egg == null || world == null) return;
            BreedingGoal goal = GoalStore.goalOf(owner);
            if (goal == null) return;                          // no active hunt → nothing to track
            boolean wasReached = GoalStore.progressOf(owner).reached(goal);

            EggSummary summary = new EggSummary(egg.species(), egg.shiny(), egg.ivTotal(), egg.perfectIvs());
            GoalProgress now = GoalStore.recordEgg(owner, summary);
            GpLog.d("goal", "egg", "owner", owner.toString(), "checked", now.checked(), "matched", now.matched());

            if (!wasReached && now.reached(goal) && world.getServer() != null) {
                ServerPlayerEntity p = world.getServer().getPlayerManager().getPlayer(owner);
                if (p != null) {
                    p.sendMessage(Text.literal("🎯 Breeding goal reached: " + goal.describe()
                            + " — after " + now.checked() + " eggs!"), false);
                    p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
                    GpLog.i("goal", "reached", "owner", owner.toString(), "goal", goal.describe(), "eggs", now.checked());
                }
            }
        } catch (Throwable t) {
            // tracking must never break egg-gen
        }
    }
}
