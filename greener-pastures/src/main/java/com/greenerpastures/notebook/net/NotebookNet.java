package com.greenerpastures.notebook.net;

import com.greenerpastures.buff.BuffConfig;
import com.greenerpastures.buff.BuffId;
import com.greenerpastures.buff.BuffResolver;
import com.greenerpastures.buff.BuffSetting;
import com.greenerpastures.buff.BuffSystem;
import com.greenerpastures.buff.DaemonBuffs;
import com.greenerpastures.buff.DaemonLoadout;
import com.greenerpastures.buff.ResolvedBuffs;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.DaemonItem;
import com.greenerpastures.economy.DarkEconomy;
import com.greenerpastures.economy.DataStore;
import com.greenerpastures.economy.GpItems;
import com.greenerpastures.notebook.NotebookStorage;
import com.greenerpastures.notebook.NotebookStore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Notebook console networking — the shared server↔client <b>sync layer</b> (NOTEBOOK_INTERACTIVE_SPEC §2).
 *
 * <p>C2S {@link NotebookRequestC2S} asks for a tab's data; the server replies with the always-on
 * {@link NotebookStatusS2C} (Data balance + GPU item-count + Daemon-on) and, later, per-tab payloads.
 * Registered from the common entrypoint so the codecs exist on both sides and the receiver runs server-side.
 */
public final class NotebookNet {
    private NotebookNet() {}

    public static void init() {
        PayloadTypeRegistry.playC2S().register(NotebookRequestC2S.ID, NotebookRequestC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(NotebookActionC2S.ID, NotebookActionC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookStatusS2C.ID, NotebookStatusS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookStorageS2C.ID, NotebookStorageS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookCompilerS2C.ID, NotebookCompilerS2C.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NotebookRequestC2S.ID, NotebookNet::onRequest);
        ServerPlayNetworking.registerGlobalReceiver(NotebookActionC2S.ID, NotebookNet::onAction);
    }

    private static void onRequest(NotebookRequestC2S payload, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        if (player.getServer() == null) return;
        player.getServer().execute(() -> {
            pushStatus(player);
            pushStorage(player);
            pushCompiler(player);
        });
    }

    private static void onAction(NotebookActionC2S p, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            switch (p.action()) {
                case NotebookActionC2S.PULL_ONE -> { pull(player, p.arg(), false); pushStorage(player); }
                case NotebookActionC2S.PULL_ID  -> { pull(player, p.arg(), true);  pushStorage(player); }
                case NotebookActionC2S.SET_BUFF -> { setBuff(player, p.arg(), p.amount()); pushCompiler(player); }
                case NotebookActionC2S.TOGGLE_DAEMON -> { toggleDaemon(player); pushCompiler(player); }
                default -> { }
            }
            pushStatus(player);
        });
    }

    /** Gather + send the status-bar figures for {@code player}. Also call this after any console action. */
    public static void pushStatus(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        long data = DataStore.get(server).balanceOf(player.getUuid());
        int gpu = countGpu(player);
        boolean daemonOn = anyDaemonOn(player);
        ServerPlayNetworking.send(player, new NotebookStatusS2C(data, gpu, daemonOn));
        GpLog.d("notebook", "status_push", "player", player.getUuid().toString(),
                "data", Long.toString(data), "gpu", Integer.toString(gpu), "daemonOn", Boolean.toString(daemonOn));
    }

    /** Send the player's Notebook item-storage snapshot (for the Harvester/Storage tab). */
    public static void pushStorage(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        NotebookStorage st = NotebookStore.get(server).storageOf(player.getUuid());
        ServerPlayNetworking.send(player, new NotebookStorageS2C(st.snapshot(), st.capacity()));
    }

    /** Withdraw from the player's Notebook storage into their inventory. {@code all=false} pulls one stack. */
    private static void pull(ServerPlayerEntity player, String itemId, boolean all) {
        MinecraftServer server = player.getServer();
        if (server == null || itemId == null || itemId.isEmpty()) return;
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == Items.AIR) return;
        NotebookStore store = NotebookStore.get(server);
        long have = store.storageOf(player.getUuid()).count(itemId);
        if (have <= 0) return;
        int maxStack = new ItemStack(item).getMaxCount();
        long want = all ? have : Math.min(maxStack, have);
        long removed = store.withdraw(player.getUuid(), itemId, want);
        long remaining = removed;
        while (remaining > 0) {
            int n = (int) Math.min(maxStack, remaining);
            player.getInventory().offerOrDrop(new ItemStack(item, n));
            remaining -= n;
        }
        GpLog.i("notebook", "pull", "player", player.getUuid().toString(),
                "item", itemId, "n", Long.toString(removed), "all", Boolean.toString(all));
    }

    // ── Compiler (Daemon) ─────────────────────────────────────────────────────────────────────────────

    /** Send the Compiler tab: the buff catalog + the first held Daemon's loadout + ON state + total drain. */
    public static void pushCompiler(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ItemStack daemon = firstDaemon(player);
        boolean has = !daemon.isEmpty();
        boolean on = has && DaemonItem.isOn(daemon);
        BuffConfig cfg = BuffSystem.config();
        Set<BuffId> supported = DaemonBuffs.supported();

        List<NotebookCompilerS2C.Buff> catalog = new ArrayList<>();
        for (BuffId b : supported) {
            BuffSetting s = cfg.settingOf(b);
            int cap = s.enabled() ? Math.min(s.maxTier(), 3) : 0;
            catalog.add(new NotebookCompilerS2C.Buff(b.id, b.label, b.category.name(), cap, s.costPerSec()));
        }
        catalog.sort(Comparator.comparing(NotebookCompilerS2C.Buff::category)
                .thenComparing(NotebookCompilerS2C.Buff::label));

        Map<String, Integer> installed = new HashMap<>();
        double drain = 0.0;
        if (has) {
            DaemonLoadout loadout = DaemonItem.loadoutOf(daemon);
            Map<BuffId, Integer> levels = loadout.toLevels();
            for (Map.Entry<BuffId, Integer> e : levels.entrySet()) installed.put(e.getKey().id, e.getValue());
            drain = BuffResolver.resolveLoadout(cfg, levels, supported).dataPerSec();
        }
        ServerPlayNetworking.send(player, new NotebookCompilerS2C(has, on, drain, catalog, installed));
    }

    private static void setBuff(ServerPlayerEntity player, String buffId, int tier) {
        ItemStack daemon = firstDaemon(player);
        if (daemon.isEmpty()) return;
        BuffId b = BuffId.byId(buffId);
        if (b == null || !DaemonBuffs.supported().contains(b)) return;
        BuffSetting s = BuffSystem.config().settingOf(b);
        int cap = s.enabled() ? Math.min(s.maxTier(), 3) : 0;
        int clamped = Math.max(0, Math.min(cap, tier));
        DaemonLoadout loadout = DaemonItem.loadoutOf(daemon).withLevel(b, clamped);
        daemon.set(DarkEconomy.DAEMON_LOADOUT, loadout);
        GpLog.i("notebook", "compile_set", "player", player.getUuid().toString(),
                "buff", buffId, "tier", Integer.toString(clamped));
    }

    private static void toggleDaemon(ServerPlayerEntity player) {
        ItemStack daemon = firstDaemon(player);
        if (daemon.isEmpty()) return;
        boolean next = !DaemonItem.isOn(daemon);
        DaemonItem.setOn(daemon, next);
        GpLog.i("notebook", "compile_toggle", "player", player.getUuid().toString(), "on", Boolean.toString(next));
    }

    private static ItemStack firstDaemon(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack s : inv.main) if (s.getItem() instanceof DaemonItem) return s;
        for (ItemStack s : inv.offHand) if (s.getItem() instanceof DaemonItem) return s;
        return ItemStack.EMPTY;
    }

    private static int countGpu(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int n = 0;
        for (ItemStack s : inv.main) if (s.isOf(GpItems.GPU)) n += s.getCount();
        for (ItemStack s : inv.offHand) if (s.isOf(GpItems.GPU)) n += s.getCount();
        return n;
    }

    private static boolean anyDaemonOn(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack s : inv.main) if (s.getItem() instanceof DaemonItem && DaemonItem.isOn(s)) return true;
        for (ItemStack s : inv.offHand) if (s.getItem() instanceof DaemonItem && DaemonItem.isOn(s)) return true;
        return false;
    }
}
