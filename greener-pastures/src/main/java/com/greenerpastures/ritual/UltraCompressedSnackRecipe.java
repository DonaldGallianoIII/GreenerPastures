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

    /** Every non-empty slot must be a poke snack; at least one present, nothing else in the grid. */
    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        int snacks = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
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
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
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

        ItemStack out = new ItemStack(CobblemonItems.POKE_SNACK);
        if (!merged.isEmpty()) out.set(CobblemonItemComponents.BAIT_EFFECTS, new BaitEffectsComponent(merged));
        if (!mergedFlavours.isEmpty()) out.set(CobblemonItemComponents.FLAVOUR, new FlavourComponent(mergedFlavours));
        out.set(DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.greenerpastures.ultra_compressed_snack")
                        .formatted(Formatting.GOLD).styled(st -> st.withItalic(false)));
        out.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("×" + snacks + " snacks · " + distinct + " effect" + (distinct == 1 ? "" : "s")
                                + " · " + merged.size() + " stacks"
                                + (compressed > 0 ? " · " + compressed + " compressed away" : ""))
                        .formatted(Formatting.DARK_AQUA))));
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
