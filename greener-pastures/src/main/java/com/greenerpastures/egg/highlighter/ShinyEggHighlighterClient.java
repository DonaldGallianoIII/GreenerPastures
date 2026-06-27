package com.greenerpastures.egg.highlighter;

import com.greenerpastures.mixin.client.HandledScreenMixin;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ShinyEggHighlighterClient {
    public static final Logger LOGGER = LoggerFactory.getLogger("shinyegghighlighter");
    private static KeyBinding dumpKey;

    private static final int GOLD_FILL = 0x4DFFD700;
    private static final int GOLD_BORDER = 0xFFFFD700;
    private static final int DIM_FILL = 0xB0202020;

    // Press while a chest/bank is open: G = count eggs into lifetime tally; Shift+G = reset.
    private static final int COUNT_KEY = GLFW.GLFW_KEY_G;

    public static void init() {
        dumpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shinyegghighlighter.dump",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_BACKSLASH,
                "category.shinyegghighlighter"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (dumpKey.wasPressed()) dumpHeldItem(client);
        });

        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            if (screen instanceof HandledScreen<?>) {
                // overlays drawn last, after the whole screen (covers Sophisticated Storage)
                ScreenEvents.afterRender(screen).register(
                        (scr, ctx, mx, my, td) -> drawEggOverlays((HandledScreen<?>) scr, ctx));
                // in-GUI hotkey to count/reset the bank's eggs
                ScreenKeyboardEvents.afterKeyPress(screen).register((scr, key, scancode, mods) -> {
                    if (key == COUNT_KEY && scr instanceof HandledScreen<?> hs) {
                        if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) resetStats();
                        else countEggs(hs);
                    }
                });
            }
        });

        LOGGER.info("Shiny Egg Highlighter loaded.");
    }

    // --- highlight overlays ---------------------------------------------------
    private static void drawEggOverlays(HandledScreen<?> screen, DrawContext ctx) {
        int ox = ((HandledScreenMixin) screen).shinyegg$getX();
        int oy = ((HandledScreenMixin) screen).shinyegg$getY();
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot == null || !slot.isEnabled()) continue;
            ItemStack stack = slot.getStack();
            if (!ShinyEggDetector.isEgg(stack)) continue;
            int sx = ox + slot.x;
            int sy = oy + slot.y;
            if (ShinyEggDetector.isShinyEgg(stack)) {
                ctx.fill(sx, sy, sx + 16, sy + 16, GOLD_FILL);
                ctx.drawBorder(sx, sy, 16, 16, GOLD_BORDER);
            } else {
                ctx.fill(sx, sy, sx + 16, sy + 16, DIM_FILL);
            }
        }
    }

    // --- counting -------------------------------------------------------------
    private static void countEggs(HandledScreen<?> screen) {
        int total = 0, shiny = 0;
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot == null || slot.inventory instanceof PlayerInventory) continue;  // storage only
            ItemStack stack = slot.getStack();
            if (!ShinyEggDetector.isEgg(stack)) continue;
            total++;
            if (ShinyEggDetector.isShinyEgg(stack)) shiny++;
        }
        if (total == 0) {
            msg("[ShinyEgg] No eggs in this container to count.");
            return;
        }
        EggStats s = EggStats.get();
        s.add(total, shiny);
        msg(String.format("[ShinyEgg] Counted %d eggs (%d shiny ★). Lifetime: %,d scanned, %,d shiny — %s",
                total, shiny, s.eggsScanned, s.shiniesFound, s.rateString()));
    }

    private static void resetStats() {
        EggStats.get().reset();
        msg("[ShinyEgg] Lifetime egg/shiny tally reset to 0.");
    }

    // --- debug dump -----------------------------------------------------------
    private static void dumpHeldItem(MinecraftClient client) {
        if (client.player == null) return;
        ItemStack stack = client.player.getMainHandStack();
        if (stack.isEmpty()) {
            msg("[ShinyEgg] Hold an egg in your main hand, then press the dump key.");
            return;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        NbtComponent cd = stack.get(DataComponentTypes.CUSTOM_DATA);
        String nbt = (cd != null) ? cd.copyNbt().toString() : "(no custom_data component)";
        msg("[ShinyEgg] item = " + id);
        msg("[ShinyEgg] custom_data = " + nbt);
        msg("[ShinyEgg] detected shiny? " + ShinyEggDetector.isShinyEgg(stack));
        LOGGER.info("[ShinyEgg] item={} custom_data={}", id, nbt);
        try {
            List<Text> lines = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
            for (Text t : lines) {
                msg("[ShinyEgg] tip| " + t.getString());
                LOGGER.info("[ShinyEgg] tip| {}", t.getString());
            }
        } catch (Exception e) {
            LOGGER.warn("[ShinyEgg] tooltip dump failed", e);
        }
    }

    private static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }
}
