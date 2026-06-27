package com.greenerpastures.mixin.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the protected GUI origin (top-left) of any container screen, so the
 * afterRender hook can position slot overlays in absolute screen coordinates.
 */
@Mixin(HandledScreen.class)
public interface HandledScreenMixin {
    @Accessor("x") int shinyegg$getX();
    @Accessor("y") int shinyegg$getY();
}
