package com.greenerpastures.mixin;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.pasture.keeper.PastureCollector;
import com.greenerpastures.pasture.keeper.PastureKeeper;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two hooks on Cobblemon's pasture block entity (both target Cobblemon's own methods, hence
 * remap=false — they aren't obfuscated Minecraft methods):
 * <ul>
 *   <li>togglePastureOn: force "off" for suppressed pastures so the tethered mons never spawn to wander.</li>
 *   <li>checkPokemon (TAIL): sweep nearby pasture loot into an adjacent chest.</li>
 * </ul>
 */
@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

    @ModifyVariable(method = "togglePastureOn", at = @At("HEAD"), argsOnly = true, remap = false)
    private boolean greenerpastures$forceOff(boolean on) {
        BlockEntity self = (BlockEntity) (Object) this;
        return PastureKeeper.isSuppressed(self.getPos()) ? false : on;
    }

    @Inject(method = "checkPokemon", at = @At("TAIL"), remap = false)
    private void greenerpastures$collect(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        PastureCollector.collect(self.getWorld(), self.getPos());
    }
}
