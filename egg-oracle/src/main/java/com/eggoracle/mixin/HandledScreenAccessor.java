package com.eggoracle.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected GUI origin (top-left) of any container screen, so the afterRender
 * overlay can position slot tints in absolute screen coordinates.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x") int eggoracle$getX();
    @Accessor("y") int eggoracle$getY();
}
