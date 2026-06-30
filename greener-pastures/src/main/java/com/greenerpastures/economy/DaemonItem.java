package com.greenerpastures.economy;

import com.greenerpastures.buff.BuffId;
import com.greenerpastures.buff.DaemonLoadout;
import com.greenerpastures.core.GpLog;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;

/**
 * The Daemon — the player's handle to their <b>Data</b> account (the dark economy's currency) and the carrier of
 * a <b>compiled buff loadout</b> ({@code com.greenerpastures.buff}). <b>BUG-004 redesign:</b> a Daemon no longer
 * grants the whole catalog at a global Mk tier. You compile the specific buffs you want (each at a chosen level)
 * onto it — at a Compiler, or via {@code /gp daemon set} — and it carries that as the
 * {@code greenerpastures:daemon_loadout} component. Data is player-bound + server-side, so the balance survives
 * losing the item.
 *
 * <p><b>Right-click toggles it ON/OFF</b> (mirrored to the vanilla enchant glint so you can see it's live). While
 * ON, it grants its installed buffs from <i>anywhere in your inventory</i> — not just in-hand — and drains only
 * the summed cost of those buffs. OFF = inert, zero drain. It never force-loads a chunk: it acts only for an
 * online player whose (always-loaded) inventory holds it.
 */
public class DaemonItem extends Item {
    public DaemonItem(Settings settings) {
        super(settings);
    }

    /** Is this Daemon toggled ON (granting its loadout + showing the glint)? Default false. */
    public static boolean isOn(ItemStack stack) {
        return stack.getOrDefault(DarkEconomy.DAEMON_ON, Boolean.FALSE);
    }

    /** The buffs compiled onto this Daemon ({@link DaemonLoadout#NONE} if none). */
    public static DaemonLoadout loadoutOf(ItemStack stack) {
        DaemonLoadout l = stack.get(DarkEconomy.DAEMON_LOADOUT);
        return l == null ? DaemonLoadout.NONE : l;
    }

    /** Set the ON flag and mirror it to the vanilla enchant glint, so toggling shows the shine immediately. */
    public static void setOn(ItemStack stack, boolean on) {
        stack.set(DarkEconomy.DAEMON_ON, on);
        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, on);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp && sp.getServer() != null) {
            boolean now = !isOn(held);
            setOn(held, now);
            DaemonLoadout loadout = loadoutOf(held);
            long balance = DataStore.get(sp.getServer()).balanceOf(sp.getUuid());
            sp.sendMessage(Text.literal(now
                    ? "§5⌬ Daemon ON§r — " + summarize(loadout) + " · Data: §d" + balance
                    : "§5⌬ Daemon OFF§r — Data: §d" + balance), true);
            GpLog.i("daemon", "toggle", "player", sp.getUuid().toString(), "on", now,
                    "buffs", loadout.toLevels().size(), "data", balance);
        }
        return TypedActionResult.success(held, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        boolean on = isOn(stack);
        tooltip.add(Text.literal(on ? "● ON — buffs active anywhere in your inventory" : "○ OFF — right-click to enable")
                .formatted(on ? Formatting.LIGHT_PURPLE : Formatting.DARK_GRAY));
        Map<BuffId, Integer> m = loadoutOf(stack).toLevels();
        if (m.isEmpty()) {
            tooltip.add(Text.literal("No buffs compiled — /gp daemon set <buff> <level>").formatted(Formatting.DARK_GRAY));
            return;
        }
        m.forEach((id, lvl) -> tooltip.add(Text.literal("  " + id.label + " +" + lvl).formatted(Formatting.GRAY)));
        tooltip.add(Text.literal("Drains Data while ON · only for what's installed").formatted(Formatting.DARK_GRAY));
    }

    /** One-line loadout summary for the toggle message — first few buffs, then a "+N more" tail. */
    private static String summarize(DaemonLoadout loadout) {
        Map<BuffId, Integer> m = loadout.toLevels();
        if (m.isEmpty()) return "§7no buffs compiled (/gp daemon set)§r";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<BuffId, Integer> e : m.entrySet()) {
            if (shown >= 4) { sb.append(", +").append(m.size() - shown).append(" more"); break; }
            if (shown > 0) sb.append(", ");
            sb.append(e.getKey().label).append(" +").append(e.getValue());
            shown++;
        }
        return sb.toString();
    }
}
