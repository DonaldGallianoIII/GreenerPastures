package com.greenerpastures.pasture.breeding;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.core.GpLog;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Notebook — the player's unified console (see {@code NOTEBOOK_CONSOLE_SPEC.md}). It <b>replaces the
 * Pasture Wand</b> and has two open modes:
 * <ul>
 *   <li><b>Right-click a pasture</b> → that pasture's config screen (reuses {@link PastureWand#openMenu}).</li>
 *   <li><b>Right-click anything else / the air</b> → the tabbed console (Pastures · Storage · Augmenter · …).</li>
 * </ul>
 * The console UI isn't built yet (task #36), so that path currently just posts a stub message — the item is
 * live and does the wand's job today; the console lands with the full build.
 */
public class NotebookItem extends Item {

    public NotebookItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof PokemonPastureBlockEntity)) {
            be = world.getBlockEntity(pos.down());
        }
        if (!(be instanceof PokemonPastureBlockEntity)) {
            return ActionResult.PASS;   // not a pasture → fall through to use() → console
        }
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (ctx.getPlayer() instanceof ServerPlayerEntity sp) {
            PastureWand.openMenu(sp, be.getPos());
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            // Console UI not built yet (NOTEBOOK_CONSOLE_SPEC.md / task #36) — stub the entry point so the
            // gesture is discoverable and logged.
            sp.sendMessage(Text.literal("§a🖥 Notebook console§r — coming soon (pastures · storage · augmenter · dashboard)"), true);
            GpLog.i("notebook", "console_open_stub", "player", sp.getUuid().toString());
        }
        return TypedActionResult.success(held, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.literal("Right-click a pasture to configure it").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click the air for the console").formatted(Formatting.DARK_GRAY));
    }
}
