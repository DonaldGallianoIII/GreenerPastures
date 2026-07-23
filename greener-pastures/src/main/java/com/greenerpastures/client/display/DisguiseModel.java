package com.greenerpastures.client.display;

import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;

import java.util.function.Supplier;

/**
 * The disguise render (Display Suite v2 §2.2): wraps a display block's baked model so that, when its block
 * entity carries a disguise, it emits the DISGUISE block's quads instead of its own - the block "blends in"
 * for aesthetic gyms and puzzles. Undisguised → forwards to the real model unchanged.
 *
 * <p>The disguise {@link BlockState} arrives as the block entity's render attachment
 * ({@code getRenderAttachmentData()} → captured at chunk-mesh time; a disguise change re-meshes via the
 * block update in {@code setDisguise}). Wrapped onto our two block models by a {@code ModelLoadingPlugin}.
 */
public class DisguiseModel extends ForwardingBakedModel {

    public DisguiseModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;   // required so emitBlockQuads is actually invoked
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos,
                               Supplier<Random> randomSupplier, RenderContext context) {
        BlockState disguise = null;
        try {
            Object data = ((FabricBlockView) blockView).getBlockEntityRenderData(pos);
            if (data instanceof BlockState bs && !bs.isAir()) disguise = bs;
        } catch (Throwable ignored) {
            // render data unavailable → fall through to the real model, never crash the chunk mesh
        }
        if (disguise != null) {
            BakedModel dm = MinecraftClient.getInstance().getBlockRenderManager().getModel(disguise);
            dm.emitBlockQuads(blockView, disguise, pos, randomSupplier, context);   // draw the mimicked block
            return;
        }
        super.emitBlockQuads(blockView, state, pos, randomSupplier, context);       // undisguised → our own model
    }
}
