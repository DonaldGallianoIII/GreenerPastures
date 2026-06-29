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

import java.util.UUID;

/**
 * The Renderer's block entity — the "eater". Once a second it reads the adjacent pasture's egg tray,
 * culls every NON-keeper egg in place (destroyed, never materialized) and credits the owner's
 * {@link DataStore} balance with {@link RenderValuation}. Keeps + shinies are left in the tray.
 *
 * <p><b>SACRED shiny rule, triple-guarded:</b> (1) it does nothing unless {@link EggReader} can actually
 * read eggs (never cull blind), (2) it skips any egg read as shiny before deciding, and (3) the decision
 * itself ({@link RenderRun#isRendered}) refuses to render a shiny. A bred shiny is never destroyed.
 */
public class RendererBlockEntity extends BlockEntity {
    private static final int INTERVAL = 20;              // cull once a second
    static final long BASE_DATA_PER_EGG = 10L;           // balance constant (placeholder; tune vs tether burn)
    private static final ValueRule KEEP = ValueRule.DEFAULT;   // keep shiny OR ≥1 perfect IV

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

        BlockPos pasturePos = adjacentPasture(world, pos);
        if (pasturePos == null) return;
        DefaultedList<ItemStack> tray = CobbreedingBridge.eggsAt(pasturePos);
        if (tray == null) return;

        int culled = 0;
        for (int i = 0; i < tray.size(); i++) {
            ItemStack s = tray.get(i);
            if (s.isEmpty() || !EggReader.isEgg(s)) continue;
            EggInfo info = EggReader.read(s);
            if (info == null || info.shiny()) continue;                     // SACRED #2: never touch a shiny
            EggSummary egg = new EggSummary(EggReader.species(s), false, info.ivTotal(), info.perfectCount());
            if (RenderRun.isRendered(egg, KEEP)) {                          // SACRED #3: decision refuses shinies too
                tray.set(i, ItemStack.EMPTY);                              // culled → destroyed (becomes Data)
                culled++;
            }
        }
        if (culled == 0) return;

        CobbreedingBridge.refreshHasEgg(world, pasturePos);
        long data = RenderValuation.dataFor(culled, BASE_DATA_PER_EGG, 1.0);
        DataStore.get(sw.getServer()).credit(be.owner, data);
        GpLog.i("renderer", "render", "pos", pos.toShortString(), "pasture", pasturePos.toShortString(),
                "culled", culled, "data", data, "owner", be.owner.toString());
        Analytics.record(world, Event.of("egg_rendered")
                .put("culled", culled).put("data", data)
                .put("x", pos.getX()).put("y", pos.getY()).put("z", pos.getZ())
                .player(be.owner));
    }

    /** First neighbour that is a Cobblemon pasture (its block-entity sits at the bottom block). */
    private static BlockPos adjacentPasture(World world, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (world.getBlockEntity(n) instanceof PokemonPastureBlockEntity) return n;
        }
        return null;
    }

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
