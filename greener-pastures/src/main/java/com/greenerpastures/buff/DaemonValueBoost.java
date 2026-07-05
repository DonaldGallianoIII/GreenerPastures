package com.greenerpastures.buff;

import com.greenerpastures.core.GpLog;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;

/**
 * The entity-scoped Daemon enchant boosts - the value-effect enchants whose 1.21.1 read seam <i>carries the
 * acting entity</i>, so (unlike Fortune, which needs {@link DaemonEnchantBoost}'s thread-local) the boost can be
 * gated directly on "is this a fed-Daemon holder with the buff?" right at the read. Pure read interception (the
 * mixin {@code EnchantmentValueBoostMixin} adds our tier to the value the game reads) - <b>never writes a stack
 * or NBT</b>, so there is no dupe/desync surface, and it only ever fires server-side for a paid holder.
 *
 * <p>Covers <b>Lure</b> ({@code getFishingTimeReduction}), <b>Luck of the Sea</b> ({@code getFishingLuckBonus}),
 * <b>Frost Walker</b> and <b>Looting</b> (both via {@code getEquipmentLevel}). Looting is the one combat-adjacent
 * buff - Deuce opted it in (default-on); it boosts drops from mobs the holder kills. Unbreaking is absent because
 * its seam ({@code getItemDamage}) carries no entity, so it can't be scoped to a holder cleanly.
 */
public final class DaemonValueBoost {
    private DaemonValueBoost() {}

    /** Vanilla Lure reduces fishing time 5s per level; the Daemon mirrors that per tier. */
    private static final float LURE_SECONDS_PER_TIER = 5.0f;

    /** Equipment-level enchants the Daemon boosts (enchant → buff): Frost Walker (movement QOL) and Looting
     *  (Deuce opted Looting in, default-on - it's the one combat-adjacent buff, boosting drops from mobs the
     *  holder kills via the {@code EnchantedCountIncreaseLootFunction} → {@code getEquipmentLevel(LOOTING, killer)} seam). */
    private static final Map<RegistryKey<Enchantment>, BuffId> EQUIP = Map.of(
            Enchantments.FROST_WALKER, BuffId.FROST_WALKER,
            Enchantments.LOOTING, BuffId.LOOTING);

    /** +tier fishing luck (treasure odds) for a fed-Daemon holder running Luck of the Sea. */
    public static int fishingLuck(Entity owner, int original) {
        int tier = paidTier(owner, BuffId.LUCK_OF_THE_SEA);
        if (tier <= 0) return original;
        GpLog.d("buff", "fish_luck", "from", original, "to", original + tier);
        return original + tier;
    }

    /** +5s/tier fishing-time reduction (faster bites) for a fed-Daemon holder running Lure. */
    public static float fishingTimeReduction(Entity owner, float original) {
        int tier = paidTier(owner, BuffId.LURE);
        if (tier <= 0) return original;
        float boosted = original + LURE_SECONDS_PER_TIER * tier;
        GpLog.d("buff", "fish_lure", "from", original, "to", boosted);
        return boosted;
    }

    /**
     * +tier to an equipment-level read for a boosted enchant on a fed-Daemon holder (Frost Walker, Looting).
     * Unlike Fortune, these grant <i>from nothing</i> (no boots / no looting weapon needed) - the Daemon itself is
     * the source. Only enchants in {@link #EQUIP} are ever touched; every other {@code getEquipmentLevel} query
     * fast-fails the key check and returns unchanged.
     */
    public static int equipmentLevel(RegistryEntry<Enchantment> enchantment, LivingEntity entity, int original) {
        if (enchantment == null) return original;
        for (Map.Entry<RegistryKey<Enchantment>, BuffId> e : EQUIP.entrySet()) {
            if (enchantment.matchesKey(e.getKey())) {
                int tier = paidTier(entity, e.getValue());
                if (tier <= 0) return original;
                int boosted = original + tier;
                GpLog.d("buff", "equip_boost", "ench", e.getKey().getValue().toString(),
                        "from", original, "to", boosted);
                return boosted;
            }
        }
        return original;
    }

    /** The buff tier the entity is paying for this second, or 0 if it isn't a fed-Daemon-holding server player. */
    private static int paidTier(Entity owner, BuffId buff) {
        if (!(owner instanceof ServerPlayerEntity sp)) return 0;
        ResolvedBuffs paid = DaemonBuffs.paidBuffs(sp);
        return paid == null ? 0 : paid.tier(buff);
    }
}
