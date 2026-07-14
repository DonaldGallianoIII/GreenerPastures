package com.greenerpastures.client;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.egg.highlighter.ShinyEggHighlighterClient;
import com.greenerpastures.egg.oracle.EggOracleClient;
import com.greenerpastures.client.notebook.NotebookBrowserScreen;
import com.greenerpastures.client.notebook.InstallMcefScreen;
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
 * Client entrypoint - keybinds, HUD overlays, container overlays, and screen registration.
 */
public final class GreenerPasturesClient implements ClientModInitializer {
    /** Ticks since load; gates the notebook live-poll to ~1×/s while the console is open. */
    private static int warmTick;
    /** Mod-load state is immutable - resolve ONCE, not 20×/s on the tick path (perf-audit R3 client #3). */
    private static boolean mcefPresent;

    @Override
    public void onInitializeClient() {
        GreenerPastures.LOG.info("Greener Pastures - client init");
        mcefPresent = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mcef");

        // egg/ (client UI + container overlays)
        EggOracleClient.init();           // odds calculator, egg culler, pasture finder/check
        ShinyEggHighlighterClient.init(); // shiny-egg gold glow + tally in containers

        // pasture/ wand GUI + Compiler bench
        HandledScreens.register(PastureMenu.TYPE, PastureScreen::new);

        // display/ - the Specimen Statue's frozen-mon renderer (factory stores a lambda; StatueRenderer
        // and its Cobblemon client classes load lazily when the render dispatcher builds, not at init).
        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                com.greenerpastures.display.DisplaySuite.SPECIMEN_STATUE_BE,
                ctx -> new com.greenerpastures.client.display.StatueRenderer());

        // notebook/ console - client-side open hook for the Notebook item (air / non-pasture right-click).
        // With MCEF installed → the React console in-game (Chromium); otherwise an install-MCEF prompt.
        // Kernel right-click → rename screen (QoL: label your kernels)
        com.greenerpastures.pasture.breeding.BreedingUpgradeItem.renameScreenOpener =
                stack -> net.minecraft.client.MinecraftClient.getInstance().setScreen(
                        new com.greenerpastures.client.notebook.KernelRenameScreen(stack));

        NotebookItem.CONSOLE_OPENER = () -> {   // air → tabbed console
            if (NotebookState.pastureConfig != null) NotebookBrowserScreen.curtain();   // coming from a pasture view → curtain the switch
            NotebookState.pastureConfig = null; NotebookState.pastureGraphJson = ""; NotebookState.pastureExtraJson = ""; NotebookState.pastureConfigLoading = false;
            openConsole();
        };
        NotebookItem.PASTURE_OPENER = (pos) -> {
            // Stale-while-revalidate: a pasture seen this session renders its cached config INSTANTLY (fully
            // interactive) and the server refresh lands silently. Only a first-ever open shows the loading
            // shell + native overlay (lifted the moment React confirms the pasture view painted).
            long key = pos.asLong();
            String cacheKey = NotebookState.posKey(key);
            NotebookPastureConfigS2C cur = NotebookState.pastureConfig;
            if (cur == null || cur.pos() != key) {
                NotebookPastureConfigS2C cached = NotebookState.pastureConfigCache.get(cacheKey);
                if (cached != null) {
                    NotebookState.pastureConfig = cached;
                    NotebookState.pastureGraphJson = NotebookState.pastureGraphCache.getOrDefault(cacheKey, "");
                    NotebookState.pastureExtraJson = NotebookState.pastureExtraCache.getOrDefault(cacheKey, "");
                    NotebookState.pastureConfigLoading = false;
                } else {
                    NotebookState.pastureConfig = new NotebookPastureConfigS2C(key, "", "", false, 0, java.util.List.of());
                    NotebookState.pastureGraphJson = "";
                    NotebookState.pastureExtraJson = "";
                    NotebookState.pastureConfigLoading = true;
                    NotebookBrowserScreen.awaitPasture();
                }
            }
            DsBridge.pushNow();
            openConsole();
        };

        // notebook/ console sync - receive status pushes → cache + refresh the open console
        ClientPlayNetworking.registerGlobalReceiver(NotebookStatusS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyStatus(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookStorageS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyStorage(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookCompilerS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyCompiler(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookPasturesS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyPastures(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookAugmenterS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyAugmenter(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookBioBankS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyBiobank(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookPastureConfigS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyPastureConfig(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookGraphS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyGraph(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookEggLogS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyEggLog(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookDashboardS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyDashboard(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(NotebookGoalsS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyGoals(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookNotifsS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyNotifs(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookPastureExtraS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyPastureExtra(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookAugmenterMetaS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyAugMeta(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookRitualsS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyRituals(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookSpecimensS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applySpecimens(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookArcadeS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyArcade(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookTreelineS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyTreeline(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookTopdeckS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyTopdeck(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookSlotsS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applySlots(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookVibeS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyVibe(payload)) DsBridge.pushNow();
                }));
        ClientPlayNetworking.registerGlobalReceiver(com.greenerpastures.notebook.net.NotebookTagS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (NotebookState.applyTag(payload)) DsBridge.pushNow();
                }));

        // Pre-warm the MCEF console browser in the background once in a world, so the FIRST open shows an
        // already-painted page (no black/loading blip). preload() self-guards: no MCEF / not-ready / already-warm.
        // While the console is CLOSED the pump runs at a trickle (full rate only during the post-preload
        // warm-up) - the open-console render() pump + transition burst are untouched (perf-audit R3 S1/#2).
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!mcefPresent || client.getNetworkHandler() == null) return;
            if (++warmTick % 40 == 0) NotebookBrowserScreen.preload();
            if (!NotebookBrowserScreen.consoleOpen) NotebookBrowserScreen.backgroundPump(warmTick);
        });

        // notebook/ console - live WS bridge for the React UI (dev browser now, MCEF in-game later). Loopback :25599.
        DsBridge.init();

        // World-leave hygiene: wipe the client cache + re-baseline the bridge, so a new world (same JVM) never
        // shows the previous world's data in the console.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                client.execute(() -> {
                    NotebookState.clearAll();
                    NotebookBrowserScreen.browserInputFocused = false;
                    NotebookBrowserScreen.pastureReady();   // drop any pending loading overlay
                    DsBridge.onWorldLeave();
                }));

        // analytics/ chart screens land here later.
    }

    /** Open the Notebook console - the MCEF React UI when the {@code mcef} mod is present, else the install prompt. */
    private static void openConsole() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mcefPresent) {
            mc.setScreen(new NotebookBrowserScreen());
        } else {
            mc.setScreen(new InstallMcefScreen());   // owo fallback retired (Deuce 2026-07-07) - MCEF is THE console
        }
    }
}
