package com.greenerpastures.mixin;

import com.greenerpastures.buff.BuffId;
import com.greenerpastures.buff.DaemonBuffs;
import com.greenerpastures.buff.ResolvedBuffs;
import com.greenerpastures.core.GpLog;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Daemon HOOK buff — XP boost. While the player holds a fed Daemon with the {@code XP_BOOST} buff, scales up the
 * experience they gain by +25% per Mk tier (Mk III = +75%). A read-only multiply on the incoming amount —
 * never touches an ItemStack. Gated by {@link DaemonBuffs#paidBuffs} so it only applies while the buff is paid.
 */
@Mixin(ServerPlayerEntity.class)
public class XpBoostMixin {

    private static final double PER_TIER = 0.25;

    @ModifyVariable(method = "addExperience(I)V", at = @At("HEAD"), argsOnly = true)
    private int gp$boostXp(int experience) {
        if (experience <= 0) return experience;
        ResolvedBuffs paid = DaemonBuffs.paidBuffs((ServerPlayerEntity) (Object) this);
        if (paid == null) return experience;
        int tier = paid.tier(BuffId.XP_BOOST);
        if (tier <= 0) return experience;
        int boosted = (int) Math.round(experience * (1.0 + tier * PER_TIER));
        if (boosted <= experience) return experience;
        GpLog.d("buff", "xp_boost", "from", experience, "to", boosted, "tier", tier);
        return boosted;
    }
}
