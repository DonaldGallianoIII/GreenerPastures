package com.greenerpastures.ritual;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.economy.GpItems;
import com.greenerpastures.pasture.breeding.GpComponents;
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

import java.util.Map;

/**
 * Charging a <b>Snack Repel</b> (Snack Overdrive pt.1 rev 2 - Deuce: "the can with the berry you DON'T want,
 * count scales it like the ultra cake"): one UNCHARGED can + 1–6 typed berries of ONE species → a charged
 * can labelled {@code 🚫 ÷N <Type> Types}. Magnitude = the berries' summed typing value (6-copy cap -
 * {@link SnackRepelMath#chargeMagnitude}). The charged can then goes into an Ultra Compressed Snack craft.
 * Deterministic (preview-safe); refuses mixed berry species, non-typed berries, or an already-charged can.
 */
public class SnackRepelChargeRecipe extends SpecialCraftingRecipe {

    public SnackRepelChargeRecipe(CraftingRecipeCategory category) {
        super(category);
    }

    public static final RecipeSerializer<SnackRepelChargeRecipe> SERIALIZER =
            new SpecialRecipeSerializer<>(SnackRepelChargeRecipe::new);

    public static void init() {
        Registry.register(Registries.RECIPE_SERIALIZER,
                Identifier.of(GreenerPastures.MOD_ID, "snack_repel_charge"), SERIALIZER);
    }

    /** Exactly one uncharged can + ≥1 berries, all the SAME item, that item carrying a typing payload. */
    private record Scan(int cans, ItemStack berry, int berries, boolean junk) {}

    private static Scan scan(CraftingRecipeInput input) {
        int cans = 0, berries = 0;
        ItemStack berry = ItemStack.EMPTY;
        boolean junk = false;
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack s = input.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (s.isOf(GpItems.SNACK_REPEL)) {
                if (s.contains(GpComponents.REPEL_TYPES)) junk = true;   // already charged - no topping up (v1)
                cans++;
                continue;
            }
            if (berry.isEmpty() || ItemStack.areItemsEqual(berry, s)) {
                berry = s;
                berries++;
            } else {
                junk = true;   // a second berry species (or any other item) - refuse
            }
        }
        return new Scan(cans, berry, berries, junk);
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        Scan sc = scan(input);
        if (sc.junk() || sc.cans() != 1 || sc.berries() < 1) return false;
        if (sc.berries() > SnackRepelMath.COPY_CAP) return false;   // a 7th berry would be silently wasted - refuse instead (review u1)
        return typingOf(sc.berry()) != null && SnackRepelMath.chargeMagnitude(sc.berries(), typingOf(sc.berry()).perCopyValue()) > 0;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup lookup) {
        Scan sc = scan(input);
        if (sc.junk() || sc.cans() != 1 || sc.berries() < 1 || sc.berries() > SnackRepelMath.COPY_CAP) return ItemStack.EMPTY;
        TypeValue tv = typingOf(sc.berry());
        if (tv == null) return ItemStack.EMPTY;
        int mag = SnackRepelMath.chargeMagnitude(sc.berries(), tv.perCopyValue());
        if (mag <= 0) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(GpItems.SNACK_REPEL);
        out.set(GpComponents.REPEL_TYPES, Map.of(tv.type(), mag));
        String pretty = Character.toUpperCase(tv.type().charAt(0)) + tv.type().substring(1);
        out.set(DataComponentTypes.LORE, new LoreComponent(java.util.List.of(
                Text.literal("🚫 ÷" + mag + " " + pretty + " Types").formatted(Formatting.RED),
                Text.literal("craft into an Ultra Compressed Snack").formatted(Formatting.DARK_GRAY))));
        return out;
    }

    private record TypeValue(String type, double perCopyValue) {}

    /** The berry's TYPING payload via Cobblemon's spawn-bait registry; null = not a typed berry. */
    private static TypeValue typingOf(ItemStack berry) {
        if (berry == null || berry.isEmpty()) return null;
        try {
            Identifier id = Registries.ITEM.getId(berry.getItem());
            com.cobblemon.mod.common.api.fishing.SpawnBait bait =
                    com.cobblemon.mod.common.api.fishing.SpawnBaitEffects.getFromIdentifier(id);
            if (bait == null) return null;
            for (com.cobblemon.mod.common.api.fishing.SpawnBait.Effect ef : bait.getEffects()) {
                if (!com.cobblemon.mod.common.api.fishing.SpawnBait.Effects.INSTANCE.getTYPING().equals(ef.getType())) continue;
                if (ef.getSubcategory() == null) continue;
                return new TypeValue(ef.getSubcategory().getPath().toLowerCase(java.util.Locale.ROOT), ef.getValue());
            }
        } catch (Throwable ignored) { }
        return null;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
