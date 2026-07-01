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
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;

/**
 * The BioBank block — a deposit station for the player's <b>per-player</b> egg bank (INTERACTIVE_SPEC §7.2; the
 * bank is UUID-keyed in {@link BioBankStore}, and the block owns no eggs). Interactions:
 * <ul>
 *   <li>right-click holding egg(s) → bank one into YOUR bank (sneak → bank every egg in your inventory)</li>
 *   <li>right-click empty-handed → print YOUR bank's contents summary in chat</li>
 * </ul>
 * Browse/manage your bank in the Notebook console's BioBank tab. No scatter-on-break: the eggs are yours, not
 * the block's, so breaking the block loses nothing.
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
        if (!(player instanceof ServerPlayerEntity sp)) return ItemActionResult.SUCCESS;
        ServerWorld sw = (ServerWorld) world;

        if (player.isSneaking()) {
            int n = depositAll(sw, sp);
            msg(sp, n > 0 ? ("Banked " + n + " eggs.") : "BioBank is full.");
        } else if (depositOne(sw, sp, stack)) {
            stack.decrement(1);
            msg(sp, "Banked 1 egg.");
        } else {
            msg(sp, "BioBank is full.");
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp) {
            summary(sw, sp);
        }
        return ActionResult.SUCCESS;
    }

    // --- helpers (per-player bank) ---

    private static boolean depositOne(ServerWorld world, ServerPlayerEntity sp, ItemStack egg) {
        if (egg.isEmpty() || !EggReader.isEgg(egg)) return false;
        BioBankStore store = BioBankStore.get(world.getServer());
        BioBankData d = store.bankOf(sp.getUuid());
        String species = EggReader.species(egg);
        if (!d.add(species, egg.copyWithCount(1))) return false;   // bank full (cap enforced inside add())
        store.markDirty();
        GpLog.d("biobank", "deposit", "player", sp.getUuid().toString(), "species", species, "total", d.total());
        return true;
    }

    private static int depositAll(ServerWorld world, ServerPlayerEntity sp) {
        int n = 0;
        DefaultedList<ItemStack> main = sp.getInventory().main;
        for (int i = 0; i < main.size(); i++) {
            ItemStack s = main.get(i);
            if (s.isEmpty() || !EggReader.isEgg(s)) continue;
            while (!s.isEmpty()) {
                if (!depositOne(world, sp, s)) return n;   // full — stop
                s.decrement(1);
                n++;
            }
            main.set(i, ItemStack.EMPTY);
        }
        return n;
    }

    private static void summary(ServerWorld world, ServerPlayerEntity sp) {
        BioBankData d = BioBankStore.get(world.getServer()).get(sp.getUuid());
        if (d == null || d.total() == 0) { msg(sp, "Your BioBank is empty."); return; }
        Map<String, Integer> counts = d.speciesCounts();
        msg(sp, "§aBioBank§r — " + d.total() + " eggs, " + counts.size() + " species (browse in the Notebook):");
        int shown = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (shown++ >= 12) { msg(sp, "  …and " + (counts.size() - 12) + " more species"); break; }
            msg(sp, "  • " + cap(e.getKey()) + " ×" + e.getValue());
        }
        GpLog.i("biobank", "summary", "player", sp.getUuid().toString(), "total", d.total(), "species", counts.size());
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static void msg(ServerPlayerEntity sp, String text) {
        sp.sendMessage(Text.literal(text), false);
    }
}
