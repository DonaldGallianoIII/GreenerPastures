package com.greenerpastures.notebook.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import com.greenerpastures.egg.oracle.cull.EggCard;
import com.greenerpastures.egg.oracle.cull.EggReader;
import com.greenerpastures.goal.BreedingGoal;
import com.greenerpastures.goal.GoalProgress;
import com.greenerpastures.goal.GoalStore;
import com.greenerpastures.notebook.EggLog;
import com.greenerpastures.notebook.NotebookStorage;
import com.greenerpastures.notebook.NotebookStore;
import com.greenerpastures.notebook.PastureSnapshot;
import com.greenerpastures.notebook.PastureSnapshotStore;
import com.greenerpastures.pasture.breeding.Augments;
import com.greenerpastures.pasture.breeding.BreedingTier;
import com.greenerpastures.pasture.breeding.BreedingUpgradeItem;
import com.greenerpastures.pasture.breeding.GpComponents;
import com.greenerpastures.pasture.breeding.compiler.AugmentType;
import com.greenerpastures.pasture.breeding.CobbreedingBridge;
import com.greenerpastures.pasture.breeding.PastureClaim;
import com.greenerpastures.pasture.breeding.PastureData;
import com.greenerpastures.pasture.breeding.PastureRegistry;
import com.greenerpastures.pasture.breeding.gui.MonEntry;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    private static final Gson GSON = new Gson();

    public static void init() {
        PayloadTypeRegistry.playC2S().register(NotebookRequestC2S.ID, NotebookRequestC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(NotebookActionC2S.ID, NotebookActionC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookStatusS2C.ID, NotebookStatusS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookStorageS2C.ID, NotebookStorageS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookCompilerS2C.ID, NotebookCompilerS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookPasturesS2C.ID, NotebookPasturesS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookAugmenterS2C.ID, NotebookAugmenterS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookBioBankS2C.ID, NotebookBioBankS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookPastureConfigS2C.ID, NotebookPastureConfigS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(NotebookPastureActionC2S.ID, NotebookPastureActionC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(NotebookInvSwapC2S.ID, NotebookInvSwapC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookGraphS2C.ID, NotebookGraphS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(NotebookGraphSaveC2S.ID, NotebookGraphSaveC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookEggLogS2C.ID, NotebookEggLogS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookDashboardS2C.ID, NotebookDashboardS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookGoalsS2C.ID, NotebookGoalsS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(NotebookGoalC2S.ID, NotebookGoalC2S.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NotebookRequestC2S.ID, NotebookNet::onRequest);
        ServerPlayNetworking.registerGlobalReceiver(NotebookActionC2S.ID, NotebookNet::onAction);
        ServerPlayNetworking.registerGlobalReceiver(NotebookPastureActionC2S.ID, NotebookNet::onPastureAction);
        ServerPlayNetworking.registerGlobalReceiver(NotebookInvSwapC2S.ID, NotebookNet::onInvSwap);
        ServerPlayNetworking.registerGlobalReceiver(NotebookGraphSaveC2S.ID, NotebookNet::onGraphSave);
        ServerPlayNetworking.registerGlobalReceiver(NotebookGoalC2S.ID, NotebookNet::onGoal);
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
            pushEggLog(player);
            pushDashboard(player);
            pushGoals(player);
        });
    }

    /** Send the viewing player's recent egg-ingest feed (kept/voided + filter) + totals — the console Log view. */
    public static void pushEggLog(ServerPlayerEntity player) {
        List<NotebookEggLogS2C.Entry> out = new ArrayList<>();
        for (EggLog.Entry e : EggLog.recent(player.getUuid())) out.add(new NotebookEggLogS2C.Entry(e.species(), e.voided(), e.filter()));
        ServerPlayNetworking.send(player, new NotebookEggLogS2C(EggLog.kept(player.getUuid()), EggLog.voided(player.getUuid()), out));
    }

    /** Send the viewing player's live breeding analytics (eggs/shiny/kept/voided/Data/by-tier/spark) for the Dashboard. */
    public static void pushDashboard(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        long now = player.getServerWorld() != null ? player.getServerWorld().getTime() : 0L;
        long[] t = EggLog.totals(id);
        JsonObject o = new JsonObject();
        o.addProperty("laid", t[0]); o.addProperty("shiny", t[1]); o.addProperty("procShiny", t[2]); o.addProperty("dataEarned", t[3]);
        o.addProperty("kept", EggLog.kept(id)); o.addProperty("voided", EggLog.voided(id));
        JsonObject tiers = new JsonObject();
        EggLog.byTier(id).forEach((k, v) -> tiers.addProperty(k, v));
        o.add("byTier", tiers);
        JsonArray spark = new JsonArray();
        for (int v : EggLog.spark(id, now)) spark.add(v);
        o.add("spark", spark);
        ServerPlayNetworking.send(player, new NotebookDashboardS2C(GSON.toJson(o)));
    }

    /** Send the viewing player's active breeding goal + live progress for the Dashboard's Goal panel. */
    public static void pushGoals(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        BreedingGoal goal = GoalStore.goalOf(id);
        JsonObject o = new JsonObject();
        if (goal == null) { o.addProperty("present", false); ServerPlayNetworking.send(player, new NotebookGoalsS2C(GSON.toJson(o))); return; }
        GoalProgress pr = GoalStore.progressOf(id);
        o.addProperty("present", true);
        o.addProperty("species", goal.species() == null ? "" : goal.species());
        o.addProperty("shiny", goal.shiny() == null ? -1 : (goal.shiny() ? 1 : 0));
        o.addProperty("minPerfect", goal.minPerfectIvs());
        o.addProperty("minIvTotal", goal.minIvTotal());
        o.addProperty("count", goal.count());
        o.addProperty("describe", goal.describe());
        o.addProperty("checked", pr.checked());
        o.addProperty("matched", pr.matched());
        o.addProperty("bestIvTotal", pr.bestIvTotal());
        o.addProperty("remaining", pr.remaining(goal));
        o.addProperty("reached", pr.reached(goal));
        ServerPlayNetworking.send(player, new NotebookGoalsS2C(GSON.toJson(o)));
    }

    /** Set or clear the player's breeding goal from the console's Goal panel. */
    private static void onGoal(NotebookGoalC2S p, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            try {
                JsonObject o = GSON.fromJson(p.json() == null ? "{}" : p.json(), JsonObject.class);
                if (o == null) return;
                if (o.has("clear") && o.get("clear").getAsBoolean()) {
                    GoalStore.clear(player.getUuid());
                } else {
                    String species = o.has("species") && !o.get("species").isJsonNull() ? o.get("species").getAsString() : null;
                    int sh = o.has("shiny") ? o.get("shiny").getAsInt() : -1;
                    Boolean shiny = sh == 1 ? Boolean.TRUE : sh == 0 ? Boolean.FALSE : null;
                    int mp = o.has("minPerfect") ? o.get("minPerfect").getAsInt() : 0;
                    int mt = o.has("minIvTotal") ? o.get("minIvTotal").getAsInt() : 0;
                    int cn = o.has("count") ? o.get("count").getAsInt() : 1;
                    GoalStore.set(player.getUuid(), new BreedingGoal(species, shiny, mp, mt, cn));
                }
                pushGoals(player);
            } catch (Exception ignored) { }
        });
    }

    private static void onAction(NotebookActionC2S p, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            switch (p.action()) {
                case NotebookActionC2S.PULL_ONE   -> { pull(player, p.arg(), 0); pushStorage(player); }
                case NotebookActionC2S.PULL_STACK -> { pull(player, p.arg(), 1); pushStorage(player); }
                case NotebookActionC2S.PULL_ID    -> { pull(player, p.arg(), 2); pushStorage(player); }
                case NotebookActionC2S.SET_BUFF -> { setBuff(player, p.arg(), p.amount()); pushCompiler(player); }
                case NotebookActionC2S.TOGGLE_DAEMON -> { toggleDaemon(player); pushCompiler(player); }
                case NotebookActionC2S.APPLY_AUGMENT -> { applyAugment(player, p.arg()); pushAugmenter(player); }
                case NotebookActionC2S.REMOVE_AUGMENT -> { removeAugment(player, p.arg()); pushAugmenter(player); }
                case NotebookActionC2S.WITHDRAW -> { withdrawEgg(player, p.amount()); pushBiobank(player); }
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

    /** Take from Notebook storage into the player's inventory. mode: 0 = one item · 1 = one stack · 2 = all.
     *  <b>Space-aware</b>: inserts into the inventory first and removes from storage ONLY what actually fit, so a
     *  full inventory leaves items in the Notebook — nothing is ever dropped in-world or destroyed (Deuce, 2026-07-01). */
    private static void pull(ServerPlayerEntity player, String itemId, int mode) {
        MinecraftServer server = player.getServer();
        if (server == null || itemId == null || itemId.isEmpty()) return;
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == Items.AIR) return;
        NotebookStore store = NotebookStore.get(server);
        long have = store.storageOf(player.getUuid()).count(itemId);
        if (have <= 0) return;
        int maxStack = new ItemStack(item).getMaxCount();
        long want = switch (mode) {
            case 0 -> 1L;
            case 1 -> Math.min(maxStack, have);
            default -> have;
        };
        want = Math.min(want, have);
        long gave = 0;
        while (want - gave >= 1) {
            int n = (int) Math.min(maxStack, want - gave);
            ItemStack stack = new ItemStack(item, n);
            player.getInventory().insertStack(stack);
            gave += n - stack.getCount();       // stack.getCount() = what did NOT fit
            if (!stack.isEmpty()) break;         // inventory full → stop, leave the rest in storage
        }
        if (gave > 0) store.withdraw(player.getUuid(), itemId, gave);
        GpLog.i("notebook", "pull", "player", player.getUuid().toString(),
                "item", itemId, "n", Long.toString(gave), "mode", Integer.toString(mode));
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

    /** Withdraw the egg at {@code flatIndex} (the console's flattened BioBank order) back into the player's inventory. */
    private static void withdrawEgg(ServerPlayerEntity player, int flatIndex) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (player.getInventory().getEmptySlot() < 0) {   // no room — leave the egg in the BioBank (never destroy)
            GpLog.i("notebook", "biobank_full", "player", player.getUuid().toString());
            return;
        }
        ItemStack egg = BioBankStore.get(server).withdraw(player.getUuid(), flatIndex);
        if (!egg.isEmpty()) {
            player.getInventory().insertStack(egg);
            GpLog.i("notebook", "biobank_withdraw", "player", player.getUuid().toString(), "index", Integer.toString(flatIndex));
        }
    }

    // ── pasture config (the React right-click-a-pasture screen; replaces the owo PastureScreen) ───────────────

    /** Build + push the focused pasture's full editable config (name · tier · link · maxPairs · roster). */
    public static void pushPastureConfig(ServerPlayerEntity player, BlockPos pos) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerWorld world = player.getServerWorld();
        if (world == null || !(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity pasture)) return;
        PastureData pd = PastureRegistry.get(server).getOrCreate(world, pos);
        // Refresh this pasture's Pastures-tab snapshot too, so a rename / edit shows up there (Deuce, 2026-07-01).
        PastureSnapshotStore.get(server).capture(player.getUuid(), world, pos, pd, pasture);
        BreedingTier tier = pd.tier();
        boolean linked = pd.owner != null && pd.owner.equals(player.getUuid());
        List<MonEntry> roster = CobbreedingBridge.rosterOf(pasture, pd);
        ServerPlayNetworking.send(player, new NotebookPastureConfigS2C(
                pos.asLong(), pd.name, tier == null ? "" : tier.name(), linked, tier == null ? 0 : tier.maxPairs, roster));
        ServerPlayNetworking.send(player, new NotebookGraphS2C(pos.asLong(), pd.graphJson == null ? "" : pd.graphJson));
    }

    private static void onPastureAction(NotebookPastureActionC2S p, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            ServerWorld world = player.getServerWorld();
            if (world == null) return;
            BlockPos pos = BlockPos.fromLong(p.pos());
            if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0 * 64.0) return;
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity)) return;
            PastureRegistry reg = PastureRegistry.get(server);
            switch (p.action()) {
                case NotebookPastureActionC2S.NAME -> {
                    String name = p.arg() == null ? "" : p.arg();
                    if (name.length() > 64) name = name.substring(0, 64);
                    reg.setName(world, pos, name);
                }
                case NotebookPastureActionC2S.PAIRINGS -> reg.setPairings(world, pos, sanitizePairings(p.pairings()));
                case NotebookPastureActionC2S.CLAIM -> {
                    PastureData pd = reg.getOrCreate(world, pos);
                    PastureClaim.Result r = PastureClaim.toggle(pd.owner, player.getUuid());
                    if (r.changed()) {
                        pd.owner = r.owner();
                        reg.markDirty();
                        player.sendMessage(Text.literal(r.outcome() == PastureClaim.Outcome.CLAIMED
                                ? "§a[Greener Pastures]§r Linked — this pasture's drops, eggs & outputs collect into your Notebook (you pay its tether cost)."
                                : "§a[Greener Pastures]§r Unlinked — its outputs no longer collect to you."), false);
                    } else {
                        player.sendMessage(Text.literal("§c[Greener Pastures]§r This pasture is owned by someone else."), false);
                    }
                }
                case NotebookPastureActionC2S.KERNEL -> {
                    int src = -1; try { src = Integer.parseInt(p.arg()); } catch (Exception ignored) { }
                    toggleKernel(player, reg.getOrCreate(world, pos), src); reg.markDirty();
                }
                default -> { }
            }
            pushPastureConfig(player, pos);
            pushStatus(player);
        });
    }

    /** Slot the first Kernel from the player's inventory into the pasture's upgrade slot, or return the slotted one. */
    private static void toggleKernel(ServerPlayerEntity player, PastureData pd, int srcSlot) {
        ItemStack cur = pd.upgrades.getStack(0);
        if (cur.getItem() instanceof BreedingUpgradeItem) {
            ItemStack out = cur.copy();
            pd.upgrades.setStack(0, ItemStack.EMPTY);
            player.getInventory().insertStack(out);
            if (!out.isEmpty()) pd.upgrades.setStack(0, out);   // inventory full → keep it slotted (never destroy)
            return;
        }
        PlayerInventory inv = player.getInventory();
        if (srcSlot >= 0 && srcSlot < inv.main.size() && inv.main.get(srcSlot).getItem() instanceof BreedingUpgradeItem) {
            pd.upgrades.setStack(0, inv.main.get(srcSlot).split(1));   // the specific Kernel the player was holding
            return;
        }
        for (int i = 0; i < inv.size(); i++) {                         // else the first Kernel found
            if (inv.getStack(i).getItem() instanceof BreedingUpgradeItem) { pd.upgrades.setStack(0, inv.getStack(i).split(1)); break; }
        }
    }

    /** Swap two of the player's own main-inventory slots (grab-and-move in the Notebook's native inventory). */
    private static void onInvSwap(NotebookInvSwapC2S p, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            var main = player.getInventory().main;
            int a = p.a(), b = p.b();
            if (a < 0 || b < 0 || a >= main.size() || b >= main.size() || a == b) return;
            ItemStack tmp = main.get(a);
            main.set(a, main.get(b));
            main.set(b, tmp);
            player.getInventory().markDirty();
        });
    }

    /** Save the focused pasture's Daemon graph (the whole JSON authored in the React node editor). Validates reach
     *  + a size cap, stores it on {@link PastureData}. Not echoed back — the editor is the authority while open. */
    private static void onGraphSave(NotebookGraphSaveC2S p, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            ServerWorld world = player.getServerWorld();
            if (world == null) return;
            BlockPos pos = BlockPos.fromLong(p.pos());
            if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0 * 64.0) return;
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity)) return;
            String json = p.json() == null ? "" : p.json();
            if (json.length() > 65536) return;   // sanity cap — a pasture graph is never this big
            PastureRegistry reg = PastureRegistry.get(server);
            PastureData pd = reg.getOrCreate(world, pos);
            pd.graphJson = json;
            reg.markDirty();
            GpLog.i("notebook", "graph_save", "player", player.getUuid().toString(),
                    "pos", pos.toShortString(), "len", Integer.toString(json.length()));
        });
    }

    private static Map<UUID, Integer> sanitizePairings(Map<UUID, Integer> raw) {
        Map<UUID, Integer> clean = new HashMap<>();
        if (raw == null) return clean;
        for (Map.Entry<UUID, Integer> e : raw.entrySet()) {
            if (clean.size() >= 64) break;
            if (e.getKey() != null && e.getValue() != null && e.getValue() >= 1 && e.getValue() <= 8) clean.put(e.getKey(), e.getValue());
        }
        return clean;
    }

    /** Send the player's BioBank contents: one entry per stored egg (species · shiny · IV total · # perfect). */
    public static void pushBiobank(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        BioBankData bank = BioBankStore.get(server).get(player.getUuid());
        List<NotebookBioBankS2C.Entry> entries = new ArrayList<>();
        if (bank != null) {
            for (String species : bank.speciesCounts().keySet()) {
                for (ItemStack egg : bank.entries(species)) {
                    EggCard c = EggReader.card(egg);
                    if (c != null) {
                        entries.add(new NotebookBioBankS2C.Entry(species, c.shiny(), c.ivs(), c.evs(),
                                c.nature(), c.gender(), c.ability()));
                    } else {
                        entries.add(new NotebookBioBankS2C.Entry(species, false, new int[6], new int[6], "", "", ""));
                    }
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
