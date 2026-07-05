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

    /** MissingNo. never enters a pasture (adversarial review, 2026-07-05): tethered mons leave the party,
     *  so the glitch would FREEZE on its current species - frozen on Ditto it becomes an immortal universal
     *  breeding parent, breaking the purely-cosmetic contract. Refused at the source, with a message. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "tether", at = @org.spongepowered.asm.mixin.injection.At("HEAD"),
            cancellable = true, remap = false)
    private void greenerpastures$refuseGlitchTether(net.minecraft.server.network.ServerPlayerEntity player,
            com.cobblemon.mod.common.pokemon.Pokemon pokemon, net.minecraft.util.math.Direction direction,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        try {
            if (com.greenerpastures.glitch.Missingno.isMissingno(pokemon)) {
                player.sendMessage(net.minecraft.text.Text.literal(
                        "§d§kA§r§5 MissingNo. refuses confinement - it is a trophy, not livestock. §d§kA§r"), false);
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // never break tethering for normal mons
        }
    }

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
