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
 * <b>Snack Overdrive</b> — both halves live here. Pt.1: carries a placed snack's <b>repel payload</b> through the block's life
 * (Snack Overdrive pt.1): Cobblemon's {@code initializeFromItemStack} copies only ITS components, so our
 * {@code gp:repel_types} would die on placement without this. We capture it, persist it across chunk
 * reloads, hand it back on {@code toItemStack}, and lazily add ONE {@link GpRepelInfluence} to the snack's
 * spawner the first time it attempts a spawn. Cobblemon-owned methods → {@code remap=false}; the two NBT
 * methods are vanilla overrides → remapped names.
 */
@Mixin(PokeSnackBlockEntity.class)
public class PokeSnackOverdriveMixin implements com.greenerpastures.drops.GpRepelHost {

    @Unique private Map<String, Integer> gp$repels = Map.of();

    @Override
    public Map<String, Integer> gp$getRepels() {
        return gp$repels;
    }

    /** Pt.2 spawn-speed credit bank (SnackSpeed): fractional spawns carried between random ticks. */
    @Unique private double gp$credits = 0.0;

    /** Every bite_time entry, per copy — the values vanilla throws away by picking one at random. */
    @Unique
    private static java.util.List<Double> gp$biteValues(PokeSnackBlockEntity be) {
        java.util.List<Double> out = new java.util.ArrayList<>();
        try {
            for (com.cobblemon.mod.common.api.fishing.SpawnBait.Effect e : be.getBaitEffects()) {
                if (com.cobblemon.mod.common.api.fishing.SpawnBait.Effects.INSTANCE.getBITE_TIME().equals(e.getType())) {
                    out.add(e.getValue());
                }
            }
        } catch (Throwable ignored) { }
        return out;
    }

    /** Pt.2: replace the vanilla countdown (ONE random bite_time entry, hard 2× cap — decompile-verified
     *  rot) with the credit accumulator: every copy counts multiplicatively, expected value exact, burst-
     *  capped, credits hold (capped) while nobody's in range. Fail-soft: ANY error before the cancel falls
     *  straight through to vanilla randomTick. */
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true, remap = false)
    private void gp$creditedRandomTick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        try {
            PokeSnackBlockEntity self = (PokeSnackBlockEntity) (Object) this;
            net.minecraft.world.World world = self.getWorld();
            if (world == null) return;   // vanilla path
            double mult = com.greenerpastures.ritual.SnackSpeed.trueMultiplier(gp$biteValues(self));
            PlayerEntity near = world.getClosestPlayer(
                    self.getPos().getX(), self.getPos().getY(), self.getPos().getZ(),
                    com.cobblemon.mod.common.Cobblemon.INSTANCE.getConfig().getMaximumSpawningZoneDistanceFromPlayer(),
                    false);
            com.greenerpastures.ritual.SnackSpeed.CreditRoll roll =
                    com.greenerpastures.ritual.SnackSpeed.onRandomTick(gp$credits, mult, near != null);
            gp$credits = roll.remaining();
            for (int i = 0; i < roll.spawns(); i++) self.attemptSpawn(near);
            self.markDirty();
            if (roll.spawns() > 0 && com.greenerpastures.core.GpLog.on(com.greenerpastures.core.GpLog.Level.DEBUG)) {
                com.greenerpastures.core.GpLog.d("snack", "speed_tick", "pos", self.getPos().toShortString(),
                        "mult", String.format("%.3f", mult), "spawns", roll.spawns(),
                        "banked", String.format("%.2f", roll.remaining()));
            }
            ci.cancel();
        } catch (Throwable ignored) {
            // computed nothing → vanilla randomTick proceeds untouched
        }
    }

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

    /** Join at SPAWNER CREATION, not first spawn attempt: Cobblenav's snack inspector reads
     *  {@code getSpawner().getInfluences()} the moment it's created — a lazy join made the Nav (and the
     *  first spawn attempt) compute WITHOUT the repel (Deuce caught it Nav-in-hand, 2026-07-04). The
     *  lambda is Cobblemon's synthetic spawner initializer; static target, {@code remap=false}. */
    @Inject(method = "spawner_delegate$lambda$0", at = @At("RETURN"), remap = false)
    private static void gp$armAtSpawnerCreation(PokeSnackBlockEntity be,
            CallbackInfoReturnable<com.cobblemon.mod.common.api.spawning.spawner.FixedAreaSpawner> cir) {
        try {
            com.greenerpastures.drops.GpRepelHost host = (com.greenerpastures.drops.GpRepelHost) be;
            cir.getReturnValue().getInfluences().add(new GpRepelInfluence(host::gp$getRepels));
            if (!host.gp$getRepels().isEmpty()) com.greenerpastures.core.GpLog.i("repel", "armed",
                    "pos", be.getPos().toShortString(), "types", host.gp$getRepels().toString());
        } catch (Throwable ignored) {
            // influence wiring must never break spawner creation
        }
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void gp$saveRepels(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        if (gp$credits > 0) nbt.putDouble("gp_spawn_credits", gp$credits);
        if (gp$repels.isEmpty()) return;
        NbtCompound r = new NbtCompound();
        gp$repels.forEach(r::putInt);
        nbt.put("gp_repel_types", r);
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void gp$loadRepels(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        gp$credits = Math.min(com.greenerpastures.ritual.SnackSpeed.CREDIT_CAP,
                Math.max(0, nbt.getDouble("gp_spawn_credits")));
        if (!nbt.contains("gp_repel_types")) return;
        NbtCompound r = nbt.getCompound("gp_repel_types");
        Map<String, Integer> m = new HashMap<>();
        for (String k : r.getKeys()) m.put(k, r.getInt(k));
        gp$repels = Map.copyOf(m);
    }
}
