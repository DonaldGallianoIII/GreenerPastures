package com.greenerpastures.drops;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.AugmentFunction;
import com.greenerpastures.economy.DataStore;
import com.greenerpastures.economy.EffectiveAugments;
import com.greenerpastures.economy.TetherRuntime;
import com.greenerpastures.pasture.breeding.CobbreedingBridge;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import com.greenerpastures.ritual.Gacha;
import com.greenerpastures.ritual.Ritual;
import com.greenerpastures.ritual.RitualConfig;
import com.greenerpastures.ritual.RitualSystem;
import com.greenerpastures.ritual.TypeDrop;
import com.greenerpastures.ritual.TypeDropTable;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The Harvester's block entity — placed touching a pasture, once an IRL minute each tethered mon has a small
 * chance ({@code BASE_PROC} = 3%) to proc a drop EVENT — Cobblemon's own faithful roll ({@link DropsBridge}:
 * amount budget + per-entry %) — and the result is deposited STRAIGHT into its own 9×3 inventory. Never
 * spawns a world item, so nothing despawns and there's no ground-item lag. Opens as a vanilla chest GUI (no
 * custom UI). Materials only — drops never feed Data.
 *
 * <p>The Kernel's Drop Rate / Drop Yield augments feed the two levers; a matching Soul Tether amplifies
 * them while the operator's Daemon is fed, draining Data on THIS block's clock (the breeder/Renderer drain
 * their own tethers — never double-charged). Starved → the free base rate, never a pause. The harvested
 * items stay materials-only: they never feed Data, they only ever cost it (the tether burn).
 *
 * <p><b>Custom drops</b> (config-driven — {@code config/greenerpastures/rituals.json}): on top of the
 * Cobblemon-table harvest, a mon's elemental TYPE can add staple drops (Tier 1) and the pasture's
 * COMPOSITION banks gacha pulls toward rare/boss items with pity (Tier 2 — see {@code RITUALS.md}). Fully
 * admin-toggleable; still materials only, never Data.
 *
 * <p>Pause-when-full: if the container has no free slot it harvests nothing (no roll, no loss) — empty it
 * to resume. Break scatters the contents (in {@code HarvesterBlock}).
 */
public class HarvesterBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    private static final int INTERVAL = 1200;                // a harvest "tick" every IRL minute (1200 ticks)
    private static final double BASE_PROC = 0.03;            // LEVER 1: 3% per mon per minute to proc a drop event
    static final int SLOTS = 27;                             // 9×3, a single chest

    /** The tether functions the Harvester owns — the only tethers it amplifies + pays burn for (the breeder
     *  owns shiny/speed, the Renderer enrichment), so a tether is billed on exactly one clock. */
    private static final Set<AugmentFunction> DROP_FUNCTIONS =
            EnumSet.of(AugmentFunction.DROP_RATE, AugmentFunction.DROP_YIELD);

    private final SimpleInventory inv = new SimpleInventory(SLOTS);
    private final Random rng = new Random();
    /** Per-ritual gacha progress (id → banked pulls + pity), persisted with the block. */
    private final Map<String, Gacha.PullState> ritualPulls = new HashMap<>();

    public HarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(Harvester.BE, pos, state);
        inv.addListener(sender -> markDirty());             // persist player withdrawals via the GUI too
    }

    public SimpleInventory inventory() { return inv; }

    public static void serverTick(World world, BlockPos pos, BlockState state, HarvesterBlockEntity be) {
        if (world.getTime() % INTERVAL != 0L) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!CobbreedingBridge.isAvailable()) return;
        if (firstEmpty(be.inv) < 0) return;                  // full → pause (no roll, no loss, no ground items)
        try {
            BlockPos pasturePos = adjacentPasture(world, pos);
            if (pasturePos == null) return;
            if (!(world.getBlockEntity(pasturePos) instanceof PokemonPastureBlockEntity pasture)) return;
            // 1) staple harvest — each mon's Cobblemon drop table (LEVER 1 proc + LEVER 2 yield)
            DropPlan plan = dropPlan(sw, pasturePos);                     // Kernel base × any FED drop tether
            double proc = BASE_PROC + plan.eff().dropRateFraction();      // LEVER 1: +0.25% base + amplified rate
            int yield = plan.eff().dropYieldBonus();                      // LEVER 2: amplified amount budget
            Map<String, Integer> harvested = DropsBridge.harvest(pasture, be.rng, proc, yield);
            if (!harvested.isEmpty()) {
                int added = be.deposit(harvested);
                if (added > 0) {
                    be.markDirty();
                    GpLog.d("harvester", "harvest", "pos", pos.toShortString(),
                            "pasture", pasturePos.toShortString(), "items", added);
                    if (plan.drain() > 0 && plan.owner() != null) {      // the fed drop tethers earned their burn
                        DataStore.get(sw.getServer()).tryDebit(plan.owner(), plan.drain());
                        GpLog.d("tether", "drain", "pos", pos.toShortString(),
                                "data", plan.drain(), "owner", plan.owner().toString(), "src", "harvester");
                    }
                }
            }
            // 2) custom drops — config type-drops (by mon type) + gacha rituals (by composition); runs even
            //    if the staple roll was empty, since rituals bank pulls on their own clock
            be.customDrops(pasture, pos);
        } catch (Throwable t) {
            // a Cobblemon API edge must never crash the world tick (mirrors the breeder/Renderer guards)
            GpLog.w("harvester", "skip", "pos", pos.toShortString(), "err", String.valueOf(t));
        }
    }

    /** Insert rolled drops (item id → count) into the inventory; returns the count actually stored.
     *  Overflow is discarded (logged), never spawned as a world item — the whole point is no ground loot. */
    private int deposit(Map<String, Integer> drops) {
        int stored = 0;
        for (Map.Entry<String, Integer> d : drops.entrySet()) {
            Identifier rid = Identifier.tryParse(d.getKey());
            if (rid == null) continue;
            Item item = Registries.ITEM.get(rid);
            if (item == Items.AIR) continue;
            int maxStack = new ItemStack(item).getMaxCount();
            int remaining = d.getValue();
            while (remaining > 0) {
                int n = Math.min(remaining, maxStack);
                ItemStack left = insert(new ItemStack(item, n));
                stored += n - left.getCount();
                remaining -= n;
                if (!left.isEmpty()) {                       // container filled mid-item
                    GpLog.d("harvester", "overflow", "pos", pos.toShortString(),
                            "item", d.getKey(), "lost", remaining + left.getCount());
                    break;
                }
            }
        }
        return stored;
    }

    /** Merge into matching stacks then fill empty slots; returns the remainder (empty if it all fit). */
    private ItemStack insert(ItemStack stack) {
        for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
            ItemStack slot = inv.getStack(i);
            if (!slot.isEmpty() && ItemStack.areItemsAndComponentsEqual(slot, stack)) {
                int moved = Math.min(slot.getMaxCount() - slot.getCount(), stack.getCount());
                if (moved > 0) { slot.increment(moved); stack.decrement(moved); }
            }
        }
        for (int i = 0; i < inv.size() && !stack.isEmpty(); i++) {
            if (inv.getStack(i).isEmpty()) { inv.setStack(i, stack.copy()); stack.setCount(0); }
        }
        return stack;
    }

    private static int firstEmpty(SimpleInventory inv) {
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isEmpty()) return i;
        return -1;
    }

    private static BlockPos adjacentPasture(World world, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (world.getBlockEntity(n) instanceof PokemonPastureBlockEntity) return n;
        }
        return null;
    }

    /** The amplified drop augments + the Data to burn for the adjacent pasture this harvest tick: the Kernel's
     *  base drop mods × any FED Drop Rate / Drop Yield tether, resolved against the operator's Data on the
     *  Harvester's OWN clock (one registry lookup feeds both levers). Drains only drop tethers — the breeder
     *  and Renderer drain theirs — so nothing is double-charged. No operator, or a starved account → free base,
     *  no drain (harvesting never pauses for Data; hunger only drops you to the base rate). */
    private static DropPlan dropPlan(ServerWorld world, BlockPos pasturePos) {
        PastureData pd = PastureRegistry.get(world.getServer()).get(world, pasturePos);
        if (pd == null) return new DropPlan(EffectiveAugments.of(Map.of(), List.of()), 0L, null);
        long balance = (pd.owner != null) ? DataStore.get(world.getServer()).balanceOf(pd.owner) : 0L;
        TetherRuntime.Resolution res =
                TetherRuntime.resolveFor(pd.baseAugmentLevels(), pd.slottedTethers(), balance, DROP_FUNCTIONS);
        return new DropPlan(res.effective(), res.drain(), pd.owner);
    }

    /** What to harvest with this tick: the (possibly tether-amplified) drop augments, the Data to debit, and
     *  whose account ({@code owner}) pays it. {@code drain == 0} / {@code owner == null} ⇒ run the free base. */
    private record DropPlan(EffectiveAugments eff, long drain, UUID owner) {}

    /**
     * The custom-drop pass (config-driven): Tier-1 <b>type-drops</b> (each mon rolls the drops matching one of
     * its elemental types) + Tier-2 <b>rituals</b> (the pasture composition banks gacha pulls toward rare
     * items). Materials only — never Data. Banks one pull per active ritual; with {@code autoPull} on (the
     * interim until the manual gacha GUI) it rolls them immediately, depositing any hit. Pity / banked-pull
     * progress persists per ritual. Whole thing is gated by the master / per-tier / per-ritual config toggles.
     */
    private void customDrops(PokemonPastureBlockEntity pasture, BlockPos pos) {
        RitualConfig cfg = RitualSystem.config();
        if (!cfg.enabled()) return;
        CompositionReader.PastureMons mons = CompositionReader.read(pasture);
        Map<String, Integer> out = new LinkedHashMap<>();

        // Tier 1 — type-drops: per mon, each config drop whose type the mon has gets a roll.
        TypeDropTable tdt = cfg.activeTypeDrops();
        if (tdt.enabled()) {
            for (Set<String> types : mons.perMonTypes()) {
                for (TypeDrop d : tdt.forTypes(types)) {
                    if (rng.nextDouble() * 100.0 < d.chancePercent()) {
                        int span = d.maxQty() - d.minQty();
                        int qty = d.minQty() + (span > 0 ? rng.nextInt(span + 1) : 0);
                        if (qty > 0) out.merge(d.item(), qty, Integer::sum);
                    }
                }
            }
        }

        // Tier 2 — rituals: bank +1 pull per satisfied ritual; auto-pull rolls it now (else it waits for the GUI).
        boolean stateChanged = false;
        for (Ritual r : cfg.activeRituals().active(mons.aggregate())) {
            Gacha.PullState st = ritualPulls.getOrDefault(r.id(), Gacha.PullState.EMPTY).bank(1);
            if (cfg.autoPull()) {
                Gacha.Session sess = Gacha.pullAll(st, r.baseChancePercent(), r.hardPity(), r.softPityStart(), rng::nextDouble);
                if (sess.hits() > 0) {
                    out.merge(r.outputItem(), r.outputQty() * sess.hits(), Integer::sum);
                    GpLog.i("ritual", "hit", "pos", pos.toShortString(), "ritual", r.id(),
                            "item", r.outputItem(), "n", sess.hits());
                }
                st = sess.state();
            }
            ritualPulls.put(r.id(), st);
            stateChanged = true;
        }

        if (!out.isEmpty()) {
            int stored = deposit(out);
            if (stored > 0) GpLog.d("ritual", "drops", "pos", pos.toShortString(), "items", stored);
        }
        if (stateChanged || !out.isEmpty()) markDirty();
    }

    // --- vanilla 9×3 chest GUI (no custom screen) ---
    @Override public Text getDisplayName() { return Text.translatable("block.greenerpastures.harvester"); }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, inv);
    }

    // --- persistence ---
    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.put("inv", inv.toNbtList(lookup));
        if (!ritualPulls.isEmpty()) {
            NbtCompound rp = new NbtCompound();
            ritualPulls.forEach((id, st) -> {
                NbtCompound e = new NbtCompound();
                e.putInt("banked", st.bankedPulls());
                e.putInt("pity", st.pity());
                rp.put(id, e);
            });
            nbt.put("ritualPulls", rp);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        inv.readNbtList(nbt.getList("inv", NbtElement.COMPOUND_TYPE), lookup);
        ritualPulls.clear();
        NbtCompound rp = nbt.getCompound("ritualPulls");
        for (String id : rp.getKeys()) {
            NbtCompound e = rp.getCompound(id);
            ritualPulls.put(id, new Gacha.PullState(e.getInt("banked"), e.getInt("pity")));
        }
    }
}
