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
 * Rituals on the network tick (NOTEBOOK_BUILD_PLAN 3b - the re-wire the block-free consolidation dropped):
 * runs the config-driven custom-drop layers over an OWNED pasture's harvest sweep, on the same clock as the
 * staple drops. Two tiers, both pure-core-tested ({@code ritual/}):
 *
 * <ul>
 *   <li><b>Type-drops</b> - every sweep, each tethered mon rolls its matching type entries (an Ice-type
 *       trickles ice, a Fire-type blaze rods - the "no vanilla mobs, farm typed mons instead" economy).</li>
 *   <li><b>Gacha rituals</b> - a pasture whose COMPOSITION satisfies a ritual's requirement banks one pull
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
     *
     * <p>Returns the TYPE-DROPS ({@code item id → count}) for the normal Harvester deposit. GACHA ritual
     * outputs deliberately do NOT flow through the return - they land in the owner's {@link RitualLedger}
     * loot pool (the Rituals tab is the reward chest, Deuce's v2 spec), and a first-ever satisfied
     * composition LEARNS the ritual: the hidden recipe reveals, with an Inbox note + a once-ever chat pop.
     */
    public static Map<String, Integer> roll(net.minecraft.server.MinecraftServer server, java.util.UUID owner,
                                            String pastureName, CompositionReader.PastureMons mons,
                                            PastureData pd, Random rng, int sweeps, long posKey) {
        Map<String, Integer> out = new LinkedHashMap<>();
        RitualConfig cfg = RitualSystem.config();
        if (!cfg.enabled() || mons == null || sweeps <= 0) return out;

        // Tier 1 - type-drops: per sweep, per mon, per matching type entry.
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

        // Tier 2 - gacha rituals (HIDDEN recipes): bank one pull per sweep while the composition holds,
        // auto-roll with pity, and pay hits into the owner's RITUAL loot pool (never Harvester storage).
        com.greenerpastures.ritual.RitualLedger ledger =
                server == null ? null : com.greenerpastures.ritual.RitualLedger.get(server);
        Composition comp = mons.aggregate();
        for (Ritual r : cfg.activeRituals().active(comp)) {
            if (ledger != null && owner != null && ledger.learn(owner, r.id())) {
                // First-ever assembly - the hidden-achievement pop. Once per player per ritual, forever.
                com.greenerpastures.notify.Inbox.push(owner, "🗡",
                        "RITUAL DISCOVERED - " + r.name() + " · the recipe is recorded in your Rituals tab");
                var online = server.getPlayerManager().getPlayer(owner);
                if (online != null) {
                    online.sendMessage(net.minecraft.text.Text.literal(
                            "§6✦ Ritual discovered: §e" + r.name() + "§6 ✦§r - its recipe is now in your Notebook."), false);
                }
                GpLog.i("ritual", "discovered", "ritual", r.id(), "owner", owner.toString(),
                        "pasture", pastureName == null ? "?" : pastureName);
            }
            int[] st = pd.ritualState.computeIfAbsent(r.id(), k -> new int[]{0, 0});
            Gacha.PullState state = new Gacha.PullState(st[0], st[1]).bank(sweeps);
            int bankedBeforeRoll = state.bankedPulls();
            int hits = 0;
            {   // autoPull=false is IGNORED until the manual-pull screen ships (review: the knob silently
                // killed all ritual output - banked pulls had no spend path). Warned once at config load.
                Gacha.Session session = Gacha.pullAll(state, r.baseChancePercent(), r.hardPity(), r.softPityStart(), rng::nextDouble);
                hits = session.hits();
                state = session.state();
            }
            st[0] = state.bankedPulls();
            st[1] = state.pity();
            int rolled = bankedBeforeRoll - state.bankedPulls();   // lifetime pulls counter (Rituals-tab QoL: proof the gacha is alive)
            if (rolled > 0 && ledger != null && owner != null) ledger.addPulls(owner, r.id(), rolled);
            if (hits > 0) {
                if (ledger != null && owner != null) {
                    String lastItem = payHits(ledger, owner, r, hits, rng);
                    ledger.addHits(owner, r.id(), hits);
                    com.greenerpastures.notify.Inbox.push(owner, "🗡", r.outputPool().isEmpty()
                            ? r.name() + " granted " + (r.outputQty() * hits) + "× " + lastItem.replace("minecraft:", "")
                            : r.name() + " played " + hits + " request" + (hits == 1 ? "" : "s") + " - check Ritual spoils");
                    GpLog.i("ritual", "hit", "ritual", r.id(), "hits", hits,
                            "item", lastItem, "qty", r.outputQty() * hits, "pity", st[1]);
                }
            } else if (GpLog.on(GpLog.Level.DEBUG)) {
                GpLog.d("ritual", "pulls", "ritual", r.id(), "banked", st[0], "pity", st[1], "sweeps", sweeps);
            }
        }

        // Tier 3 - SPANNING rituals (v3, Deuce 2026-07-04): the requirement evaluates against the UNION of
        // this pasture + any OTHER pasture of the same owner (Professor's Summit: 27 starters > 16 slots, so
        // one pasture can never do it alone). Compositions come only from REAL sweeps (a snapshot per swept
        // pasture, ≤5 min fresh - never guess an unloaded roster); pity/banked live on the PLAYER ledger (a
        // pair has no single home pasture); a satisfied pair banks only on the SMALLER pos's sweep so the two
        // pastures never double-bank the same ritual.
        if (owner != null && ledger != null) {
            Map<Long, long[]> mine = snapshotsTime.computeIfAbsent(owner, k -> new java.util.concurrent.ConcurrentHashMap<>());
            Map<Long, Composition> comps = snapshots.computeIfAbsent(owner, k -> new java.util.concurrent.ConcurrentHashMap<>());
            long nowMs = System.currentTimeMillis();
            comps.put(posKey, comp);
            mine.put(posKey, new long[]{nowMs});
            for (Ritual r : cfg.activeRituals().spanning()) {
                java.util.List<Long> partners = new java.util.ArrayList<>();
                for (var it = comps.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Long, Composition> other = it.next();
                    if (other.getKey() == posKey) continue;
                    long[] ts = mine.get(other.getKey());
                    if (ts == null || nowMs - ts[0] > SNAPSHOT_FRESH_MS) {   // stale (unloaded/destroyed) → not a partner
                        it.remove();                                          // and prune the dead key (review minor: map grew forever)
                        mine.remove(other.getKey());
                        continue;
                    }
                    if (r.requirement().satisfiedBy(Composition.union(comp, other.getValue()))) partners.add(other.getKey());
                }
                if (partners.isEmpty()) continue;
                if (ledger.learn(owner, r.id())) {
                    com.greenerpastures.notify.Inbox.push(owner, "🗡",
                            "RITUAL DISCOVERED - " + r.name() + " · the recipe is recorded in your Rituals tab");
                    var online = server.getPlayerManager().getPlayer(owner);
                    if (online != null) {
                        online.sendMessage(net.minecraft.text.Text.literal(
                                "§6✦ Ritual discovered: §e" + r.name() + "§6 ✦§r - its recipe is now in your Notebook."), false);
                    }
                    GpLog.i("ritual", "discovered", "ritual", r.id(), "owner", owner.toString(),
                            "pasture", pastureName == null ? "?" : pastureName);
                }
                if (!com.greenerpastures.ritual.SpanGate.shouldBank(posKey, partners)) continue;   // the partner's sweep banks
                int[] st = ledger.spanStateOf(owner, r.id());
                Gacha.PullState state = new Gacha.PullState(st[0], st[1]).bank(sweeps);
                int bankedBeforeRoll = state.bankedPulls();
                int hits = 0;
                {   // autoPull=false ignored - see the tier-2 note
                    Gacha.Session session = Gacha.pullAll(state, r.baseChancePercent(), r.hardPity(), r.softPityStart(), rng::nextDouble);
                    hits = session.hits();
                    state = session.state();
                }
                st[0] = state.bankedPulls();
                st[1] = state.pity();
                ledger.markSpanDirty();
                int rolled = bankedBeforeRoll - state.bankedPulls();
                if (rolled > 0) ledger.addPulls(owner, r.id(), rolled);
                if (hits > 0) {
                    String lastItem = payHits(ledger, owner, r, hits, rng);
                    ledger.addHits(owner, r.id(), hits);
                    com.greenerpastures.notify.Inbox.push(owner, "🗡",
                            r.name() + " granted " + (r.outputQty() * hits) + "× " + lastItem.replace("minecraft:", "").replace("cobblemon:", ""));
                    GpLog.i("ritual", "hit", "ritual", r.id(), "hits", hits,
                            "item", lastItem, "qty", r.outputQty() * hits, "pity", st[1]);
                } else if (GpLog.on(GpLog.Level.DEBUG)) {
                    GpLog.d("ritual", "pulls", "ritual", r.id(), "banked", st[0], "pity", st[1], "sweeps", sweeps);
                }
            }
        }
        return out;
    }

    /** Pay {@code hits} into the loot pool - each hit rolls the ritual's output INDEPENDENTLY (the Music
     *  Disc pool gives a different disc per hit; fixed-output rituals behave exactly as before). Returns the
     *  last item paid, for the Inbox line. */
    private static String payHits(com.greenerpastures.ritual.RitualLedger ledger, java.util.UUID owner,
                                  Ritual r, int hits, Random rng) {
        String last = r.outputItem();
        for (int h = 0; h < hits; h++) {
            last = r.rollOutput(rng::nextDouble);
            ledger.addLoot(owner, last, r.outputQty());
        }
        return last;
    }

    /** Owner → (pasture pos → last-swept composition). Snapshots exist ONLY from real sweeps and expire in
     *  {@link #SNAPSHOT_FRESH_MS} - an unloaded / destroyed / unlinked pasture silently drops out of pairing. */
    private static final Map<java.util.UUID, Map<Long, Composition>> snapshots = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<java.util.UUID, Map<Long, long[]>> snapshotsTime = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long SNAPSHOT_FRESH_MS = 5 * 60_000L;

    /** SP-statics hygiene: one JVM hosts many worlds - clear on SERVER_STARTED like every session store. */
    public static void resetSession() {
        snapshots.clear();
        snapshotsTime.clear();
    }
}
