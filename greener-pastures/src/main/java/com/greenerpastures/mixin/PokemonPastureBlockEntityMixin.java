package com.greenerpastures.mixin;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.pasture.keeper.PastureKeeper;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Hook on Cobblemon's pasture block entity — togglePastureOn (HEAD): force "off" for suppressed
 * pastures so their tethered mons never spawn to wander (the no-wander lag fix). Targets Cobblemon's
 * own method, hence remap=false (it isn't an obfuscated Minecraft method).
 *
 * <p>(The old checkPokemon loot-sweep was removed 2026-06-28 — loot moves to a dedicated %-chance
 * loot block under the dark-economy drops system.)
 */
@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

    @ModifyVariable(method = "togglePastureOn", at = @At("HEAD"), argsOnly = true, remap = false)
    private boolean greenerpastures$forceOff(boolean on) {
        BlockEntity self = (BlockEntity) (Object) this;
        return PastureKeeper.isSuppressed(self.getPos()) ? false : on;
    }
}
