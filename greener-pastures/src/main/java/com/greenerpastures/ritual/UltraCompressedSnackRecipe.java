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
 * carrying <b>every seasoning from every input</b>. Cobblemon's cooking pot caps a snack at 3 seasonings
 * (3 input slots) — but its bait data is an unbounded {@code cobblemon:bait_effects} identifier list that the
 * spawner reads by full iteration, with duplicate entries stacking <i>additively</i> (decompile-verified,
 * Cobblemon 1.7.3). Compression is therefore real power: 9 snacks → one mega-bait the pot could never cook.
 *
 * <p>The output stays a genuine {@code cobblemon:poke_snack} (the spawn logic is component-driven, not
 * identity-driven), so towers treat it as a normal snack — just with the merged effect list, summed flavours,
 * a custom name, and a lore line recording the compression count.
 */
public class UltraCompressedSnackRecipe extends SpecialCraftingRecipe {
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
        List<Identifier> mergedBait = new ArrayList<>();
        Map<Flavour, Integer> mergedFlavours = new EnumMap<>(Flavour.class);
        int snacks = 0;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (!s.isOf(CobblemonItems.POKE_SNACK)) continue;
            snacks++;
            BaitEffectsComponent bait = s.get(CobblemonItemComponents.BAIT_EFFECTS);
            if (bait != null) mergedBait.addAll(bait.getEffects());   // duplicates KEPT — they stack additively
            FlavourComponent fl = s.get(CobblemonItemComponents.FLAVOUR);
            if (fl != null) fl.getFlavours().forEach((k, v) -> mergedFlavours.merge(k, v, Integer::sum));
        }
        if (snacks == 0) return ItemStack.EMPTY;

        ItemStack out = new ItemStack(CobblemonItems.POKE_SNACK);
        if (!mergedBait.isEmpty()) out.set(CobblemonItemComponents.BAIT_EFFECTS, new BaitEffectsComponent(mergedBait));
        if (!mergedFlavours.isEmpty()) out.set(CobblemonItemComponents.FLAVOUR, new FlavourComponent(mergedFlavours));
        out.set(DataComponentTypes.CUSTOM_NAME,
                Text.translatable("item.greenerpastures.ultra_compressed_snack")
                        .formatted(Formatting.GOLD).styled(st -> st.withItalic(false)));
        out.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("×" + snacks + " snacks compressed · " + mergedBait.size() + " bait effects")
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
