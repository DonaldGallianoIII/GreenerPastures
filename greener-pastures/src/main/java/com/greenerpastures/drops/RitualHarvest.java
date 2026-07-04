package com.greenerpastures.drops;

import com.greenerpastures.core.GpLog;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.ritual.Composition;
import com.greenerpastures.ritual.Gacha;
import com.greenerpastures.ritual.Ritual;
import com.greenerpastures.ritual.RitualConfig;
import com.greenerpastures.ritual.RitualSystem;
import com.greenerpastures.ritual.TypeDrop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Rituals on the network tick (NOTEBOOK_BUILD_PLAN 3b — the re-wire the block-free consolidation dropped):
 * runs the config-driven custom-drop layers over an OWNED pasture's harvest sweep, on the same clock as the
 * staple drops. Two tiers, both pure-core-tested ({@code ritual/}):
 *
 * <ul>
 *   <li><b>Type-drops</b> — every sweep, each tethered mon rolls its matching type entries (an Ice-type
 *       trickles ice, a Fire-type blaze rods — the "no vanilla mobs, farm typed mons instead" economy).</li>
 *   <li><b>Gacha rituals</b> — a pasture whose COMPOSITION satisfies a ritual's requirement banks one pull
 *       per sweep; banked pulls roll with soft/hard pity ({@link Gacha}). Pull state (banked + pity) persists
 *       per pasture per ritual on {@link PastureData#ritualState}, so pity survives restarts and catch-up
 *       banks exactly the missed sweeps.</li>
 * </ul>
 */
public final class RitualHarvest {
    private RitualHarvest() {}

    /**
     * Roll {@code sweeps} worth of type-drops + ritual pulls for one pasture. The composition is read once
     * by the caller (it can't change while a chunk is unloaded, so it's exact across a catch-up).
     * Returns {@code item id → count} to merge into the sweep's deposit; mutates {@code pd.ritualState}.
     */
    public static Map<String, Integer> roll(CompositionReader.PastureMons mons, PastureData pd, Random rng, int sweeps) {
        Map<String, Integer> out = new LinkedHashMap<>();
        RitualConfig cfg = RitualSystem.config();
        if (!cfg.enabled() || mons == null || sweeps <= 0) return out;

        // Tier 1 — type-drops: per sweep, per mon, per matching type entry.
        var table = cfg.activeTypeDrops();
        if (table.enabled() && !table.drops().isEmpty()) {
            for (Set<String> types : mons.perMonTypes()) {
                if (types.isEmpty()) continue;
                for (TypeDrop td : table.drops()) {
                    if (!types.contains(td.type()) || td.chancePercent() <= 0) continue;
                    for (int s = 0; s < sweeps; s++) {
                        if (rng.nextDouble() * 100.0 >= td.chancePercent()) continue;
                        int qty = td.minQty() + (td.maxQty() > td.minQty() ? rng.nextInt(td.maxQty() - td.minQty() + 1) : 0);
                        if (qty > 0) out.merge(td.item(), qty, Integer::sum);
                    }
                }
            }
        }

        // Tier 2 — gacha rituals: bank one pull per sweep while the composition holds, then auto-roll with pity.
        Composition comp = mons.aggregate();
        for (Ritual r : cfg.activeRituals().active(comp)) {
            int[] st = pd.ritualState.computeIfAbsent(r.id(), k -> new int[]{0, 0});
            Gacha.PullState state = new Gacha.PullState(st[0], st[1]).bank(sweeps);
            int hits = 0;
            if (cfg.autoPull()) {
                Gacha.Session session = Gacha.pullAll(state, r.baseChancePercent(), r.hardPity(), r.softPityStart(), rng::nextDouble);
                hits = session.hits();
                state = session.state();
            }
            st[0] = state.bankedPulls();
            st[1] = state.pity();
            if (hits > 0) {
                out.merge(r.outputItem(), r.outputQty() * hits, Integer::sum);
                GpLog.i("ritual", "hit", "ritual", r.id(), "hits", hits,
                        "item", r.outputItem(), "qty", r.outputQty() * hits, "pity", st[1]);
            } else if (GpLog.on(GpLog.Level.DEBUG)) {
                GpLog.d("ritual", "pulls", "ritual", r.id(), "banked", st[0], "pity", st[1], "sweeps", sweeps);
            }
        }
        return out;
    }
}
