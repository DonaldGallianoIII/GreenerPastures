package com.deuce.shinycollector;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Server-safe shiny-egg detection. Cobbreeding marks shiny eggs with a ★ (U+2605) in the
 * egg's display name (e.g. "Aggron Egg ★"), which {@code stack.getName()} resolves without
 * any client tooltip — so this works in block-entity tick logic on the server.
 */
public final class ShinyEggUtil {
    private ShinyEggUtil() {}

    private static final char SHINY_STAR = '★';

    public static boolean isEgg(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id.getPath().contains("pokemon_egg")
                || (id.getNamespace().contains("cobb") && id.getPath().contains("egg"));
    }

    public static boolean isShinyEgg(ItemStack stack) {
        if (!isEgg(stack)) return false;
        return stack.getName().getString().indexOf(SHINY_STAR) >= 0;
    }
}
