package com.eggoracle;

import com.eggoracle.calc.Profile;
import com.eggoracle.cull.CullSettings;
import com.eggoracle.cull.EggCuller;
import com.eggoracle.farm.Finder;
import com.eggoracle.farm.PastureRoster;
import com.eggoracle.farm.PastureCheck;
import com.eggoracle.farm.PokemonFinder;
import com.eggoracle.ui.EggOracleScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class EggOracleClient implements ClientModInitializer {
    /** Shared in-session profile so edits persist between opens. */
    public static final Profile PROFILE = new Profile();
    /** Shared egg-culler filter/display settings. */
    public static final CullSettings CULL = new CullSettings();
    /** Shared pasture roster backing the Farm dashboard (persisted to config/eggoracle_farm.txt). */
    public static final PastureRoster ROSTER = new PastureRoster();

    // While a container is open: toggle the culler overlay on/off.
    private static final int TOGGLE_CULL_KEY = GLFW.GLFW_KEY_C;

    @Override
    public void onInitializeClient() {
        ROSTER.load();

        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.eggoracle.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "category.eggoracle"));
        KeyBinding clearFind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.eggoracle.clearfind", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.eggoracle"));
        KeyBinding pastureCheck = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.eggoracle.pasturecheck", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.eggoracle"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (open.wasPressed()) mc.setScreen(new EggOracleScreen(PROFILE));
            while (clearFind.wasPressed()) {
                if (Finder.active()) {
                    msg(mc, "[EggOracle] Finder cleared (" + Finder.target + ")");
                    Finder.clear();
                }
            }
            while (pastureCheck.wasPressed()) {
                PastureCheck.toggle();
                msg(mc, "[EggOracle] Pasture fill check " + (PastureCheck.enabled ? "ON" : "OFF"));
            }
        });

        // Pasture finder: highlight a target species' Pokémon in the world + a HUD readout.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PokemonFinder::renderWorld);
        HudRenderCallback.EVENT.register(PokemonFinder::renderHud);

        // Pasture fill check (toggle, key J): box under-staffed pastures (1 mon).
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PastureCheck::renderWorld);
        HudRenderCallback.EVENT.register(PastureCheck::renderHud);

        // Egg Culler: tint keep/cull eggs in any open container (drawn last, over Sophisticated GUIs).
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (!(screen instanceof HandledScreen<?>)) return;
            ScreenEvents.afterRender(screen).register(
                    (scr, ctx, mx, my, td) -> EggCuller.drawOverlays((HandledScreen<?>) scr, ctx, CULL));
            ScreenKeyboardEvents.afterKeyPress(screen).register((scr, key, scancode, mods) -> {
                if (key == TOGGLE_CULL_KEY) {
                    CULL.enabled = !CULL.enabled;
                    msg(client, "[EggOracle] Egg culler " + (CULL.enabled ? "ON" : "OFF"));
                }
            });
        });
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }
}
