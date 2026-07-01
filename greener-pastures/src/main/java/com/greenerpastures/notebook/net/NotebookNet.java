package com.greenerpastures.notebook.net;

import com.greenerpastures.biobank.BioBankData;
import com.greenerpastures.biobank.BioBankStore;
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
import com.greenerpastures.egg.oracle.cull.EggInfo;
import com.greenerpastures.egg.oracle.cull.EggReader;
import com.greenerpastures.notebook.NotebookStorage;
import com.greenerpastures.notebook.NotebookStore;
import com.greenerpastures.notebook.PastureSnapshot;
import com.greenerpastures.notebook.PastureSnapshotStore;
import com.greenerpastures.pasture.breeding.Augments;
import com.greenerpastures.pasture.breeding.BreedingTier;
import com.greenerpastures.pasture.breeding.BreedingUpgradeItem;
import com.greenerpastures.pasture.breeding.GpComponents;
import com.greenerpastures.pasture.breeding.compiler.AugmentType;
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
import java.util.function.Consumer;

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
        PayloadTypeRegistry.playS2C().register(NotebookPasturesS2C.ID, NotebookPasturesS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookAugmenterS2C.ID, NotebookAugmenterS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookBioBankS2C.ID, NotebookBioBankS2C.CODEC);
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
            pushPastures(player);
            pushAugmenter(player);
            pushBiobank(player);
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
                case NotebookActionC2S.APPLY_AUGMENT -> { applyAugment(player, p.arg()); pushAugmenter(player); }
                case NotebookActionC2S.REMOVE_AUGMENT -> { removeAugment(player, p.arg()); pushAugmenter(player); }
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

    // ── Pastures (read-only snapshots) ──────────────────────────────────────────────────────────────────

    /** Send the player's tracked pasture snapshots (captured when they opened each pasture in-world). */
    public static void pushPastures(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        List<PastureSnapshot> snaps = PastureSnapshotStore.get(server).snapshotsOf(player.getUuid());
        ServerPlayNetworking.send(player, new NotebookPasturesS2C(snaps));
    }

    // ── Kernel Augmenter (slot model; GPU/Data cost DEFERRED per §7.5) ──────────────────────────────────

    /** A found Kernel + a writer that puts a replacement stack back in its inventory slot (apply returns a copy). */
    private record KernelRef(ItemStack stack, Consumer<ItemStack> writer) {}

    private static KernelRef firstKernel(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.main.size(); i++) {
            if (inv.main.get(i).getItem() instanceof BreedingUpgradeItem) {
                final int idx = i;
                return new KernelRef(inv.main.get(i), ns -> inv.main.set(idx, ns));
            }
        }
        for (int i = 0; i < inv.offHand.size(); i++) {
            if (inv.offHand.get(i).getItem() instanceof BreedingUpgradeItem) {
                final int idx = i;
                return new KernelRef(inv.offHand.get(i), ns -> inv.offHand.set(idx, ns));
            }
        }
        return null;
    }

    private static int slotCost(AugmentType at) {
        return 1;   // uniform 1 slot per augment for v1 (per-augment costs arrive with the economy pass)
    }

    private static int slotsUsed(ItemStack kernel) {
        int n = 0;
        for (AugmentType at : AugmentType.values()) if (at.installedOn(kernel)) n += slotCost(at);
        return n;
    }

    private static AugmentType augmentType(String name) {
        try {
            return AugmentType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Send the Augmenter tab: the first held Kernel's tier + slot usage/capacity + the augment catalog. */
    public static void pushAugmenter(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        KernelRef ref = firstKernel(player);
        boolean has = ref != null;
        String tierLabel = "no Kernel";
        int slotCap = 0, used = 0;
        List<NotebookAugmenterS2C.Aug> catalog = new ArrayList<>();
        if (has) {
            BreedingTier tier = ((BreedingUpgradeItem) ref.stack().getItem()).tier();
            tierLabel = tier.name();
            slotCap = tier.slots;
            used = slotsUsed(ref.stack());
            for (AugmentType at : AugmentType.values()) {
                catalog.add(new NotebookAugmenterS2C.Aug(at.name(), at.effectSummary(), slotCost(at),
                        at.installedOn(ref.stack())));
            }
        }
        ServerPlayNetworking.send(player, new NotebookAugmenterS2C(has, tierLabel, used, slotCap, catalog));
    }

    private static void applyAugment(ServerPlayerEntity player, String typeName) {
        KernelRef ref = firstKernel(player);
        if (ref == null) return;
        AugmentType at = augmentType(typeName);
        if (at == null || !at.appliesTo(ref.stack()) || at.installedOn(ref.stack())) return;   // no-dupe
        BreedingTier tier = ((BreedingUpgradeItem) ref.stack().getItem()).tier();
        if (slotsUsed(ref.stack()) + slotCost(at) > tier.slots) return;   // slot gate (GPU/Data cost deferred)
        ref.writer().accept(at.apply(ref.stack()));
        GpLog.i("notebook", "augment_apply", "player", player.getUuid().toString(), "type", typeName);
    }

    private static void removeAugment(ServerPlayerEntity player, String typeName) {
        KernelRef ref = firstKernel(player);
        if (ref == null) return;
        AugmentType at = augmentType(typeName);
        if (at == null || !at.appliesTo(ref.stack())) return;
        Augments a = ref.stack().get(GpComponents.AUGMENTS);
        if (a == null || a.level(at.function) <= 0) return;
        ItemStack out = ref.stack().copy();
        out.set(GpComponents.AUGMENTS, a.withLevel(at.function, 0));
        ref.writer().accept(out);
        GpLog.i("notebook", "augment_remove", "player", player.getUuid().toString(), "type", typeName);
    }

    // ── BioBank (per-player; browse-only in 6a) ─────────────────────────────────────────────────────────

    /** Send the player's BioBank contents: one entry per stored egg (species · shiny · IV total · # perfect). */
    public static void pushBiobank(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        BioBankData bank = BioBankStore.get(server).get(player.getUuid());
        List<NotebookBioBankS2C.Entry> entries = new ArrayList<>();
        if (bank != null) {
            for (String species : bank.speciesCounts().keySet()) {
                for (ItemStack egg : bank.entries(species)) {
                    EggInfo info = EggReader.read(egg);
                    boolean shiny = info != null && info.shiny();
                    int ivTotal = info != null ? info.ivTotal() : 0;
                    int perfect = info != null ? info.perfectCount() : 0;
                    entries.add(new NotebookBioBankS2C.Entry(species, shiny, ivTotal, perfect));
                }
            }
        }
        ServerPlayNetworking.send(player, new NotebookBioBankS2C(entries.size(), entries));
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
