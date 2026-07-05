package com.greenerpastures.egg.highlighter;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Classifies Cobbreeding eggs.
 *
 * Confirmed in-game on this pack:
 *  - Egg items are typed but NOT shiny-tagged: cobbreeding:normal_<type>_pokemon_egg
 *    (there is no shiny_* item id).
 *  - Shiny status is a property (registered component, not custom_data) - but it IS shown
 *    in the (readable) tooltip, so we detect it there.
 *
 * Performance: results are cached per ItemStack instance. Slot stacks are stable while a
 * screen is open, so each egg's tooltip is built ~once, not every frame - safe for huge banks.
 */
public final class ShinyEggDetector {
    private ShinyEggDetector() {}

    private static final int CACHE_MAX = 4096;   // perf-audit M6: LRU bound (gradual eviction, no full-clear storm)
    private static final Map<ItemStack, Boolean> CACHE = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<ItemStack, Boolean>(256, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<ItemStack, Boolean> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    /** Any Cobbreeding Pokémon egg (any type, shiny or not). */
    public static boolean isEgg(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id.getPath().contains("pokemon_egg")
                || (id.getNamespace().contains("cobb") && id.getPath().contains("egg"));
    }

    /** Only a SHINY egg. Cached for big-bank performance. */
    public static boolean isShinyEgg(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Boolean cached = CACHE.get(stack);
        if (cached != null) return cached;                  // cache hit: skip the isEgg + compute recheck (M5)
        boolean result = isEgg(stack) && compute(stack);
        CACHE.put(stack, result);                           // LRU evicts the eldest past CACHE_MAX (M6)
        return result;
    }

    private static boolean compute(ItemStack stack) {
        // Primary: the shiny marker in the readable tooltip (the same thing you see on hover).
        if (tooltipSaysShiny(stack)) return true;
        // Belt-and-suspenders: a separate shiny item id, or a custom_data flag, if either ever applies.
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (id.getPath().contains("shiny")) return true;
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        return cd != null && nbtHasShinyTrue(cd.copyNbt());
    }

    // Cobbreeding marks shiny eggs with a BLACK STAR (U+2605) appended to the egg name,
    // e.g. "Aggron Egg [star]". Confirmed from the in-game tooltip dump.
    private static final char SHINY_STAR = '★';

    private static boolean tooltipSaysShiny(ItemStack stack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        try {
            List<Text> lines = stack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
            for (Text t : lines) {
                String s = t.getString();
                if (s.indexOf(SHINY_STAR) >= 0) return true;          // ★ = shiny (primary)
                String low = s.toLowerCase();                          // word fallback
                if (low.contains("shiny") && !low.contains("no") && !low.contains("false") && !low.contains("not")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // some modded tooltips can throw; ignore and treat as non-shiny
        }
        return false;
    }

    private static boolean nbtHasShinyTrue(NbtCompound nbt) {
        for (String key : nbt.getKeys()) {
            NbtElement el = nbt.get(key);
            if (el == null) continue;
            if (key.toLowerCase().contains("shiny") && isTruthy(el)) return true;
            if (el instanceof NbtCompound child) {
                if (nbtHasShinyTrue(child)) return true;
            } else if (el instanceof NbtList list) {
                for (NbtElement e : list) {
                    if (e instanceof NbtCompound c && nbtHasShinyTrue(c)) return true;
                }
            }
        }
        return false;
    }

    private static boolean isTruthy(NbtElement el) {
        if (el instanceof NbtByte b) return b.byteValue() != 0;
        if (el instanceof NbtInt i) return i.intValue() != 0;
        if (el instanceof NbtString s) return s.asString().equalsIgnoreCase("true");
        return false;
    }
}
