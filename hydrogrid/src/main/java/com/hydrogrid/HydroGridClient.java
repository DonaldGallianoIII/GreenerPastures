package com.hydrogrid;

import com.hydrogrid.farm.AutoFarm;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class HydroGridClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HydroKeys.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            HydroKeys.handle(mc);
            Hydro.tick(mc);
            Field.tick(mc);
            AutoFarm.tick(mc);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(HydroRenderer::render);
        HudRenderCallback.EVENT.register(HydroHud::render);
    }
}
