package com.greenerpastures.client;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.egg.highlighter.ShinyEggHighlighterClient;
import com.greenerpastures.egg.oracle.EggOracleClient;
import com.greenerpastures.client.notebook.NotebookScreen;
import com.greenerpastures.client.notebook.NotebookState;
import com.greenerpastures.notebook.bridge.DsBridge;
import com.greenerpastures.notebook.net.NotebookAugmenterS2C;
import com.greenerpastures.notebook.net.NotebookBioBankS2C;
import com.greenerpastures.notebook.net.NotebookCompilerS2C;
import com.greenerpastures.notebook.net.NotebookPasturesS2C;
import com.greenerpastures.notebook.net.NotebookRequestC2S;
import com.greenerpastures.notebook.net.NotebookStatusS2C;
import com.greenerpastures.notebook.net.NotebookStorageS2C;
import com.greenerpastures.pasture.breeding.NotebookItem;
import com.greenerpastures.pasture.breeding.compiler.CompilerMenu;
import com.greenerpastures.pasture.breeding.compiler.CompilerScreen;
import com.greenerpastures.pasture.breeding.gui.PastureMenu;
import com.greenerpastures.pasture.breeding.gui.PastureScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

/**
 * Client entrypoint — keybinds, HUD overlays, container overlays, and screen registration.
 */
public final class GreenerPasturesClient implements ClientModInitializer {
    /** Ticks since load; gates the notebook live-poll to ~1×/s while the console is open. */
    private static int notebookPollTick;

    @Override
    public void onInitializeClient() {
        GreenerPastures.LOG.info("Greener Pastures — client init");

        // egg/ (client UI + container overlays)
        EggOracleClient.init();           // odds calculator, egg culler, pasture finder/check
        ShinyEggHighlighterClient.init(); // shiny-egg gold glow + tally in containers

        // pasture/ wand GUI + Compiler bench
        HandledScreens.register(PastureMenu.TYPE, PastureScreen::new);
        HandledScreens.register(CompilerMenu.TYPE, CompilerScreen::new);

        // notebook/ console — client-side open hook for the Notebook item (air / non-pasture right-click)
        NotebookItem.CONSOLE_OPENER = () -> MinecraftClient.getInstance().setScreen(new NotebookScreen());

        // notebook/ console sync — receive status pushes → cache + refresh the open console
        ClientPlayNetworking.registerGlobalReceiver(NotebookStatusS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyStatus(payload)) NotebookScreen.refreshIfOpen();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookStorageS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyStorage(payload)) NotebookScreen.refreshIfOpen();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookCompilerS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyCompiler(payload)) NotebookScreen.refreshIfOpen();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookPasturesS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyPastures(payload)) NotebookScreen.refreshIfOpen();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookAugmenterS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyAugmenter(payload)) NotebookScreen.refreshIfOpen();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookBioBankS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyBiobank(payload)) NotebookScreen.refreshIfOpen();
                }));

        // notebook/ console — poll the server ~1×/s while the console is open so the status bar + tabs tick live.
        // Change-detection in NotebookState means an unchanged push does NOT repaint (no flicker / scroll-reset).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!(client.currentScreen instanceof NotebookScreen)) return;
            if (client.getNetworkHandler() == null) return;
            if (++notebookPollTick % 20 == 0) ClientPlayNetworking.send(new NotebookRequestC2S(0));
        });

        // notebook/ console — live WS bridge for the React UI (dev browser now, MCEF in-game later). Loopback :25599.
        DsBridge.init();

        // analytics/ chart screens land here later.
    }
}
