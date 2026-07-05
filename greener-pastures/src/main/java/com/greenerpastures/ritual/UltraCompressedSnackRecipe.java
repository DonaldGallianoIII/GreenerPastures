package com.greenerpastures.ritual;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.api.cooking.Flavour;
import com.cobblemon.mod.common.item.components.BaitEffectsComponent;
import com.cobblemon.mod.common.item.components.FlavourComponent;
import com.greenerpastures.GreenerPastures;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The <b>Ultra Compressed Snack</b> (Deuce, 2026-07-03): put poke snacks in a crafting grid → ONE snack
 * carrying the seasonings of all of them. Cobblemon's cooking pot caps a snack at 3 seasonings (3 input
 * slots); the Ultra's value is BREADTH beyond that — up to {@link #MAX_EFFECTS} DISTINCT effects in one
 * snack, which the pot could never cook.
 *
 * <p><b>Double-additive rule</b> (Deuce, 2026-07-04): Cobblemon's spawner stacks duplicate bait-effect
 * entries additively (decompile-verified, 1.7.3). Unbounded merging let 9 shiny-seasoned snacks stack 27
 * shiny effects — out of control — so stacking is allowed up to DOUBLE a single pot-cook and no more:
 * each effect keeps at most {@link #MAX_STACK_PER_EFFECT} copies (2 × the pot's 3 seasoning slots), the
 * list holds at most {@link #MAX_EFFECTS} DISTINCT effects (first-seen grid order), and each flavour sums
 * but caps at 2× the strongest single input. Deuce's canonical example: snacks of [chilan+ega+starf],
 * [3 starf], [3 starf], [3 chilan] → 4 chilan, 6 starf (7th compressed away), 1 ega.
 *
 * <p>The output stays a genuine {@code cobblemon:poke_snack} (the spawn logic is component-driven, not
 * identity-driven), so towers treat it as a normal snack.
 */
public class UltraCompressedSnackRecipe extends SpecialCraftingRecipe {
    /** Hard cap on DISTINCT bait effects an Ultra can carry — 3 pot-cooked snacks' worth of breadth. */
    public static final int MAX_EFFECTS = 9;

    /** Max copies of the SAME effect ("double additive, just not more"): 2 × a pot-cook's 3 slots. */
    public static final int MAX_STACK_PER_EFFECT = 6;

    public static final RecipeSerializer<UltraCompressedSnackRecipe> SERIALIZER =
            new SpecialRecipeSerializer<>(UltraCompressedSnackRecipe::new);

    public UltraCompressedSnackRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    public static void init() {
        Registry.register(Registries.RECIPE_SERIALIZER,
                Identifier.of(GreenerPastures.MOD_ID, "ultra_compressed_snack"), SERIALIZER);
    }

    /** Every non-empty slot must be a poke snack OR a Snack Repel can; at least one snack present. */
    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        int snacks = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (s.isOf(com.greenerpastures.economy.GpItems.SNACK_REPEL)) {
                if (!s.contains(com.greenerpastures.pasture.breeding.GpComponents.REPEL_TYPES)) return false;   // uncharged can — charge it with berries first
                continue;
            }
            if (!s.isOf(CobblemonItems.POKE_SNACK)) return false;
            snacks++;
        }
        return snacks >= 1;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        Map<Identifier, Integer> copies = new java.util.LinkedHashMap<>();   // first-seen order, raw counts
        Map<Flavour, Integer> flavourSum = new EnumMap<>(Flavour.class);
        Map<Flavour, Integer> flavourMax = new EnumMap<>(Flavour.class);
        int snacks = 0;
        List<Map.Entry<String, Integer>> cans = new ArrayList<>();
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isOf(com.greenerpastures.economy.GpItems.SNACK_REPEL)) {
                Map<String, Integer> payload = s.get(com.greenerpastures.pasture.breeding.GpComponents.REPEL_TYPES);
                if (payload != null) payload.forEach((t, m) -> cans.add(Map.entry(t, m)));
                continue;
            }
            if (!s.isOf(CobblemonItems.POKE_SNACK)) continue;
            snacks++;
            BaitEffectsComponent bait = s.get(CobblemonItemComponents.BAIT_EFFECTS);
            if (bait != null) for (Identifier id : bait.getEffects()) copies.merge(id, 1, Integer::sum);
            FlavourComponent fl = s.get(CobblemonItemComponents.FLAVOUR);
            if (fl != null) fl.getFlavours().forEach((k, v) -> {
                flavourSum.merge(k, v, Integer::sum);
                flavourMax.merge(k, v, Math::max);
            });
        }
        if (snacks == 0) return ItemStack.EMPTY;

        // Double-additive: keep up to 6 copies per effect, up to 9 distinct effects; count what compression ate.
        List<Identifier> merged = new ArrayList<>();
        int distinct = 0, compressed = 0;
        for (Map.Entry<Identifier, Integer> e : copies.entrySet()) {
            if (distinct >= MAX_EFFECTS) { compressed += e.getValue(); continue; }
            distinct++;
            int keep = Math.min(e.getValue(), MAX_STACK_PER_EFFECT);
            compressed += e.getValue() - keep;
            for (int k = 0; k < keep; k++) merged.add(e.getKey());
        }
        // Flavours follow the same spirit: sum, capped at double the strongest single input.
        Map<Flavour, Integer> mergedFlavours = new EnumMap<>(Flavour.class);
        flavourSum.forEach((k, v) -> mergedFlavours.put(k, Math.min(v, 2 * flavourMax.getOrDefault(k, 0))));

        // Snack Repel (Overdrive pt.1 rev 2 — Deuce's design): CHARGED cans carry their own {type → ÷magnitude}
        // payload; here they simply merge onto the snack (per-type sums, capped at double the strongest single
        // can — SnackRepelMath). Attract berries in the grid are untouched — the repel came pre-bottled.
        Map<String, Integer> repelSums = SnackRepelMath.mergeCans(cans);

        ItemStack out = new ItemStack(CobblemonItems.POKE_SNACK);
        if (!repelSums.isEmpty())
            out.set(com.greenerpastures.pasture.breeding.GpComponents.REPEL_TYPES,
                    new java.util.HashMap<>(repelSums));
        if (!merged.isEmpty()) out.set(CobblemonItemComponents.BAIT_EFFECTS, new BaitEffectsComponent(merged));
        if (!mergedFlavours.isEmpty()) out.set(CobblemonItemComponents.FLAVOUR, new FlavourComponent(mergedFlavours));
        out.set(DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.greenerpastures.ultra_compressed_snack")
                        .formatted(Formatting.GOLD).styled(st -> st.withItalic(false)));
        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("×" + snacks + " snacks · " + distinct + " effect" + (distinct == 1 ? "" : "s")
                        + " · " + merged.size() + " stacks"
                        + (compressed > 0 ? " · " + compressed + " compressed away" : ""))
                .formatted(Formatting.DARK_AQUA));
        repelSums.forEach((type, mag) -> lore.add(Text.literal(
                "🚫 ÷" + mag + " " + Character.toUpperCase(type.charAt(0)) + type.substring(1) + " Types")
                .formatted(Formatting.RED)));
        double speed = SnackSpeed.throughputFactor(biteValuesOf(merged));   // TRUE speed (Overdrive pt.2) — the Cobblemon tooltip's "Reduce Bite Time" line lies
        if (speed > 1.005) lore.add(Text.literal(String.format("⚡ spawn speed ×%.1f", speed)).formatted(Formatting.GOLD));
        out.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return out;
    }

    /** Every kept copy's bite_time value (per copy — the multiplicative stack SnackSpeed honors). */
    private static List<Double> biteValuesOf(List<Identifier> merged) {
        List<Double> out = new ArrayList<>();
        for (Identifier id : merged) {
            try {
                com.cobblemon.mod.common.api.fishing.SpawnBait bait =
                        com.cobblemon.mod.common.api.fishing.SpawnBaitEffects.getFromIdentifier(id);
                if (bait == null) continue;
                for (com.cobblemon.mod.common.api.fishing.SpawnBait.Effect ef : bait.getEffects()) {
                    if (com.cobblemon.mod.common.api.fishing.SpawnBait.Effects.INSTANCE.getBITE_TIME().equals(ef.getType())) {
                        out.add(ef.getValue());
                        break;
                    }
                }
            } catch (Throwable ignored) { }
        }
        return out;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
