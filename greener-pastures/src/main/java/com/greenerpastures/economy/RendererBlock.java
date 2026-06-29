package com.greenerpastures.economy;

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
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Renderer block — placed next to a pasture, it culls non-keeper eggs in-step into Data (see
 * {@link RendererBlockEntity}). It records the placer as owner so the Data it earns goes to the right
 * player's {@link DataStore} account.
 */
public class RendererBlock extends BlockWithEntity {
    public static final MapCodec<RendererBlock> CODEC = createCodec(RendererBlock::new);

    public RendererBlock(Settings settings) {
        super(settings);
    }

    @Override protected MapCodec<? extends BlockWithEntity> getCodec() { return CODEC; }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RendererBlockEntity(pos, state);
    }

    @Override protected BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer instanceof PlayerEntity p
                && world.getBlockEntity(pos) instanceof RendererBlockEntity be) {
            be.setOwner(p.getUuid());
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) return null;   // the cull is server-authoritative
        return validateTicker(type, DarkEconomy.BE, RendererBlockEntity::serverTick);
    }
}
