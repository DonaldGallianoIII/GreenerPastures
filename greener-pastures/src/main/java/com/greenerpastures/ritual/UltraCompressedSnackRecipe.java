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
 * <p><b>Compression deduplicates</b> (Deuce, 2026-07-04): Cobblemon's spawner stacks duplicate bait-effect
 * entries additively (decompile-verified, 1.7.3), so naive merging let 9 shiny-seasoned snacks stack 27
 * shiny effects — out of control. The merge therefore keeps each effect ONCE (first-seen grid order),
 * caps the list at {@link #MAX_EFFECTS}, and takes the per-flavour MAX rather than the sum. On-brand:
 * it's a Data Science mod — compressed data is deduplicated data.
 *
 * <p>The output stays a genuine {@code cobblemon:poke_snack} (the spawn logic is component-driven, not
 * identity-driven), so towers treat it as a normal snack.
 */
public class UltraCompressedSnackRecipe extends SpecialCraftingRecipe {
    /** Hard cap on DISTINCT bait effects an Ultra can carry — 3 pot-cooked snacks' worth of breadth. */
    public static final int MAX_EFFECTS = 9;

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
        java.util.LinkedHashSet<Identifier> mergedBait = new java.util.LinkedHashSet<>();   // DEDUP, first-seen order
        Map<Flavour, Integer> mergedFlavours = new EnumMap<>(Flavour.class);
        int snacks = 0;
        int dropped = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isOf(CobblemonItems.POKE_SNACK)) continue;
            snacks++;
            BaitEffectsComponent bait = s.get(CobblemonItemComponents.BAIT_EFFECTS);
            if (bait != null) {
                for (Identifier id : bait.getEffects()) {
                    if (mergedBait.contains(id)) { dropped++; continue; }              // duplicate — compression dedupes
                    if (mergedBait.size() >= MAX_EFFECTS) { dropped++; continue; }     // over the breadth cap
                    mergedBait.add(id);
                }
            }
            FlavourComponent fl = s.get(CobblemonItemComponents.FLAVOUR);
            if (fl != null) fl.getFlavours().forEach((k, v) -> mergedFlavours.merge(k, v, Math::max));   // MAX, not sum
        }
        if (snacks == 0) return ItemStack.EMPTY;

        ItemStack out = new ItemStack(CobblemonItems.POKE_SNACK);
        if (!mergedBait.isEmpty()) out.set(CobblemonItemComponents.BAIT_EFFECTS,
                new BaitEffectsComponent(new ArrayList<>(mergedBait)));
        if (!mergedFlavours.isEmpty()) out.set(CobblemonItemComponents.FLAVOUR, new FlavourComponent(mergedFlavours));
        out.set(DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.greenerpastures.ultra_compressed_snack")
                        .formatted(Formatting.GOLD).styled(st -> st.withItalic(false)));
        out.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("×" + snacks + " snacks · " + mergedBait.size() + "/" + MAX_EFFECTS
                                + " unique effects" + (dropped > 0 ? " · " + dropped + " deduplicated" : ""))
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
