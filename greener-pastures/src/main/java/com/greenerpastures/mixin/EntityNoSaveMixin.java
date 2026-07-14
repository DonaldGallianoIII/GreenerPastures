package com.greenerpastures.mixin;

import com.greenerpastures.display.DisplaySuite;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Display Suite's anti-dupe backstop (spec §0-1): an Exhibit Pen projection must NEVER reach the
 * world save - the disk in the pen is the single source of truth, and a serialized projection would
 * come back as a second copy after a crash mid-autosave. Vanilla filters every entity through
 * {@code shouldSave()} on chunk serialization, so one HEAD check on our command tag ({@link
 * DisplaySuite#PROJECTION_TAG}) covers autosave, chunk unload, and server stop alike. The tag-set
 * lookup is a {@code Set.contains} on a set that is empty for every entity we didn't mark - cheap.
 */
@Mixin(Entity.class)
public abstract class EntityNoSaveMixin {

    @Inject(method = "shouldSave", at = @At("HEAD"), cancellable = true)
    private void greenerpastures$neverSaveExhibitProjections(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getCommandTags().contains(DisplaySuite.PROJECTION_TAG)) {
            cir.setReturnValue(false);
        }
    }
}
