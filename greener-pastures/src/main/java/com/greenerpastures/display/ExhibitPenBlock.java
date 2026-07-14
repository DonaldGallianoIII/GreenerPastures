package com.greenerpastures.display;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The <b>Exhibit Pen</b> block (spec §1): right-click with a written specimen disk to add a roaming
 * resident, sneak-right-click empty-handed to take your newest disk back, break it and everything
 * pops. All state + lifecycle lives in {@link ExhibitPenBlockEntity}; this class is routing.
 */
public class ExhibitPenBlock extends BlockWithEntity {

    public static final MapCodec<ExhibitPenBlock> CODEC = createCodec(ExhibitPenBlock::new);

    public ExhibitPenBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;   // BlockWithEntity defaults to INVISIBLE
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ExhibitPenBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer instanceof PlayerEntity player
                && world.getBlockEntity(pos) instanceof ExhibitPenBlockEntity pen) {
            pen.setOwner(player.getUuid());
        }
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!stack.isOf(com.greenerpastures.economy.GpItems.SPECIMEN_DISK)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp
                && world.getBlockEntity(pos) instanceof ExhibitPenBlockEntity pen) {
            pen.tryInsert(sp, stack);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.isSneaking()) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp
                && world.getBlockEntity(pos) instanceof ExhibitPenBlockEntity pen) {
            pen.tryEject(sp);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient
                && world.getBlockEntity(pos) instanceof ExhibitPenBlockEntity pen) {
            pen.onBroken();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;
        return validateTicker(type, DisplaySuite.EXHIBIT_PEN_BE, ExhibitPenBlockEntity::serverTick);
    }
}
