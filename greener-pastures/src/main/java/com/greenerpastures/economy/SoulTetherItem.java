package com.greenerpastures.economy;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Soul Tether item - a rented amplifier. Blank until inscribed at a Compiler with {@code [function,
 * tier]} (costs Data); slotted into a Kernel's functional slot it amplifies that Kernel's matching base
 * mod while the operator's Daemon is fed. All magnitude / burn numbers come from the tested
 * {@link SoulTether}, so the tooltip is the single source of truth with the runtime.
 */
public class SoulTetherItem extends Item {
    public SoulTetherItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        Tether t = stack.get(DarkEconomy.TETHER);
        if (t == null || t.isBlank()) {
            tooltip.add(Text.literal("Blank - inscribe [function · tier] at the Notebook's Loom").formatted(Formatting.DARK_GRAY));
            return;
        }
        SoulTether st = t.toSoulTether();
        AugmentFunction f = AugmentFunction.byId(t.function());
        String name = (f != null) ? f.label : t.function();
        String cls = (f != null && f.cls == TetherClass.THROUGHPUT) ? "throughput" : "quality";
        String effect = (f != null) ? f.boostLabel(t.tier()) : "+" + t.tier() + " levels";
        tooltip.add(Text.literal(name + " Tether · Tier " + roman(t.tier())).formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal(effect + " on top of its Kernel mod, past its normal max · burns "
                + st.burnPerCycle() + " Data/cycle (" + cls + ")").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Slot it on a pasture's config screen · rented while the Daemon is fed").formatted(Formatting.DARK_GRAY));
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> String.valueOf(tier);
        };
    }
}
