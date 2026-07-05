package com.greenerpastures.mixin;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.pasture.keeper.PastureKeeper;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The <b>"ghost pasture"</b> spawn block. Cobblemon turns a tethered mon into a roaming {@code PokemonEntity} from
 * exactly one place - the single {@code World.spawnEntity} call inside {@code tether()}. For a suppressed pasture
 * we redirect that call: the tether <i>data</i> is still recorded (so breeding + loot keep working) but no entity
 * enters the world, so the farm stays entity-free. One interception at the source - <b>no per-tick work</b>.
 *
 * <p>The enclosing {@code tether} is Cobblemon's own method (hence {@code remap=false} on the {@code @Redirect});
 * the redirected {@code World.spawnEntity} is a vanilla Minecraft method, so its {@code @At} target stays
 * {@code remap=true}. (The old {@code togglePastureOn} hook was removed - that method only flips the block's ON
 * visual; it never controlled entity spawning, so it did nothing. QA caught it.)
 */
@Mixin(PokemonPastureBlockEntity.class)
public class PokemonPastureBlockEntityMixin {

    @Redirect(method = "tether",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z",
                     remap = true),
            remap = false)
    private boolean greenerpastures$blockSuppressedSpawn(World world, Entity entity) {
        PokemonPastureBlockEntity self = (PokemonPastureBlockEntity) (Object) this;
        if (PastureKeeper.isSuppressed(world, self.getPos())) {
            return true;   // report success so tether() still records the data - just don't add the entity
        }
        return world.spawnEntity(entity);
    }
}
