package com.hydrogrid;

import com.hydrogrid.farm.Farm;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class HydroKeys {
    private HydroKeys() {}

    private static KeyBinding toggle, stamp, field, farmRun, farmMode;

    public static void register() {
        toggle = reg("toggle", GLFW.GLFW_KEY_H);
        stamp = reg("stamp", GLFW.GLFW_KEY_V);
        field = reg("field", GLFW.GLFW_KEY_N);
        farmRun = reg("farmrun", GLFW.GLFW_KEY_COMMA);
        farmMode = reg("farmmode", GLFW.GLFW_KEY_PERIOD);
    }

    private static KeyBinding reg(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.hydrogrid." + name, InputUtil.Type.KEYSYM, key, "category.hydrogrid"));
    }

    public static void handle(MinecraftClient mc) {
        while (toggle.wasPressed()) {
            Hydro.toggle();
            msg(mc, "overlay " + (Hydro.enabled ? "ON" : "OFF"));
        }
        while (stamp.wasPressed()) {
            if (mc.player == null) continue;
            BlockPos p = mc.player.getBlockPos().down();   // the block you're standing on
            Field.stamp(p);
            if (!Field.isSet()) {
                msg(mc, "corner A " + p.toShortString() + "  — walk to the opposite corner and press V");
            } else {
                msg(mc, "field " + Field.sizeX + "x" + Field.sizeZ + "  — " + Field.needed()
                        + " water spots (" + Field.remaining() + " to place)");
            }
        }
        while (field.wasPressed()) {
            mc.setScreen(new FieldScreen());
        }
        while (farmRun.wasPressed()) {
            Farm.toggle();
            if (Farm.active && mc.player != null) {
                Farm.homeSlot = mc.player.getInventory().selectedSlot;   // your harvest tool (e.g. Fortune pickaxe)
            }
            String hint = Farm.active
                    ? (Farm.mode == Farm.Mode.TILL_PLANT
                        ? "  (hold a hoe + seeds)"
                        : "  (held = harvest tool; keep vivichoke seeds in the hotbar)")
                    : "";
            msg(mc, "Auto-Farm " + Farm.mode.label() + ": " + (Farm.active ? "ON" : "OFF") + hint);
        }
        while (farmMode.wasPressed()) {
            Farm.cycleMode();
            msg(mc, "Auto-Farm mode: " + Farm.mode.label());
        }
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[HydroGrid] " + s), true);
    }
}
