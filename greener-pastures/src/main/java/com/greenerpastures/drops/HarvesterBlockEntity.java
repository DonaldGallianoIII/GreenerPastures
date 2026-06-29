package com.greenerpastures.drops;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.EffectiveAugments;
import com.greenerpastures.pasture.breeding.CobbreedingBridge;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
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

import java.util.Map;
import java.util.Random;

/**
 * The Harvester's block entity — placed touching a pasture, once an IRL minute each tethered mon has a small
 * chance ({@code BASE_PROC} = 3%) to proc a drop EVENT — Cobblemon's own faithful roll ({@link DropsBridge}:
 * amount budget + per-entry %) — and the result is deposited STRAIGHT into its own 9×3 inventory. Never
 * spawns a world item, so nothing despawns and there's no ground-item lag. Opens as a vanilla chest GUI (no
 * custom UI). Materials only — drops never feed Data.
 *
 * <p>Pause-when-full: if the container has no free slot it harvests nothing (no roll, no loss) — empty it
 * to resume. Break scatters the contents (in {@code HarvesterBlock}).
 */
public class HarvesterBlockEntity extends BlockEntity implements NamedScreenHandlerFactory {
    private static final int INTERVAL = 1200;                // a harvest "tick" every IRL minute (1200 ticks)
    private static final double BASE_PROC = 0.03;            // LEVER 1: 3% per mon per minute to proc a drop event
    static final int SLOTS = 27;                             // 9×3, a single chest

    private final SimpleInventory inv = new SimpleInventory(SLOTS);
    private final Random rng = new Random();

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
            EffectiveAugments eff = effectiveAt(sw, pasturePos);          // the pasture Kernel's drop mods
            double proc = BASE_PROC + eff.dropRateFraction();            // LEVER 1: +0.25% kernel base etc.
            int yield = eff.dropYieldBonus();                           // LEVER 2: widen the amount budget
            Map<String, Integer> harvested = DropsBridge.harvest(pasture, be.rng, proc, yield);
            if (harvested.isEmpty()) return;
            int added = be.deposit(harvested);
            if (added > 0) {
                be.markDirty();
                GpLog.d("harvester", "harvest", "pos", pos.toShortString(),
                        "pasture", pasturePos.toShortString(), "items", added);
            }
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

    /** The adjacent pasture's effective drop augments (Kernel base + any compiled Drop Rate / Drop Yield
     *  augment) — one registry lookup feeding both levers. Tether-amplified drop rate/yield (with their Data
     *  drain) is a later step, so no tethers are passed yet (base augments only). */
    private static EffectiveAugments effectiveAt(ServerWorld world, BlockPos pasturePos) {
        PastureData pd = PastureRegistry.get(world.getServer()).get(world, pasturePos);
        return EffectiveAugments.of(pd == null ? java.util.Map.of() : pd.baseAugmentLevels(), java.util.List.of());
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
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        inv.readNbtList(nbt.getList("inv", NbtElement.COMPOUND_TYPE), lookup);
    }
}
