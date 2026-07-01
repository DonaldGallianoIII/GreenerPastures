package com.greenerpastures.economy;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.analytics.Event;
import com.greenerpastures.biobank.EggSummary;
import com.greenerpastures.biobank.ValueRule;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.egg.oracle.cull.EggInfo;
import com.greenerpastures.egg.oracle.cull.EggReader;
import com.greenerpastures.pasture.breeding.CobbreedingBridge;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Renderer's block entity — the "eater". Once a second it reads the adjacent pasture's egg tray,
 * culls every NON-keeper egg in place (destroyed, never materialized) and credits the owner's
 * {@link DataStore} balance with {@link RenderValuation}. Keeps + shinies are left in the tray.
 *
 * <p>The touching pasture's base <b>Enrichment</b> augment multiplies the render value; a slotted
 * <b>Enrichment</b> Soul Tether amplifies that further while the pasture OPERATOR's Daemon is fed, draining
 * the operator's Data per render on THIS block's clock (the breeder/Harvester drain their own tethers — a
 * tether is billed exactly once). Starved → the free base multiplier, no drain. The render <i>reward</i> is
 * separate — it always credits the Renderer's own {@code owner}.
 *
 * <p><b>SACRED shiny rule, guarded four ways:</b> (1) nothing runs unless {@link EggReader} can read eggs
 * at all (never cull blind), (2) it skips any egg read as shiny, (3) it skips any egg whose decrypt
 * FAILED — a failed read reports {@code shiny=false}/{@code ivsKnown=false} and can't be trusted, so it's
 * never culled — and (4) the decision ({@link RenderRun#isRendered}) refuses a shiny too. A bred shiny is
 * never destroyed. The whole tick is also wrapped so a Cobblemon API edge can't crash the world tick.
 */
public class RendererBlockEntity extends BlockEntity {
    private static final int INTERVAL = 20;              // cull once a second
    // ⭐ THE balance constant: kept BELOW the per-cycle tether burn (8/16/24 quality) so a trophy pasture's
    // own non-keeper renders can't self-fund its tethers — dedicated FUEL pastures are required
    // (DAEMON_AND_TETHERS.md "the single number is the balance"). Config-tunable; pin exactly in QA.
    static final long BASE_DATA_PER_EGG = 2L;
    private static final ValueRule KEEP = ValueRule.DEFAULT;   // keep shiny OR ≥1 perfect IV
    /** The tether function the Renderer owns — the only tether it amplifies + pays burn for (the breeder
     *  owns shiny/speed, the Harvester drop_rate/drop_yield), so a tether is billed on exactly one clock. */
    private static final Set<AugmentFunction> ENRICH_FUNCTIONS = EnumSet.of(AugmentFunction.ENRICHMENT);

    private UUID owner;

    public RendererBlockEntity(BlockPos pos, BlockState state) {
        super(DarkEconomy.BE, pos, state);
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        markDirty();
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, RendererBlockEntity be) {
        if (be.owner == null) return;                                       // no account to credit → idle
        if (world.getTime() % INTERVAL != 0L) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!CobbreedingBridge.isAvailable() || !EggReader.apiAvailable()) return;   // never cull blind (SACRED #1)
        try {
            BlockPos pasturePos = adjacentPasture(world, pos);
            if (pasturePos == null) return;
            DefaultedList<ItemStack> tray = CobbreedingBridge.eggsAt(pasturePos);
            if (tray == null) return;

            int culled = 0;
            for (int i = 0; i < tray.size(); i++) {
                ItemStack s = tray.get(i);
                if (s.isEmpty() || !EggReader.isEgg(s)) continue;
                EggInfo info = EggReader.read(s);                              // one Cobbreeding decrypt per egg
                // SACRED #2/#3: never cull a shiny, and never cull on a FAILED read — a failed decrypt reads
                // as shiny=false / ivsKnown=false, indistinguishable from a worthless egg, so it MUST be skipped.
                if (info == null || info.shiny() || !info.ivsKnown()) continue;
                // species isn't part of the cull decision (ValueRule keys on shiny/IV only) → skip a 2nd decrypt
                EggSummary egg = new EggSummary("", false, info.ivTotal(), info.perfectCount());
                if (RenderRun.isRendered(egg, KEEP)) {                          // SACRED #4: decision refuses shinies too
                    tray.set(i, ItemStack.EMPTY);                              // culled → destroyed (becomes Data)
                    culled++;
                }
            }
            if (culled == 0) return;

            CobbreedingBridge.refreshHasEgg(world, pasturePos);
            EnrichPlan plan = enrichPlan(sw, pasturePos);                      // base Enrichment × any FED tether
            long data = RenderValuation.dataFor(culled, BASE_DATA_PER_EGG, plan.multiplier());
            DataStore.get(sw.getServer()).credit(be.owner, data);             // the render reward → the Renderer's owner
            if (plan.drain() > 0 && plan.operator() != null) {                // the fed Enrichment tether earned its burn
                DataStore.get(sw.getServer()).tryDebit(plan.operator(), plan.drain());   // …billed to the pasture operator
                GpLog.d("tether", "drain", "pos", pos.toShortString(),
                        "data", plan.drain(), "owner", plan.operator().toString(), "src", "renderer");
            }
            GpLog.d("renderer", "render", "pos", pos.toShortString(), "pasture", pasturePos.toShortString(),
                    "culled", culled, "data", data, "owner", be.owner.toString());   // DEBUG: routine cull, up to 1/sec — silent at INFO
            Analytics.record(world, Event.of("egg_rendered")
                    .put("culled", culled).put("data", data)
                    .put("x", pos.getX()).put("y", pos.getY()).put("z", pos.getZ())
                    .player(be.owner));
        } catch (Throwable t) {
            // a Cobblemon/Cobbreeding API edge must never crash the world tick (twin of the breeder guard)
            GpLog.w("renderer", "skip", "pos", pos.toShortString(), "err", String.valueOf(t));
        }
    }

    /** First neighbour that is a Cobblemon pasture (its block-entity sits at the bottom block). */
    private static BlockPos adjacentPasture(World world, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (world.getBlockEntity(n) instanceof PokemonPastureBlockEntity) return n;
        }
        return null;
    }

    /** The render-Data multiplier (≥1) + the Data to burn for the touching pasture this render: the base
     *  Enrichment augment × any FED Enrichment tether, resolved against the pasture OPERATOR's Data on the
     *  Renderer's OWN clock. Drains only enrichment tethers (the breeder/Harvester drain theirs), billed to
     *  the operator — the box-clicker who pays all this pasture's tether cost (twin of the breeder/harvester
     *  owner). No operator, or a starved account → the free base multiplier, no drain. */
    private static EnrichPlan enrichPlan(ServerWorld world, BlockPos pasturePos) {
        PastureData pd = PastureRegistry.get(world.getServer()).get(world, pasturePos);
        if (pd == null) return new EnrichPlan(1.0, 0L, null);
        long balance = (pd.owner != null) ? DataStore.get(world.getServer()).balanceOf(pd.owner) : 0L;
        TetherRuntime.Resolution res =
                TetherRuntime.resolveFor(pd.baseAugmentLevels(), pd.slottedTethers(), balance, ENRICH_FUNCTIONS);
        return new EnrichPlan(res.effective().enrichmentMultiplier(), res.drain(), pd.owner);
    }

    /** What to value this render with: the (possibly tether-amplified) Enrichment multiplier, the Data to
     *  debit, and whose account ({@code operator}) pays it. {@code drain == 0} / {@code operator == null}
     *  ⇒ the free base multiplier. */
    private record EnrichPlan(double multiplier, long drain, UUID operator) {}

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        if (owner != null) nbt.putUuid("owner", owner);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        if (nbt.containsUuid("owner")) owner = nbt.getUuid("owner");
    }
}
