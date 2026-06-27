package com.shedmon.shedscope;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class Keys {
    private static KeyBinding toggle;
    private static KeyBinding oresKey;
    private static KeyBinding lootKey;

    private Keys() {}

    public static void register() {
        toggle = reg("toggle", GLFW.GLFW_KEY_BACKSLASH);
        oresKey = reg("ores", GLFW.GLFW_KEY_LEFT_BRACKET);
        lootKey = reg("loot", GLFW.GLFW_KEY_RIGHT_BRACKET);
    }

    private static KeyBinding reg(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.shedscope." + name, InputUtil.Type.KEYSYM, key, "category.shedscope"));
    }

    public static void handle(MinecraftClient mc) {
        while (toggle.wasPressed()) {
            State.enabled = !State.enabled;
            msg(mc, "ShedScope " + (State.enabled ? "ON" : "OFF"));
        }
        while (oresKey.wasPressed()) {
            State.ores = !State.ores;
            msg(mc, "Ores " + (State.ores ? "shown" : "hidden"));
        }
        while (lootKey.wasPressed()) {
            State.loot = !State.loot;
            msg(mc, "Loot/Spawners " + (State.loot ? "shown" : "hidden"));
        }
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[ShedScope] " + s), true);
    }
}
