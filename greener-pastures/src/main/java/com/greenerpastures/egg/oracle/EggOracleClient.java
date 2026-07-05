package com.greenerpastures.egg.oracle;

import com.greenerpastures.egg.oracle.calc.Profile;
import com.greenerpastures.egg.oracle.cull.CullSettings;
import com.greenerpastures.egg.oracle.farm.Finder;
import com.greenerpastures.egg.oracle.farm.PastureRoster;
import com.greenerpastures.egg.oracle.farm.PastureCheck;
import com.greenerpastures.egg.oracle.farm.PokemonFinder;
import com.greenerpastures.egg.oracle.ui.EggOracleScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class EggOracleClient {
    /** Shared in-session profile so edits persist between opens. */
    public static final Profile PROFILE = new Profile();
    /** Shared egg-culler filter/display settings. */
    public static final CullSettings CULL = new CullSettings();
    /** Shared pasture roster backing the Farm dashboard (persisted to config/eggoracle_farm.txt). */
    public static final PastureRoster ROSTER = new PastureRoster();

    public static void init() {
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
                PastureCheck.chatReport(mc);
            }
        });

        // Pasture finder: highlight a target species' Pokémon in the world + a HUD readout.
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PokemonFinder::renderWorld);
        HudRenderCallback.EVENT.register(PokemonFinder::renderHud);

        // Pasture fill check (toggle, key J): box under-staffed pastures (1 mon).
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PastureCheck::renderWorld);
        HudRenderCallback.EVENT.register(PastureCheck::renderHud);

        // Egg Culler overlay DISABLED - it drew the floating keep/cull readout on EVERY container
        // (inventory, chests, …), which was intrusive. Being reworked from a global overlay into a
        // dedicated item + GUI (like the Pasture Wand). The EggCuller/CullSettings code is kept for
        // that rework. See task: "Rework egg culler into an item + GUI".
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }
}
