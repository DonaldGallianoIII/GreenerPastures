package com.greenerpastures.buff;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pure resolver: given the {@link BuffConfig} and the held Daemon's level (Mk I/II/III ⇒ 1/2/3), compute which
 * buffs are active, at what tier, and the summed Data/sec to sustain them. No MC, no Gson — unit-tested.
 *
 * <p>Effective tier of a buff = {@code min(daemonLevel, setting.maxTier)}; a buff disabled, capped to 0, or
 * resolving to tier 0 (no Daemon) is omitted. Drain is <b>tier-scaled and summed</b>:
 * {@code Σ tier × costPerSec} over the active buffs — running more, or higher-tier, buffs burns Data faster.
 *
 * <p>Every bound is re-clamped here at use-site (not just in the record's compact constructor) so a hand-edited
 * or corrupt config — or an older Gson that bypasses the canonical constructor — can never produce a negative
 * cost, an over-ceiling tier, or a free-riding buff.
 */
public final class BuffResolver {
    private BuffResolver() {}

    public static ResolvedBuffs resolve(BuffConfig config, int daemonLevel) {
        if (config == null || !config.enabled() || daemonLevel <= 0) return ResolvedBuffs.NONE;

        int level = Math.min(daemonLevel, BuffSetting.TIER_CEILING);
        Map<BuffId, Integer> tiers = new EnumMap<>(BuffId.class);
        double cost = 0.0;

        for (BuffId id : BuffId.values()) {
            BuffSetting s = config.settingOf(id);
            if (s == null || !s.enabled()) continue;
            int cap = Math.max(0, Math.min(BuffSetting.TIER_CEILING, s.maxTier()));
            int tier = Math.min(level, cap);
            if (tier <= 0) continue;
            tiers.put(id, tier);
            cost += tier * Math.max(0.0, s.costPerSec());
        }

        return tiers.isEmpty() ? ResolvedBuffs.NONE : new ResolvedBuffs(Map.copyOf(tiers), cost, level);
    }
}
