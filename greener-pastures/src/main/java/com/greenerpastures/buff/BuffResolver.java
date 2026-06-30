package com.greenerpastures.buff;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

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
        return resolve(config, daemonLevel, null);
    }

    /**
     * As {@link #resolve(BuffConfig, int)}, but only considers the buffs in {@code applicable} ({@code null} ⇒
     * every buff). The MC adapter passes exactly the buffs it can currently deliver, so a player is <b>never
     * billed Data for a buff the mod hasn't wired up yet</b> — as each buff's adapter lands, it's added to that
     * set and starts being charged for. Per-buff (not per-category) so a category can be delivered piecemeal
     * (e.g. the item-magnet HOOK ships before auto-smelt/vein-mine).
     */
    public static ResolvedBuffs resolve(BuffConfig config, int daemonLevel, Set<BuffId> applicable) {
        if (config == null || !config.enabled() || daemonLevel <= 0) return ResolvedBuffs.NONE;

        int level = Math.min(daemonLevel, BuffSetting.TIER_CEILING);
        Map<BuffId, Integer> tiers = new EnumMap<>(BuffId.class);
        double cost = 0.0;

        for (BuffId id : BuffId.values()) {
            if (applicable != null && !applicable.contains(id)) continue;
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

    /**
     * Resolve a Daemon's <b>compiled loadout</b> (BUG-004) instead of a single global Mk tier: each installed
     * buff runs at <i>its own</i> chosen level, re-clamped to {@code min(level, cfg cap, +3 ceiling)}, and the
     * bill is {@code Σ tier × costPerSec} over <b>only the installed buffs</b> — so a one-buff Daemon is cheap
     * and you pay for exactly what you compiled. {@code deliverable} ({@code null} ⇒ all) gates to the buffs the
     * adapter can currently apply, so — exactly as {@link #resolve(BuffConfig, int, Set)} — a player is never
     * billed for a buff the mod hasn't wired up yet. The returned {@code daemonLevel} is the loadout's highest
     * effective tier (an informational summary; there's no longer one global level).
     *
     * <p>Every bound is re-clamped here at use-site, so a hand-edited component or an over-level the Compiler
     * never should have written still can't grant an over-ceiling tier, a negative cost, or a free-riding buff.
     */
    public static ResolvedBuffs resolveLoadout(BuffConfig config, Map<BuffId, Integer> loadout, Set<BuffId> deliverable) {
        if (config == null || !config.enabled() || loadout == null || loadout.isEmpty()) return ResolvedBuffs.NONE;

        Map<BuffId, Integer> tiers = new EnumMap<>(BuffId.class);
        double cost = 0.0;
        int top = 0;

        for (Map.Entry<BuffId, Integer> e : loadout.entrySet()) {
            BuffId id = e.getKey();
            if (id == null) continue;
            if (deliverable != null && !deliverable.contains(id)) continue;
            Integer want = e.getValue();
            if (want == null || want <= 0) continue;
            BuffSetting s = config.settingOf(id);
            if (s == null || !s.enabled()) continue;
            int cap = Math.max(0, Math.min(BuffSetting.TIER_CEILING, s.maxTier()));
            int tier = Math.min(want, cap);
            if (tier <= 0) continue;
            tiers.put(id, tier);
            cost += tier * Math.max(0.0, s.costPerSec());
            top = Math.max(top, tier);
        }

        return tiers.isEmpty() ? ResolvedBuffs.NONE : new ResolvedBuffs(Map.copyOf(tiers), cost, top);
    }
}
