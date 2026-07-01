package com.greenerpastures.pasture.breeding;

import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.greenerpastures.notebook.PastureSnapshotStore;
import com.greenerpastures.pasture.breeding.gui.PastureMenu;
import com.greenerpastures.pasture.breeding.gui.PastureOpenData;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Pasture Wand — right-click a pasture to open its configuration GUI. The pasture's
 * {@link PastureData} is server-owned (persisted via {@link PastureRegistry}); the GUI is backed by
 * its upgrade inventory, and the mon roster is shipped to the client via {@link PastureOpenData}.
 */
public class PastureWand extends Item {

    public PastureWand(Settings settings) {
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
            return ActionResult.PASS;
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
     * Open the Greener Pastures wand menu for a pasture. Shared by the wand item (right-click) and the
     * {@code OpenPasturePayload} Esc-return path, so closing the Daemon/board with Esc lands the player
     * back on this menu instead of dropping out to the world.
     */
    public static void openMenu(ServerPlayerEntity sp, BlockPos pasturePos) {
        if (sp.getServer() == null) return;
        World world = sp.getServerWorld();
        BlockEntity be = world.getBlockEntity(pasturePos);
        if (!(be instanceof PokemonPastureBlockEntity pasture)) return;
        final PastureData pd = PastureRegistry.get(sp.getServer()).getOrCreate(world, pasturePos);

        // Notebook: snapshot this pasture into the player's console (read-only remote view; INTERACTIVE_SPEC §3.2).
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
}
