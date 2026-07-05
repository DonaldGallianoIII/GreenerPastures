package com.greenerpastures.mixin;

import com.cobblemon.mod.common.block.entity.PokeSnackBlockEntity;
import com.greenerpastures.drops.GpRepelInfluence;
import com.greenerpastures.pasture.breeding.GpComponents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;

/**
 * Carries a placed Ultra Compressed Snack's <b>repel payload</b> through the snack block's life
 * (Snack Overdrive pt.1): Cobblemon's {@code initializeFromItemStack} copies only ITS components, so our
 * {@code gp:repel_types} would die on placement without this. We capture it, persist it across chunk
 * reloads, hand it back on {@code toItemStack}, and lazily add ONE {@link GpRepelInfluence} to the snack's
 * spawner the first time it attempts a spawn. Cobblemon-owned methods → {@code remap=false}; the two NBT
 * methods are vanilla overrides → remapped names.
 */
@Mixin(PokeSnackBlockEntity.class)
public class PokeSnackRepelMixin {

    @Unique private Map<String, Integer> gp$repels = Map.of();
    @Unique private boolean gp$influenceAdded = false;

    @Inject(method = "initializeFromItemStack", at = @At("TAIL"), remap = false)
    private void gp$captureRepels(ItemStack stack, CallbackInfo ci) {
        Map<String, Integer> m = stack.get(GpComponents.REPEL_TYPES);
        gp$repels = m == null ? Map.of() : Map.copyOf(m);
    }

    @Inject(method = "toItemStack", at = @At("RETURN"), remap = false)
    private void gp$returnRepels(CallbackInfoReturnable<ItemStack> cir) {
        if (!gp$repels.isEmpty() && cir.getReturnValue() != null) {
            cir.getReturnValue().set(GpComponents.REPEL_TYPES, new HashMap<>(gp$repels));
        }
    }

    @Inject(method = "attemptSpawn", at = @At("HEAD"), remap = false)
    private void gp$addInfluenceOnce(PlayerEntity player, CallbackInfo ci) {
        if (gp$influenceAdded) return;
        gp$influenceAdded = true;
        try {
            PokeSnackBlockEntity self = (PokeSnackBlockEntity) (Object) this;
            self.getSpawner().getInfluences().add(new GpRepelInfluence(() -> gp$repels));
        } catch (Throwable ignored) {
            // influence wiring must never break the spawn attempt
        }
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void gp$saveRepels(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        if (gp$repels.isEmpty()) return;
        NbtCompound r = new NbtCompound();
        gp$repels.forEach(r::putInt);
        nbt.put("gp_repel_types", r);
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void gp$loadRepels(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        if (!nbt.contains("gp_repel_types")) return;
        NbtCompound r = nbt.getCompound("gp_repel_types");
        Map<String, Integer> m = new HashMap<>();
        for (String k : r.getKeys()) m.put(k, r.getInt(k));
        gp$repels = Map.copyOf(m);
    }
}
