package com.greenerpastures.pasture.breeding.compiler;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Compiler — right-click to open the augment bench (Kernel + augment → Compile → augmented
 * Kernel). Workstation-style: no block-entity storage; the input items are transient and returned to
 * the player on close. The compile itself is server-authoritative ({@link CompilerMenu#onButtonClick}).
 * Keeps the material-cost gate of the locked design (the augment item is consumed).
 */
public class CompilerBlock extends Block {
    public CompilerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && player instanceof ServerPlayerEntity sp) {
            final BlockPos here = pos.toImmutable();
            sp.openHandledScreen(new ExtendedScreenHandlerFactory<BlockPos>() {
                @Override public Text getDisplayName() { return Text.literal("Compiler"); }
                @Override public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity p) {
                    return new CompilerMenu(syncId, inv, here);
                }
                @Override public BlockPos getScreenOpeningData(ServerPlayerEntity p) { return here; }
            });
        }
        return ActionResult.SUCCESS;
    }
}
