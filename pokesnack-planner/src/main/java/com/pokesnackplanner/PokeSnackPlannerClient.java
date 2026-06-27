package com.pokesnackplanner;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class PokeSnackPlannerClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PlannerKeys.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            PlannerKeys.handle(mc);
            Planner.scanHome(mc);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(OverlayRenderer::render);
        HudRenderCallback.EVENT.register(PlannerHud::render);
    }
}
