package com.greenerpastures.drops;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Harvester block — placed touching a pasture, it passively collects each tethered mon's drops into
 * its own chest inventory (see {@link HarvesterBlockEntity}). Right-click to open it (vanilla 9×3 GUI);
 * break scatters the collected materials so nothing is lost.
 */
public class HarvesterBlock extends BlockWithEntity {
    public static final MapCodec<HarvesterBlock> CODEC = createCodec(HarvesterBlock::new);

    public HarvesterBlock(Settings settings) {
        super(settings);
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new HarvesterBlockEntity(pos, state);
    }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof HarvesterBlockEntity be
                && player instanceof ServerPlayerEntity sp) {
            sp.openHandledScreen(be);   // vanilla chest GUI — no custom screen
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;   // the harvest is server-authoritative
        return validateTicker(type, Harvester.BE, HarvesterBlockEntity::serverTick);
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof HarvesterBlockEntity be) {
                ItemScatterer.spawn(world, pos, be.inventory());   // never lose collected materials on break
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
}
