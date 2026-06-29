package com.greenerpastures.mixin;

import com.greenerpastures.buff.DaemonValueBoost;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The read side of the <b>entity-scoped</b> Daemon enchant boosts (Lure, Luck of the Sea, Frost Walker). Each of
 * these 1.21.1 read seams carries the acting entity, so the boost is gated right here on the entity being a paid
 * fed-Daemon holder ({@link DaemonValueBoost}) — no thread-local needed. Pure read interception: it adds the
 * holder's tier to the value the game reads, never writes a stack, so there is no dupe/desync/NBT path, and it is
 * a no-op for everyone but a server-side paying holder.
 *
 * <p>Separate from {@link EnchantmentLevelMixin} (Fortune's {@code getLevel}, which has no entity and must be
 * thread-local-scoped). Looting rides {@code getEquipmentLevel} too, but {@link DaemonValueBoost} withholds it
 * (combat-adjacent) until confirmed, so this mixin already covers it the moment it's enabled there.
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentValueBoostMixin {

    @Inject(method = "getFishingLuckBonus(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/Entity;)I",
            at = @At("RETURN"), cancellable = true)
    private static void gp$boostFishingLuck(ServerWorld world, ItemStack stack, Entity entity,
                                            CallbackInfoReturnable<Integer> cir) {
        int original = cir.getReturnValueI();
        int boosted = DaemonValueBoost.fishingLuck(entity, original);
        if (boosted != original) cir.setReturnValue(boosted);
    }

    @Inject(method = "getFishingTimeReduction(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/Entity;)F",
            at = @At("RETURN"), cancellable = true)
    private static void gp$boostFishingTime(ServerWorld world, ItemStack stack, Entity entity,
                                            CallbackInfoReturnable<Float> cir) {
        float original = cir.getReturnValueF();
        float boosted = DaemonValueBoost.fishingTimeReduction(entity, original);
        if (boosted != original) cir.setReturnValue(boosted);
    }

    @Inject(method = "getEquipmentLevel(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/entity/LivingEntity;)I",
            at = @At("RETURN"), cancellable = true)
    private static void gp$boostEquipmentLevel(RegistryEntry<Enchantment> enchantment, LivingEntity entity,
                                               CallbackInfoReturnable<Integer> cir) {
        int original = cir.getReturnValueI();
        int boosted = DaemonValueBoost.equipmentLevel(enchantment, entity, original);
        if (boosted != original) cir.setReturnValue(boosted);
    }
}
