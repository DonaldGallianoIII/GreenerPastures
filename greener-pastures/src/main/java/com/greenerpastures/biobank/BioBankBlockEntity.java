package com.greenerpastures.biobank;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Thin owner block-entity for the BioBank. The eggs themselves live in {@link BioBankStore}
 * (a per-world save, keyed by this BE's position), not in chunk NBT. This BE exists so the block is
 * a {@code BlockWithEntity} (identity now; a ticker for auto-ingest from an adjacent pasture lands
 * in Batch 3).
 */
public class BioBankBlockEntity extends BlockEntity {
    public BioBankBlockEntity(BlockPos pos, BlockState state) {
        super(BioBank.BE, pos, state);
    }
}
