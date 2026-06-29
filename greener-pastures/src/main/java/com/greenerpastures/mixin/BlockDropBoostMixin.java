package com.greenerpastures.mixin;

import com.greenerpastures.buff.DaemonAutoSmelt;
import com.greenerpastures.buff.DaemonEnchantBoost;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Scopes the Daemon enchant boost to a block's loot resolution. {@code Block.getDroppedStacks(...,Entity,
 * ItemStack)} is the static drop-generator that has the <b>breaker</b> entity + tool and runs the loot
 * functions (where Fortune's level is read). HEAD opens a boost window for a fed-Daemon holder; RETURN closes
 * it — so the boost is live ONLY during this server-side loot roll, never for tooltips/anvils/other entities.
 * The level bump is applied by {@link EnchantmentLevelMixin} <i>during</i> generation; auto-smelt rewrites the
 * finished loot list at RETURN — so Fortune fattens the raw count and auto-smelt then converts it.
 */
@Mixin(Block.class)
public class BlockDropBoostMixin {

    @Inject(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("HEAD"))
    private static void gp$beginBoost(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity,
                                      Entity breaker, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (breaker instanceof ServerPlayerEntity sp) DaemonEnchantBoost.begin(sp);
        else DaemonEnchantBoost.end();
    }

    @Inject(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void gp$finishBoost(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity,
                                       Entity breaker, ItemStack tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        try {
            if (breaker instanceof ServerPlayerEntity sp) {
                List<ItemStack> smelted = DaemonAutoSmelt.smelt(sp, world, cir.getReturnValue());
                if (smelted != null) cir.setReturnValue(smelted);
            }
        } finally {
            DaemonEnchantBoost.end();                 // always close the window, even if auto-smelt throws
        }
    }
}
