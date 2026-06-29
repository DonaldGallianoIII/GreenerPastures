package com.greenerpastures.mixin;

import com.greenerpastures.buff.DaemonEnchantBoost;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The read side of the Daemon enchant boost: adds the holder's resolved tier to the enchant level the loot
 * table reads. Gated entirely by {@link DaemonEnchantBoost}'s thread-local window (only set during a
 * fed-Daemon holder's block-drop resolution by {@link BlockDropBoostMixin}), so outside that window — tooltips,
 * anvils, grindstones, other players, the client — it is a no-op. Pure read interception: it never writes the
 * ItemStack, so there is no dupe/desync/NBT path.
 */
@Mixin(EnchantmentHelper.class)
public class EnchantmentLevelMixin {

    @Inject(method = "getLevel(Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/item/ItemStack;)I",
            at = @At("RETURN"), cancellable = true)
    private static void gp$boostLevel(RegistryEntry<Enchantment> enchantment, ItemStack stack,
                                      CallbackInfoReturnable<Integer> cir) {
        int original = cir.getReturnValueI();
        int boosted = DaemonEnchantBoost.boost(enchantment, original);
        if (boosted != original) cir.setReturnValue(boosted);
    }
}
