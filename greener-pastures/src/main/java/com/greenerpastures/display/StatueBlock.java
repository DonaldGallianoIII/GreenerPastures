package com.greenerpastures.display;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The <b>Specimen Statue</b> plinth (spec §2). v1 control map (Deuce feel-passes these, spec §5 Q2):
 * written disk = set the statue · empty right-click = rotate 22.5° · empty sneak-right-click = cycle
 * scale · stick = nudge the current axis in 1/16 steps · sneak-stick = switch axis · shears = reclaim
 * the disk · break = disk pops. All right-click variants on purpose - left-click stays vanilla
 * (the viewport-UI principle: never overload left-click).
 */
public class StatueBlock extends BlockWithEntity {

    public static final MapCodec<StatueBlock> CODEC = createCodec(StatueBlock::new);

    public StatueBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public net.minecraft.block.entity.BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new StatueBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        if (!world.isClient && placer instanceof PlayerEntity player
                && world.getBlockEntity(pos) instanceof StatueBlockEntity statue) {
            statue.setOwner(player.getUuid());
            if (world.getServer() != null) {
                ExhibitStore.get(world.getServer()).register(player.getUuid(),
                        ExhibitStore.entryFor(world, pos, "Specimen Statue", ""));
                com.greenerpastures.core.GpLog.i("display", "registered", "type", "Specimen Statue",
                        "pos", pos.toShortString(), "owner", player.getUuid().toString());
            }
        }
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                             PlayerEntity player, Hand hand, BlockHitResult hit) {
        boolean disk = stack.isOf(com.greenerpastures.economy.GpItems.SPECIMEN_DISK);
        boolean stick = stack.isOf(Items.STICK);
        boolean shears = stack.isOf(Items.SHEARS);
        if (!disk && !stick && !shears) return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (world.isClient) return ItemActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp
                && world.getBlockEntity(pos) instanceof StatueBlockEntity statue) {
            if (disk) statue.tryInsert(sp, stack);
            else if (shears) statue.tryEject(sp);
            else statue.tryAdjust(sp, player.isSneaking()
                        ? StatueBlockEntity.Adjust.AXIS : StatueBlockEntity.Adjust.NUDGE);
        }
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // Rotate/scale are EMPTY-hand gestures; a random held item falling through from
        // onUseWithItem's PASS must not spin someone's museum piece.
        if (!player.getMainHandStack().isEmpty()) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity sp
                && world.getBlockEntity(pos) instanceof StatueBlockEntity statue) {
            statue.tryAdjust(sp, player.isSneaking()
                    ? StatueBlockEntity.Adjust.SCALE : StatueBlockEntity.Adjust.ROTATE);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient
                && world.getBlockEntity(pos) instanceof StatueBlockEntity statue) {
            if (world.getServer() != null && statue.getOwner() != null) {
                ExhibitStore.get(world.getServer()).deregister(statue.getOwner(),
                        world.getRegistryKey().getValue().toString(), pos.getX(), pos.getY(), pos.getZ());
                com.greenerpastures.core.GpLog.i("display", "deregistered", "type", "Specimen Statue",
                        "pos", pos.toShortString());
            }
            statue.onBroken();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
