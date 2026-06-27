package com.pokesnackplanner;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public final class PlannerKeys {
    private static KeyBinding anchorKey;
    private static KeyBinding platformKey;

    private PlannerKeys() {}

    public static void register() {
        anchorKey = reg("anchor", GLFW.GLFW_KEY_P);
        platformKey = reg("platform", GLFW.GLFW_KEY_L);
    }

    private static KeyBinding reg(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.pokesnackplanner." + name, InputUtil.Type.KEYSYM, key, "category.pokesnackplanner"));
    }

    public static void handle(MinecraftClient mc) {
        while (anchorKey.wasPressed()) {
            BlockPos look = lookedAtBlock(mc);
            if (look != null) {
                Planner.setAnchor(look);
                msg(mc, "anchor " + look.toShortString()
                        + "  | mine base->bedrock at " + (look.getX() - Planner.HOME_OFFSET) + "," + (look.getZ() - Planner.HOME_OFFSET));
            } else {
                Planner.clear();
                msg(mc, "anchor cleared");
            }
        }
        while (platformKey.wasPressed()) {
            if (Planner.anchor == null) {
                msg(mc, "lock an anchor first (P)");
                continue;
            }
            BlockPos look = lookedAtBlock(mc);
            if (look != null) {
                Planner.platformY = look.getY();
                msg(mc, "platform plane Y=" + Planner.platformY
                        + "  (covers " + (Planner.platformY - Planner.V_REACH) + ".." + (Planner.platformY + Planner.V_REACH) + ")");
            }
        }
    }

    private static BlockPos lookedAtBlock(MinecraftClient mc) {
        HitResult hit = mc.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            return bhr.getBlockPos();
        }
        return null;
    }

    private static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Planner] " + s), true);
    }
}
