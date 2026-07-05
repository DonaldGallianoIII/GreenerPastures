package com.greenerpastures.mixin;

import com.greenerpastures.buff.BuffId;
import com.greenerpastures.buff.DaemonBuffs;
import com.greenerpastures.buff.ResolvedBuffs;
import com.greenerpastures.core.GpLog;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Set;

/**
 * Daemon HOOK buff - potion duration+. While the player holds a fed Daemon with the {@code POTION_DURATION} buff,
 * lengthens the duration of incoming <b>utility</b> status effects by +50% per Mk tier (Mk III = +150%).
 *
 * <p>Deliberately an <b>allowlist of non-combat effects only</b> (Night Vision, Water Breathing, Fire Resistance,
 * Conduit Power, Dolphin's Grace, Slow Falling, Luck) - so it's unambiguously PvP-neutral (no Strength/Speed/
 * Resistance/Regen/Absorption ever extended), honoring the worker-not-fighter rule. Haste/Saturation are omitted
 * because the Daemon grants those directly. Replaces the incoming instance with a longer-duration copy; never
 * touches an ItemStack.
 */
@Mixin(LivingEntity.class)
public class PotionDurationMixin {

    private static final Set<RegistryEntry<StatusEffect>> EXTENDABLE = Set.of(
            StatusEffects.NIGHT_VISION, StatusEffects.WATER_BREATHING, StatusEffects.FIRE_RESISTANCE,
            StatusEffects.CONDUIT_POWER, StatusEffects.DOLPHINS_GRACE, StatusEffects.SLOW_FALLING,
            StatusEffects.LUCK);
    private static final double PER_TIER = 0.5;

    @ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private StatusEffectInstance gp$extendDuration(StatusEffectInstance effect) {
        if (effect == null || effect.isInfinite() || effect.getDuration() <= 0) return effect;
        if (!EXTENDABLE.contains(effect.getEffectType())) return effect;
        if (!((Object) this instanceof ServerPlayerEntity sp)) return effect;

        ResolvedBuffs paid = DaemonBuffs.paidBuffs(sp);
        if (paid == null) return effect;
        int tier = paid.tier(BuffId.POTION_DURATION);
        if (tier <= 0) return effect;

        long scaled = Math.round(effect.getDuration() * (1.0 + tier * PER_TIER));
        int newDuration = (int) Math.min(Integer.MAX_VALUE, scaled);
        if (newDuration <= effect.getDuration()) return effect;

        GpLog.d("buff", "potion_extend", "from", effect.getDuration(), "to", newDuration, "tier", tier);
        return new StatusEffectInstance(effect.getEffectType(), newDuration, effect.getAmplifier(),
                effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon());
    }
}
