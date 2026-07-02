package com.greenerpastures.client;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.egg.highlighter.ShinyEggHighlighterClient;
import com.greenerpastures.egg.oracle.EggOracleClient;
import com.greenerpastures.client.notebook.NotebookBrowserScreen;
import com.greenerpastures.client.notebook.NotebookScreen;
import com.greenerpastures.client.notebook.NotebookState;
import com.greenerpastures.notebook.bridge.DsBridge;
import com.greenerpastures.notebook.net.NotebookAugmenterS2C;
import com.greenerpastures.notebook.net.NotebookBioBankS2C;
import com.greenerpastures.notebook.net.NotebookCompilerS2C;
import com.greenerpastures.notebook.net.NotebookDashboardS2C;
import com.greenerpastures.notebook.net.NotebookEggLogS2C;
import com.greenerpastures.notebook.net.NotebookGoalsS2C;
import com.greenerpastures.notebook.net.NotebookGraphS2C;
import com.greenerpastures.notebook.net.NotebookPastureConfigS2C;
import com.greenerpastures.notebook.net.NotebookPasturesS2C;
import com.greenerpastures.notebook.net.NotebookRequestC2S;
import com.greenerpastures.notebook.net.NotebookStatusS2C;
import com.greenerpastures.notebook.net.NotebookStorageS2C;
import com.greenerpastures.pasture.breeding.NotebookItem;
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
    private static int warmTick;

    @Override
    public void onInitializeClient() {
        GreenerPastures.LOG.info("Greener Pastures — client init");

        // egg/ (client UI + container overlays)
        EggOracleClient.init();           // odds calculator, egg culler, pasture finder/check
        ShinyEggHighlighterClient.init(); // shiny-egg gold glow + tally in containers

        // pasture/ wand GUI + Compiler bench
        HandledScreens.register(PastureMenu.TYPE, PastureScreen::new);

        // notebook/ console — client-side open hook for the Notebook item (air / non-pasture right-click).
        // With MCEF installed → the React console in-game (Chromium); otherwise fall back to the owo UI.
        NotebookItem.CONSOLE_OPENER = () -> {   // air → tabbed console
            if (NotebookState.pastureConfig != null) NotebookBrowserScreen.curtain();   // coming from a pasture view → curtain the switch
            NotebookState.pastureConfig = null; NotebookState.pastureGraphJson = ""; NotebookState.pastureConfigLoading = false;
            openConsole();
        };
        NotebookItem.PASTURE_OPENER = (pos) -> {
            // Show the config view immediately so the previously-open tab doesn't linger. Only reset to a LOADING
            // shell when opening a DIFFERENT pasture — reopening the same one keeps its cached config (no flash);
            // an unrelated pasture shows "loading…" (not misleading empty/unlinked data) until the S2C lands, and
            // a curtain hides the previous (air / other-pasture) view until this one paints.
            NotebookPastureConfigS2C cur = NotebookState.pastureConfig;
            if (cur == null || cur.pos() != pos.asLong()) {
                NotebookState.pastureConfig = new NotebookPastureConfigS2C(pos.asLong(), "", "", false, 0, java.util.List.of());
                NotebookState.pastureGraphJson = "";
                NotebookState.pastureConfigLoading = true;
                NotebookBrowserScreen.awaitPasture();   // native loading overlay until React confirms the pasture view painted
            }
            DsBridge.pushNow();
            openConsole();
        };

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
        ClientPlayNetworking.registerGlobalReceiver(NotebookPastureConfigS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyPastureConfig(payload)) { NotebookScreen.refreshIfOpen(); DsBridge.pushNow(); }
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookGraphS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyGraph(payload)) { NotebookScreen.refreshIfOpen(); DsBridge.pushNow(); }
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookEggLogS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyEggLog(payload)) NotebookScreen.refreshIfOpen();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookDashboardS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyDashboard(payload)) NotebookScreen.refreshIfOpen();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookGoalsS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyGoals(payload)) NotebookScreen.refreshIfOpen();
                }));

        // notebook/ console — poll the server ~1×/s while the console is open so the status bar + tabs tick live.
        // Change-detection in NotebookState means an unchanged push does NOT repaint (no flicker / scroll-reset).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!(client.currentScreen instanceof NotebookScreen)) return;
            if (client.getNetworkHandler() == null) return;
            if (++notebookPollTick % 20 == 0) ClientPlayNetworking.send(new NotebookRequestC2S(0));
        });

        // Pre-warm the MCEF console browser in the background once in a world, so the FIRST open shows an
        // already-painted page (no black/loading blip). preload() self-guards: no MCEF / not-ready / already-warm.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getNetworkHandler() == null) return;
            if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mcef")) return;
            if (++warmTick % 40 == 0) NotebookBrowserScreen.preload();
        });

        // notebook/ console — live WS bridge for the React UI (dev browser now, MCEF in-game later). Loopback :25599.
        DsBridge.init();

        // analytics/ chart screens land here later.
    }

    /** Open the Notebook console — the MCEF React UI when the {@code mcef} mod is present, else the owo shell. */
    private static void openConsole() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mcef")) {
            mc.setScreen(new NotebookBrowserScreen());
        } else {
            mc.setScreen(new NotebookScreen());
        }
    }
}
