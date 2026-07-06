package com.greenerpastures.core;

import com.greenerpastures.GreenerPastures;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.recipe.RecipeEntry;

import java.util.List;

/**
 * Every Greener Pastures recipe is ALWAYS in the recipe book (Deuce, live QA 2026-07-06: "our crafting
 * recipes don't show up in auto-craft until we have done one already"). Vanilla gates book entries behind
 * ingredient discovery / first craft - hostile to a mod whose whole loop starts at "craft a Notebook".
 * Unlocked on every join: {@code unlockRecipes} silently skips already-known entries, so returning players
 * get no toast and fresh players get exactly one "new recipes" flash.
 */
public final class RecipeBookUnlock {
    private RecipeBookUnlock() {}

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            try {
                List<RecipeEntry<?>> ours = server.getRecipeManager().values().stream()
                        .filter(r -> GreenerPastures.MOD_ID.equals(r.id().getNamespace()))
                        .toList();
                if (ours.isEmpty()) return;
                int fresh = handler.player.unlockRecipes(ours);
                if (fresh > 0) GpLog.i("guide", "recipes_unlocked", "player",
                        handler.player.getUuid().toString(), "count", fresh);
            } catch (Throwable t) {
                GpLog.w("guide", "recipe_unlock_failed", "err", t.toString());
            }
        }));
    }
}
