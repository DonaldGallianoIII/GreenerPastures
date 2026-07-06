package com.greenerpastures.egg.oracle.cull;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * CLIENT-ONLY tooltip probe, quarantined in its own class. The JVM verifier resolves a method's
 * class references at its FIRST CALL - so an in-method {@code EnvType} guard around client classes
 * does nothing (BUG-021: dedicated-server ingest threw {@code NoClassDefFoundError: class_746} the
 * moment {@code shinyByName} was invoked, and every egg fell back to the tray). Only
 * {@link EggReader#shinyByName} touches this class, strictly behind the environment check, so a
 * dedicated server never loads it.
 */
final class EggTooltipProbe {
    private EggTooltipProbe() {}

    static boolean tooltipHasStar(ItemStack stack, char star) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        try {
            List<Text> lines = stack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
            for (Text t : lines) {
                if (t.getString().indexOf(star) >= 0) return true;
            }
        } catch (Exception ignored) {
            // some modded tooltips can throw; treat as non-shiny
        }
        return false;
    }
}
