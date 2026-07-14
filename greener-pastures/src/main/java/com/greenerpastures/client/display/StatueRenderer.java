package com.greenerpastures.client.display;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.greenerpastures.display.RenderSpec;
import com.greenerpastures.display.StatueBlockEntity;
import com.greenerpastures.display.StatueTransform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.RotationAxis;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The <b>Specimen Statue</b>'s renderer (spec §2): a client-side dummy {@code PokemonEntity} - built
 * from the synced cosmetics-only {@link RenderSpec}, never added to any world, never ticked - handed
 * to the vanilla entity render dispatcher with the plinth's {@link StatueTransform} on the matrix
 * stack. {@code FREEZE_FRAME} pins Cobblemon's animation clock, so the mon holds one idle frame
 * forever. Zero server entities; a museum hall of 50 statues costs almost nothing.
 */
public class StatueRenderer implements BlockEntityRenderer<StatueBlockEntity> {

    /** One dummy per plinth, rebuilt only when its spec changes; keys GC with their chunk's BEs. */
    private final Map<StatueBlockEntity, Dummy> dummies = new WeakHashMap<>();

    private record Dummy(RenderSpec spec, PokemonEntity entity) {}

    @Override
    public void render(StatueBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        RenderSpec spec = be.renderSpec();
        if (spec.isEmpty()) {
            dummies.remove(be);
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        Dummy dummy = dummies.get(be);
        if (dummy == null || !dummy.spec().equals(spec)) {
            PokemonEntity built = build(mc.world, spec);
            if (built == null) return;   // species not in the client's synced dex: render nothing, never crash
            dummy = new Dummy(spec, built);
            dummies.put(be, dummy);
        }

        StatueTransform t = be.transform();
        matrices.push();
        matrices.translate(0.5 + t.offsetX(), 1.0 + t.offsetY(), 0.5 + t.offsetZ());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-t.yawDegrees()));
        float scale = (float) t.scale() * spec.scaleHint();
        matrices.scale(scale, scale, scale);

        // The BER's light is sampled inside the (solid) plinth = darkness; the statue stands above it.
        int lightAbove = net.minecraft.client.render.WorldRenderer.getLightmapCoordinates(mc.world, be.getPos().up());

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadows(false);   // a statue casts block shadow, not mob shadow
        try {
            dispatcher.render(dummy.entity(), 0, 0, 0, 0f, tickDelta, matrices, vertexConsumers, lightAbove);
        } finally {
            dispatcher.setRenderShadows(true);   // vanilla's steady state (no public getter to restore from)
        }
        matrices.pop();
    }

    /** Cosmetics in, dummy out. v1 renders species + shiny + gender; form aspects ride the spec for a
     *  later pass. Returns null (renders nothing) rather than ever throwing into the render loop. */
    private static PokemonEntity build(ClientWorld world, RenderSpec spec) {
        try {
            Species species = PokemonSpecies.getByName(spec.species());
            if (species == null) return null;
            Pokemon pokemon = new Pokemon();
            pokemon.setSpecies(species);
            pokemon.setShiny(spec.shiny());
            try {
                pokemon.setGender(Gender.valueOf(spec.gender()));
            } catch (IllegalArgumentException unknownGender) {
                // spec from an older save: default gender renders fine
            }
            PokemonEntity entity = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
            entity.getDataTracker().set(PokemonEntity.getFREEZE_FRAME(), 0f);
            entity.bodyYaw = 0f;
            entity.prevBodyYaw = 0f;
            entity.headYaw = 0f;
            entity.prevHeadYaw = 0f;
            entity.setPitch(0f);
            return entity;
        } catch (Throwable t) {
            return null;
        }
    }
}
