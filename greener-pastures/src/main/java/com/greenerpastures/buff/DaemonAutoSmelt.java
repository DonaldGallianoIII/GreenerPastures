package com.greenerpastures.buff;

import com.greenerpastures.core.GpLog;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Auto-smelt-on-mine: while a fed Daemon holder with the {@code AUTO_SMELT} buff breaks a block, each dropped
 * stack that has a furnace recipe is replaced by its smelted result (raw_iron → iron_ingot, sand → glass, …).
 * Runs at the END of {@code Block.getDroppedStacks} (see {@link com.greenerpastures.mixin.BlockDropBoostMixin}),
 * so it composes with the Fortune boost applied during generation — Fortune fattens the raw count, auto-smelt
 * then converts it. Like the enchant boost, it never mutates the player's gear; it only rewrites the loot list.
 *
 * <p>Binary (on/off) per the buff being active — the tier doesn't change the result, only the rental cost.
 */
public final class DaemonAutoSmelt {
    private DaemonAutoSmelt() {}

    /** Smelt the drops if the breaker has an active auto-smelt buff; returns a new list, or {@code null} if unchanged. */
    public static List<ItemStack> smelt(ServerPlayerEntity breaker, ServerWorld world, List<ItemStack> drops) {
        ResolvedBuffs paid = DaemonBuffs.paidBuffs(breaker);
        if (paid == null || paid.tier(BuffId.AUTO_SMELT) <= 0 || drops.isEmpty()) return null;

        boolean changed = false;
        List<ItemStack> out = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            ItemStack smelted = smeltOne(world, drop);
            if (smelted != null) {
                out.add(smelted);
                changed = true;
            } else {
                out.add(drop);
            }
        }
        return changed ? out : null;
    }

    /** The smelted form of one dropped stack (whole stack), or {@code null} if it has no smelting recipe. */
    private static ItemStack smeltOne(ServerWorld world, ItemStack in) {
        if (in.isEmpty()) return null;
        SingleStackRecipeInput input = new SingleStackRecipeInput(in);
        Optional<RecipeEntry<SmeltingRecipe>> recipe =
                world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, input, world);
        if (recipe.isEmpty()) return null;
        ItemStack result = recipe.get().value().craft(input, world.getRegistryManager());
        if (result.isEmpty()) return null;
        ItemStack out = result.copy();
        out.setCount(result.getCount() * in.getCount());        // smelt the entire dropped stack
        GpLog.d("buff", "auto_smelt", "to", out.getItem().toString(), "n", out.getCount());
        return out;
    }
}
