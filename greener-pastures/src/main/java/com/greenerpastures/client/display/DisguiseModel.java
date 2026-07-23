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
            // Apply the disguise block's BIOME TINT (grass green, foliage, water, ...) - the render context
            // tints for OUR block (untinted), so tinted quads would come out grey without this. ARGB quads.
            final BlockState tintState = disguise;
            net.minecraft.client.color.block.BlockColors colors = MinecraftClient.getInstance().getBlockColors();
            context.pushTransform(quad -> {
                int ti = quad.colorIndex();
                if (ti != -1) {
                    int tint = colors.getColor(tintState, blockView, pos, ti);   // 0xRRGGBB, or -1 (white) if none
                    int tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
                    for (int i = 0; i < 4; i++) {
                        int c = quad.color(i);
                        int a = (c >>> 24) & 0xFF;
                        int r = ((c >> 16) & 0xFF) * tr / 255;
                        int g = ((c >> 8) & 0xFF) * tg / 255;
                        int b = (c & 0xFF) * tb / 255;
                        quad.color(i, (a << 24) | (r << 16) | (g << 8) | b);
                    }
                }
                return true;
            });
            dm.emitBlockQuads(blockView, disguise, pos, randomSupplier, context);   // draw the mimicked block
            context.popTransform();
            return;
        }
        super.emitBlockQuads(blockView, state, pos, randomSupplier, context);       // undisguised → our own model
    }
}
