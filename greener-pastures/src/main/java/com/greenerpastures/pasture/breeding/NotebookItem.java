package com.greenerpastures.pasture.breeding;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.notebook.PastureSnapshotStore;
import com.greenerpastures.pasture.breeding.gui.PastureMenu;
import com.greenerpastures.pasture.breeding.gui.PastureOpenData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
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
 *   <li><b>Right-click a pasture</b> → that pasture's config screen ({@link #openMenu}).</li>
 *   <li><b>Right-click anything else / the air</b> → the tabbed console (Pastures · Storage · Augmenter · …).</li>
 * </ul>
 * Right-click-the-air opens the owo-ui {@code NotebookScreen} shell client-side (via {@code CONSOLE_OPENER});
 * per-tab content (Pastures · Storage · Augmenter · …) is being built out next.
 */
public class NotebookItem extends Item {

    /**
     * Client-only hook that opens the Notebook console. Defaults to a no-op so this common/server class carries no
     * client references; {@code GreenerPasturesClient} sets it to open {@code NotebookScreen}. Invoked from
     * {@link #use} on the client when the Notebook is right-clicked in the air / at a non-pasture.
     */
    public static Runnable CONSOLE_OPENER = () -> {};

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
            openMenu(sp, be.getPos());
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Open a pasture's config screen (moved here from the retired Pasture Wand). Shared by the Notebook's
     * right-click-a-pasture path and the {@code OpenPasturePayload} Esc-return path — so closing a sub-screen
     * with Esc lands the player back on this menu instead of dropping to the world.
     */
    public static void openMenu(ServerPlayerEntity sp, BlockPos pasturePos) {
        if (sp.getServer() == null) return;
        World world = sp.getServerWorld();
        BlockEntity be = world.getBlockEntity(pasturePos);
        if (!(be instanceof PokemonPastureBlockEntity pasture)) return;
        final PastureData pd = PastureRegistry.get(sp.getServer()).getOrCreate(world, pasturePos);
        // snapshot this pasture into the player's console (read-only remote view; INTERACTIVE_SPEC §3.2)
        PastureSnapshotStore.get(sp.getServer()).capture(sp.getUuid(), world, pasturePos, pd, pasture);
        sp.openHandledScreen(new ExtendedScreenHandlerFactory<PastureOpenData>() {
            @Override
            public Text getDisplayName() {
                return Text.literal(pd.name.isEmpty() ? "Pasture" : pd.name);
            }

            @Override
            public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
                return new PastureMenu(syncId, inv, pd.upgrades, pasturePos, pd.name);
            }

            @Override
            public PastureOpenData getScreenOpeningData(ServerPlayerEntity player) {
                return new PastureOpenData(pasturePos, pd.name, CobbreedingBridge.rosterOf(pasture, pd));
            }
        });
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack held = user.getStackInHand(hand);
        if (world.isClient) {
            CONSOLE_OPENER.run();   // client-only: open the owo-ui Notebook console (hook set in GreenerPasturesClient)
        } else if (user instanceof ServerPlayerEntity sp) {
            GpLog.i("notebook", "console_open", "player", sp.getUuid().toString());
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
