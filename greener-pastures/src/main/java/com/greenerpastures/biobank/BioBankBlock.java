package com.greenerpastures.biobank;

import com.greenerpastures.core.GpLog;
import com.greenerpastures.egg.oracle.cull.EggReader;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;

/**
 * The BioBank block — AE2 ME-style egg storage (eggs as data, bucketed by species).
 *
 * <p>Batch 1 interactions (no GUI yet — that's Batch 2):
 * <ul>
 *   <li>right-click holding egg(s) → bank one (sneak → bank every egg in your inventory)</li>
 *   <li>right-click empty-handed → print a contents summary in chat</li>
 *   <li>break → all stored eggs scatter back out (nothing is lost)</li>
 * </ul>
 * Contents live in {@link BioBankStore} (per-world save), keyed by this block's position.
 */
public class BioBankBlock extends BlockWithEntity {
    public static final MapCodec<BioBankBlock> CODEC = createCodec(BioBankBlock::new);

    public BioBankBlock(Settings settings) {
        super(settings);
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BioBankBlockEntity(pos, state);
    }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!EggReader.isEgg(stack)) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        ServerWorld sw = (ServerWorld) world;
        ServerPlayerEntity sp = player instanceof ServerPlayerEntity s ? s : null;

        if (player.isSneaking()) {
            int n = (sp != null) ? depositAll(sw, pos, sp) : 0;
            if (sp != null) msg(sp, n > 0 ? ("Banked " + n + " eggs.") : "BioBank is full.");
        } else if (depositOne(sw, pos, stack)) {
            stack.decrement(1);
            if (sp != null) msg(sp, "Banked 1 egg.");
        } else if (sp != null) {
            msg(sp, "BioBank is full.");
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
            summary(sw, pos, sp);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world instanceof ServerWorld sw) {
                BioBankStore store = BioBankStore.get(sw.getServer());
                BioBankData d = store.get(sw, pos);
                if (d != null && d.total() > 0) {
                    int n = 0;
                    for (ItemStack egg : d.all()) {
                        ItemScatterer.spawn(world, pos.getX(), pos.getY(), pos.getZ(), egg);
                        n++;
                    }
                    GpLog.i("biobank", "break_scatter", "pos", pos.toShortString(), "scattered", n);
                }
                store.remove(sw, pos);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    // --- helpers ---

    private static boolean depositOne(ServerWorld world, BlockPos pos, ItemStack egg) {
        if (egg.isEmpty() || !EggReader.isEgg(egg)) return false;
        BioBankStore store = BioBankStore.get(world.getServer());
        BioBankData d = store.getOrCreate(world, pos);
        String species = EggReader.species(egg);
        if (!d.add(species, egg.copyWithCount(1))) return false;   // bank full (cap enforced inside add())
        store.markDirty();
        GpLog.d("biobank", "deposit", "pos", pos.toShortString(), "species", species, "total", d.total());
        return true;
    }

    private static int depositAll(ServerWorld world, BlockPos pos, ServerPlayerEntity sp) {
        int n = 0;
        DefaultedList<ItemStack> main = sp.getInventory().main;
        for (int i = 0; i < main.size(); i++) {
            ItemStack s = main.get(i);
            if (s.isEmpty() || !EggReader.isEgg(s)) continue;
            while (!s.isEmpty()) {
                if (!depositOne(world, pos, s)) return n;   // full — stop
                s.decrement(1);
                n++;
            }
            main.set(i, ItemStack.EMPTY);
        }
        return n;
    }

    private static void summary(ServerWorld world, BlockPos pos, ServerPlayerEntity sp) {
        BioBankData d = BioBankStore.get(world.getServer()).get(world, pos);
        if (d == null || d.total() == 0) { msg(sp, "BioBank is empty."); return; }
        Map<String, Integer> counts = d.speciesCounts();
        msg(sp, "§aBioBank§r — " + d.total() + " eggs, " + counts.size() + " species:");
        int shown = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (shown++ >= 12) { msg(sp, "  …and " + (counts.size() - 12) + " more species"); break; }
            msg(sp, "  • " + cap(e.getKey()) + " ×" + e.getValue());
        }
        GpLog.i("biobank", "summary", "pos", pos.toShortString(), "total", d.total(), "species", counts.size());
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void msg(ServerPlayerEntity sp, String text) {
        sp.sendMessage(Text.literal(text), false);
    }
}
