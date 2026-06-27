package com.shedmon.shedscope;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public class ShedScopeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Keys.register();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Keys.handle(mc);
            Scanner.tick(mc);
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(EspRenderer::render);
        HudRenderCallback.EVENT.register(HudOverlay::render);
    }
}
