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
import com.greenerpastures.notebook.AugmentArg;
import com.greenerpastures.notebook.EggLog;
import com.greenerpastures.notebook.NotebookStorage;
import com.greenerpastures.notebook.NotebookStore;
import com.greenerpastures.notebook.PastureHealth;
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

    /** Per-player last prefetch-sweep time (ms) — the console poll re-warms the client cache at most 1×/min. */
    private static final Map<UUID, Long> lastPrefetch = new HashMap<>();

    /** Per-player, per-channel LAST payload actually sent — the server-side change gate (perf-audit R3 S2).
     *  The console poll rebuilds every channel each second; this drops identical payloads BEFORE the packet
     *  layer, so unchanged data pays no encode, no zlib, no client decode + deep-equals. Records compare
     *  structurally, so equality is exact (no hash-collision staleness). Server thread only. */
    private static final Map<UUID, Map<String, Object>> lastPush = new HashMap<>();

    /** True (and remembers {@code payload}) when it differs from the last one sent on {@code channel}. */
    private static boolean changed(ServerPlayerEntity player, String channel, Object payload) {
        Map<String, Object> m = lastPush.computeIfAbsent(player.getUuid(), u -> new HashMap<>());
        Object prev = m.put(channel, payload);
        return prev == null || !prev.equals(payload);
    }

    /** Send {@code payload} only if it differs from the last send on {@code channel} (see {@link #lastPush}). */
    private static void sendGated(ServerPlayerEntity player, String channel, net.minecraft.network.packet.CustomPayload payload) {
        if (changed(player, channel, payload)) ServerPlayNetworking.send(player, payload);
    }

    /** Player left — drop their gate/prefetch state (unbounded-per-player maps on 24/7 servers, R3 #5/F11). */
    public static void onDisconnect(UUID player) {
        lastPush.remove(player);
        lastPrefetch.remove(player);
        augTargetSlot.remove(player);
        daemonTargetSlot.remove(player);
    }

    /** Reset per-server-session state — called on SERVER_STARTED (a new SP world shares the JVM). */
    public static void resetSession() { lastPrefetch.clear(); lastPush.clear(); augTargetSlot.clear(); daemonTargetSlot.clear(); }

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
        PayloadTypeRegistry.playS2C().register(NotebookNotifsS2C.ID, NotebookNotifsS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookDashboardS2C.ID, NotebookDashboardS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookGoalsS2C.ID, NotebookGoalsS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(NotebookGoalC2S.ID, NotebookGoalC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookPastureExtraS2C.ID, NotebookPastureExtraS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookAugmenterMetaS2C.ID, NotebookAugmenterMetaS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookRitualsS2C.ID, NotebookRitualsS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookSpecimensS2C.ID, NotebookSpecimensS2C.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(NotebookRequestC2S.ID, NotebookNet::onRequest);
        ServerPlayNetworking.registerGlobalReceiver(NotebookActionC2S.ID, NotebookNet::onAction);
        ServerPlayNetworking.registerGlobalReceiver(NotebookPastureActionC2S.ID, NotebookNet::onPastureAction);
        ServerPlayNetworking.registerGlobalReceiver(NotebookInvSwapC2S.ID, NotebookNet::onInvSwap);
        ServerPlayNetworking.registerGlobalReceiver(NotebookGraphSaveC2S.ID, NotebookNet::onGraphSave);
        ServerPlayNetworking.registerGlobalReceiver(NotebookGoalC2S.ID, NotebookNet::onGoal);
        // Warm the client's pasture-config cache at join, so the FIRST right-click of any known pasture renders
        // instantly from cache (stale-while-revalidate) instead of showing a loading state.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> prefetchConfigs(handler.player)));
        // Departed players must not accumulate gate/prefetch state forever on a 24/7 server (R3 #5/F11).
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                onDisconnect(handler.getPlayer().getUuid()));
    }

    /** Push every known (snapshotted) pasture's config + graph for {@code player} — pre-warms the client cache.
     *  Current dimension + loaded chunks only (a live roster needs the block entity); the 1-min console-poll sweep
     *  self-heals any pasture whose chunk wasn't loaded yet. Capped for sanity. */
    public static void prefetchConfigs(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        ServerWorld world = player.getServerWorld();
        if (server == null || world == null) return;
        String dim = world.getRegistryKey().getValue().toString();
        int sent = 0;
        for (com.greenerpastures.notebook.PastureSnapshot s : PastureSnapshotStore.get(server).snapshotsOf(player.getUuid())) {
            if (!dim.equals(s.dim())) continue;
            BlockPos pos = BlockPos.fromLong(s.pos());
            if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (!(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity)) continue;
            pushPastureConfig(player, pos, false);   // prefetch shape: no snapshot capture, no per-mon stats (R3 F6)
            if (++sent >= 16) break;
        }
        if (sent > 0) GpLog.d("notebook", "prefetch", "player", player.getUuid().toString(), "n", Integer.toString(sent));
    }

    private static void onRequest(NotebookRequestC2S payload, ServerPlayNetworking.Context ctx) {
        ServerPlayerEntity player = ctx.player();
        if (player.getServer() == null) return;
        player.getServer().execute(() -> {
            try (var span = com.greenerpastures.core.GpProf.begin("net.push_batch")) {
                pushStatus(player);
                pushStorage(player);
                pushCompiler(player);
                pushPastures(player);
                pushAugmenter(player);
                pushBiobank(player);
                pushEggLog(player);
                pushDashboard(player);
                pushGoals(player);
                pushNotifs(player);
                pushRituals(player);
                pushSpecimens(player);
                long nowMs = System.currentTimeMillis();
                Long last = lastPrefetch.get(player.getUuid());
                if (last == null || nowMs - last > 60_000L) {   // re-warm the pasture-config cache at most 1×/min
                    lastPrefetch.put(player.getUuid(), nowMs);
                    prefetchConfigs(player);
                }
            }
        });
    }

    /** Send the viewing player's recent egg-ingest feed (kept/voided + filter) + totals — the console Log view. */
    public static void pushEggLog(ServerPlayerEntity player) {
        List<NotebookEggLogS2C.Entry> out = new ArrayList<>();
        for (EggLog.Entry e : EggLog.recent(player.getUuid())) out.add(new NotebookEggLogS2C.Entry(e.species(), e.voided(), e.filter()));
        sendGated(player, "egglog", new NotebookEggLogS2C(EggLog.kept(player.getUuid()), EggLog.voided(player.getUuid()), out));
    }

    /** Send the viewing player's Inbox (dismissible notifications — catch-up pings etc.) for the Inbox tab. */
    public static void pushNotifs(ServerPlayerEntity player) {
        JsonArray notes = new JsonArray();
        for (com.greenerpastures.notify.Inbox.Note n : com.greenerpastures.notify.Inbox.notesOf(player.getUuid())) {
            JsonObject o = new JsonObject();
            o.addProperty("id", n.id());
            o.addProperty("icon", n.icon());
            o.addProperty("text", n.text());
            o.addProperty("t", n.atMs());
            notes.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("notes", notes);
        sendGated(player, "notifs", new NotebookNotifsS2C(GSON.toJson(root)));
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
        JsonObject methods = new JsonObject();
        CobbreedingBridge.shinyMethods().forEach((k, v) -> methods.addProperty(k, v));
        o.add("shinyMethods", methods);   // {always, crystal, masuda} multipliers → the shiny-breeding indicator
        sendGated(player, "dashboard", new NotebookDashboardS2C(GSON.toJson(o)));
    }

    /** Send the viewing player's active breeding goal + live progress for the Dashboard's Goal panel. */
    public static void pushGoals(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        BreedingGoal goal = GoalStore.goalOf(id);
        JsonObject o = new JsonObject();
        if (goal == null) { o.addProperty("present", false); sendGated(player, "goals", new NotebookGoalsS2C(GSON.toJson(o))); return; }
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
        sendGated(player, "goals", new NotebookGoalsS2C(GSON.toJson(o)));
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
                    if (species != null && !CobbreedingBridge.isSpecies(species)) {   // reject a fake species (would never match)
                        player.sendMessage(Text.literal("§c[Greener Pastures]§r No such species: \"" + species + "\" — goal not set."), false);
                        return;
                    }
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
                case NotebookActionC2S.WRITE_DISK -> { writeDisk(player, p.arg()); pushStorage(player); }
                case NotebookActionC2S.RITUAL_PULL -> { ritualPull(player, p.arg(), p.amount()); pushRituals(player); }
                case NotebookActionC2S.CORRUPT_KERNEL -> { corruptKernel(player); pushAugmenter(player); }
                case NotebookActionC2S.SET_KERNEL_TARGET -> {
                    if (kernelAt(player, p.amount()) != null) augTargetSlot.put(player.getUuid(), p.amount());
                    pushAugmenter(player);
                }
                case NotebookActionC2S.SET_DAEMON_TARGET -> {
                    if (!daemonAt(player, p.amount()).isEmpty()) daemonTargetSlot.put(player.getUuid(), p.amount());
                    pushCompiler(player); pushAugmenter(player);
                }
                case NotebookActionC2S.RENAME_HELD_KERNEL -> { renameHeldKernel(player, p.arg()); pushAugmenter(player); }
                case NotebookActionC2S.COMPRESS_MON -> { compressMon(player, p.amount()); pushSpecimens(player); }
                case NotebookActionC2S.DISMISS_NOTE -> {
                    if ("all".equals(p.arg())) com.greenerpastures.notify.Inbox.dismissAll(player.getUuid());
                    else try { com.greenerpastures.notify.Inbox.dismiss(player.getUuid(), Long.parseLong(p.arg())); } catch (NumberFormatException ignored) { }
                    pushNotifs(player);
                }
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
        NotebookStatusS2C p = new NotebookStatusS2C(data, gpu, daemonOn);
        if (!changed(player, "status", p)) return;   // unchanged → no packet, no status_push log spam
        ServerPlayNetworking.send(player, p);
        GpLog.d("notebook", "status_push", "player", player.getUuid().toString(),
                "data", Long.toString(data), "gpu", Integer.toString(gpu), "daemonOn", Boolean.toString(daemonOn));
    }

    /** Send the player's Notebook item-storage snapshot (for the Harvester/Storage tab). */
    public static void pushStorage(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        NotebookStorage st = NotebookStore.get(server).storageOf(player.getUuid());
        sendGated(player, "storage", new NotebookStorageS2C(st.snapshot(), st.capacity()));
    }

    /** Take from Notebook storage into the player's inventory. mode: 0 = one item · 1 = one stack · 2 = all.
     *  <b>Space-verified + manual placement</b> (Deuce, 2026-07-03): we count the MAIN inventory's real capacity
     *  for this item and place stacks into slots ourselves — never via {@code insertStack}, whose return other
     *  mods can mixin-hijack (a backpack "absorbed" 3k ink sacs by reporting fit-into-nowhere). Storage is
     *  debited only for what we physically placed, so nothing can ever be destroyed. */
    private static void pull(ServerPlayerEntity player, String itemId, int mode) {
        MinecraftServer server = player.getServer();
        if (server == null || itemId == null || itemId.isEmpty()) return;
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == Items.AIR) return;
        NotebookStore store = NotebookStore.get(server);
        long have = store.storageOf(player.getUuid()).count(itemId);
        if (have <= 0) return;
        var main = player.getInventory().main;
        ItemStack probe = new ItemStack(item);
        int maxStack = probe.getMaxCount();

        long capacity = 0;                                   // real room in MAIN slots only — counted by us
        for (ItemStack s : main) {
            if (s.isEmpty()) capacity += maxStack;
            else if (ItemStack.areItemsAndComponentsEqual(s, probe)) capacity += Math.max(0, s.getMaxCount() - s.getCount());
        }
        long want = switch (mode) {
            case 0 -> 1L;
            case 1 -> Math.min(maxStack, have);
            default -> have;
        };
        want = Math.min(Math.min(want, have), capacity);
        if (want <= 0) {                                     // no room → refuse loudly, storage untouched
            player.sendMessage(net.minecraft.text.Text.literal("§c[Greener Pastures]§r Inventory full — nothing pulled."), false);
            GpLog.i("notebook", "pull_full", "player", player.getUuid().toString(), "item", itemId);
            return;
        }
        long placed = 0;
        for (int i = 0; i < main.size() && placed < want; i++) {   // top up matching partials first
            ItemStack s = main.get(i);
            if (s.isEmpty() || !ItemStack.areItemsAndComponentsEqual(s, probe)) continue;
            int add = (int) Math.min(s.getMaxCount() - s.getCount(), want - placed);
            if (add > 0) { s.increment(add); placed += add; }
        }
        for (int i = 0; i < main.size() && placed < want; i++) {   // then fill empty slots
            if (!main.get(i).isEmpty()) continue;
            int add = (int) Math.min(maxStack, want - placed);
            main.set(i, new ItemStack(item, add));
            placed += add;
        }
        player.getInventory().markDirty();
        if (placed > 0) store.withdraw(player.getUuid(), itemId, placed);
        GpLog.i("notebook", "pull", "player", player.getUuid().toString(),
                "item", itemId, "n", Long.toString(placed), "capacity", Long.toString(capacity), "mode", Integer.toString(mode));
    }

    // ── Compiler (Daemon) ─────────────────────────────────────────────────────────────────────────────

    /** Send the Compiler tab: the buff catalog + the first held Daemon's loadout + ON state + total drain. */
    public static void pushCompiler(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ItemStack daemon = targetDaemon(player);
        boolean has = !daemon.isEmpty();
        boolean on = has && DaemonItem.isOn(daemon);
        BuffConfig cfg = BuffSystem.config();
        Set<BuffId> supported = DaemonBuffs.supported();

        List<NotebookCompilerS2C.Buff> catalog = new ArrayList<>();
        for (BuffId b : supported) {
            BuffSetting s = cfg.settingOf(b);
            int cap = s.enabled() ? Math.min(s.maxTier(), 3) : 0;
            catalog.add(new NotebookCompilerS2C.Buff(b.id, b.label, b.category.name(), cap, s.costPerSec(), GPU_PER_BUFF_TIER));
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
        sendGated(player, "compiler", new NotebookCompilerS2C(has, on, drain, catalog, installed));
    }

    private static void setBuff(ServerPlayerEntity player, String buffId, int tier) {
        ItemStack daemon = targetDaemon(player);
        if (daemon.isEmpty()) return;
        BuffId b = BuffId.byId(buffId);
        if (b == null || !DaemonBuffs.supported().contains(b)) return;
        BuffSetting s = BuffSystem.config().settingOf(b);
        int cap = s.enabled() ? Math.min(s.maxTier(), 3) : 0;
        int clamped = Math.max(0, Math.min(cap, tier));
        int cur = DaemonItem.loadoutOf(daemon).toLevels().getOrDefault(b, 0);
        int gpuSpent = 0;
        if (clamped > cur) {                                   // tiering UP costs GPU; down is free, never refunded
            gpuSpent = (clamped - cur) * GPU_PER_BUFF_TIER;
            if (!consumeGpu(player, gpuSpent)) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Not enough GPU — need "
                        + gpuSpent + ", you have " + countGpu(player) + "."), false);
                return;
            }
        }
        DaemonLoadout loadout = DaemonItem.loadoutOf(daemon).withLevel(b, clamped);
        daemon.set(DarkEconomy.DAEMON_LOADOUT, loadout);
        GpLog.i("notebook", "compile_set", "player", player.getUuid().toString(),
                "buff", buffId, "tier", Integer.toString(clamped), "gpu", Integer.toString(gpuSpent));
    }

    private static void toggleDaemon(ServerPlayerEntity player) {
        ItemStack daemon = targetDaemon(player);
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
        // #37 — per-snapshot ⚠ badge flags, from registry-side state (works for unloaded chunks; parent/bank
        // checks need the live block entity and only run for loaded ones).
        PastureRegistry reg = PastureRegistry.get(server);
        Map<String, ServerWorld> dims = new HashMap<>();   // dim string → world, resolved ONCE per push (R3 F4)
        for (ServerWorld w : server.getWorlds()) dims.put(w.getRegistryKey().getValue().toString(), w);
        JsonObject health = new JsonObject();
        try (var span = com.greenerpastures.core.GpProf.begin("net.health_pass")) {
            for (PastureSnapshot s : snaps) {
                BlockPos pos = BlockPos.fromLong(s.pos());
                PastureData pd = reg.get(s.dim(), pos);
                if (pd == null) continue;
                String csv = PastureHealth.idsCsv(gatherHealth(server, dims.get(s.dim()), pos, pd));
                if (!csv.isEmpty()) health.addProperty(s.dim() + "|" + s.pos(), csv);
            }
        }
        sendGated(player, "pastures", new NotebookPasturesS2C(snaps, health.toString()));
    }

    /** Gather one pasture's health flags (#37). The (pre-resolved) world + loaded-chunk check pick whether the
     *  live block entity (parents / bred-species bank caps) participates; registry state always does. */
    private static List<PastureHealth.Flag> gatherHealth(MinecraftServer server, ServerWorld world, BlockPos pos, PastureData pd) {
        PokemonPastureBlockEntity be = null;
        if (world != null
                && world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
                && world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity p) {
            be = p;
        }
        return gatherHealth(server, pd, be);
    }

    /** Health flags with a (possibly null) live block entity in hand — the pure core does the deciding.
     *  Species come straight off the tether list (NOT {@code rosterOf} — no per-mon stats JSON on a 1/s poll). */
    private static List<PastureHealth.Flag> gatherHealth(MinecraftServer server, PastureData pd, PokemonPastureBlockEntity be) {
        int monCount = -1;
        List<String> fullSpecies = null;
        if (be != null) {
            List<String> species = new ArrayList<>();
            try {
                var tethered = new ArrayList<>(be.getTetheredPokemon());
                monCount = tethered.size();
                for (var t : tethered) {
                    try {
                        var pkm = t.getPokemon();
                        String sp = pkm == null ? null : pkm.getSpecies().getName();
                        if (sp != null && !sp.isEmpty() && !species.contains(sp)) species.add(sp);
                    } catch (Throwable ignored) { }
                }
            } catch (Throwable t) { }
            if (pd.owner != null && !species.isEmpty()) {
                BioBankData bank = BioBankStore.get(server).get(pd.owner);
                if (bank != null) {
                    List<String> full = new ArrayList<>();
                    for (String sp : species) {   // no speciesCounts() map copy per pasture per second (R3 #2)
                        if (bank.countOfIgnoreCase(sp) >= com.greenerpastures.biobank.BioBank.capacity()) full.add(sp);
                    }
                    if (!full.isEmpty()) fullSpecies = full;
                }
            }
        }
        return PastureHealth.evaluate(pd.owner != null, pd.tier() != null, monCount, pd.eggQueue.isFull(), fullSpecies);
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

    // ── Specimen Disks (mon compression v1 — Deuce, 2026-07-05) ─────────────────────────────────────────

    /** The Specimens tab: live party digest + blank-disk count. Change-gated like every channel. */
    public static void pushSpecimens(ServerPlayerEntity player) {
        JsonObject root = new JsonObject();
        JsonArray party = new JsonArray();
        try {
            var store = com.cobblemon.mod.common.util.PlayerExtensionsKt.party(player);
            for (int i = 0; i < com.greenerpastures.specimen.SpecimenRules.PARTY_SLOTS; i++) {
                com.cobblemon.mod.common.pokemon.Pokemon mon = store.get(i);
                if (mon == null) continue;
                JsonObject o = new JsonObject();
                o.addProperty("slot", i);
                o.addProperty("species", mon.getSpecies().getName());
                o.addProperty("level", mon.getLevel());
                o.addProperty("shiny", mon.getShiny());
                o.addProperty("gender", mon.getGender().name().toLowerCase(java.util.Locale.ROOT));
                party.add(o);
            }
            root.addProperty("busy", com.cobblemon.mod.common.util.PlayerExtensionsKt.isPartyBusy(player));
        } catch (Throwable t) {
            root.addProperty("busy", true);   // Cobblemon hiccup → tab shows read-only, never crashes the push
        }
        root.add("party", party);
        root.addProperty("blanks", countBlankSpecimenDisks(player));
        sendGated(player, "specimens", new NotebookSpecimensS2C(GSON.toJson(root)));
    }

    private static int countBlankSpecimenDisks(ServerPlayerEntity player) {
        int n = 0;
        for (ItemStack st : player.getInventory().main) {
            if (st.isOf(com.greenerpastures.economy.GpItems.SPECIMEN_DISK) && !st.contains(GpComponents.SPECIMEN)) n += st.getCount();
        }
        return n;
    }

    /** Party mon → written Specimen Disk. Dupe-proof order: verify EVERY gate (SpecimenRules) including a
     *  guaranteed landing slot, THEN remove from party, THEN mint into the pre-verified slot — with
     *  offerOrDrop as the never-lose-the-mon last resort. No insertStack, ever. */
    private static void compressMon(ServerPlayerEntity player, int slot) {
        try {
            var party = com.cobblemon.mod.common.util.PlayerExtensionsKt.party(player);
            var inv = player.getInventory();
            int blankSlot = -1;
            for (int i = 0; i < inv.main.size(); i++) {
                ItemStack st = inv.main.get(i);
                if (st.isOf(com.greenerpastures.economy.GpItems.SPECIMEN_DISK) && !st.contains(GpComponents.SPECIMEN)) { blankSlot = i; break; }
            }
            boolean landing = blankSlot >= 0 && (inv.main.get(blankSlot).getCount() == 1 || inv.getEmptySlot() >= 0);
            com.cobblemon.mod.common.pokemon.Pokemon mon =
                    (slot >= 0 && slot < com.greenerpastures.specimen.SpecimenRules.PARTY_SLOTS) ? party.get(slot) : null;
            String err = com.greenerpastures.specimen.SpecimenRules.compressRefusal(
                    party.size(), slot, com.cobblemon.mod.common.util.PlayerExtensionsKt.isPartyBusy(player),
                    mon != null, blankSlot >= 0, landing);
            if (err != null) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r " + err), false);
                return;
            }
            ItemStack written = new ItemStack(com.greenerpastures.economy.GpItems.SPECIMEN_DISK);
            written.set(GpComponents.SPECIMEN, mon.saveToNBT(player.getServerWorld().getRegistryManager(),
                    new net.minecraft.nbt.NbtCompound()));
            written.set(GpComponents.SPECIMEN_SUMMARY, new com.greenerpastures.specimen.SpecimenSummary(
                    mon.getSpecies().getName(), mon.getLevel(), mon.getShiny(),
                    mon.getGender().name().toLowerCase(java.util.Locale.ROOT)));
            if (!party.remove(mon)) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Could not archive — the party refused the removal."), false);
                return;
            }
            ItemStack blank = inv.main.get(blankSlot);
            blank.decrement(1);
            if (inv.main.get(blankSlot).isEmpty()) inv.main.set(blankSlot, written);
            else {
                int empty = inv.getEmptySlot();
                if (empty >= 0) inv.main.set(empty, written);
                else inv.offerOrDrop(written);   // race fallback — the mon is NEVER lost
            }
            player.sendMessage(Text.literal("§a[Greener Pastures]§r Archived §b" + mon.getSpecies().getName()
                    + "§r to a Specimen Disk."), false);
            GpLog.i("specimen", "compress", "player", player.getUuid().toString(),
                    "species", mon.getSpecies().getName(), "level", Integer.toString(mon.getLevel()),
                    "shiny", Boolean.toString(mon.getShiny()));
        } catch (Throwable t) {
            GpLog.w("specimen", "compress_fail", "err", String.valueOf(t));
            player.sendMessage(Text.literal("§c[Greener Pastures]§r Archive failed — nothing was changed."), false);
        }
    }

    // ── Multi-item targeting (backlog #5): with 2+ Kernels/Daemons in the inventory, the tabs used to
    // operate on whichever was found first. The client picks a target card; we remember the SLOT per player
    // (session-scoped: cleared on disconnect + SERVER_STARTED) and re-validate it on EVERY use — if the slot
    // no longer holds the right item we silently fall back to first-found (never operate on the wrong stack).
    private static final Map<UUID, Integer> augTargetSlot = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, Integer> daemonTargetSlot = new java.util.concurrent.ConcurrentHashMap<>();
    static final int OFFHAND_SLOT = 1000;   // wire encoding for the offhand slot in target selection

    private static KernelRef kernelAt(ServerPlayerEntity player, int slot) {
        PlayerInventory inv = player.getInventory();
        if (slot == OFFHAND_SLOT) {
            if (!inv.offHand.isEmpty() && inv.offHand.get(0).getItem() instanceof BreedingUpgradeItem)
                return new KernelRef(inv.offHand.get(0), ns -> inv.offHand.set(0, ns));
            return null;
        }
        if (slot < 0 || slot >= inv.main.size()) return null;
        if (!(inv.main.get(slot).getItem() instanceof BreedingUpgradeItem)) return null;
        final int idx = slot;
        return new KernelRef(inv.main.get(idx), ns -> inv.main.set(idx, ns));
    }

    private static KernelRef targetKernel(ServerPlayerEntity player) {
        Integer t = augTargetSlot.get(player.getUuid());
        if (t != null) {
            KernelRef r = kernelAt(player, t);
            if (r != null) return r;
            augTargetSlot.remove(player.getUuid());   // stale slot (kernel moved/slotted) → back to first-found
        }
        return firstKernel(player);
    }

    private static ItemStack daemonAt(ServerPlayerEntity player, int slot) {
        PlayerInventory inv = player.getInventory();
        if (slot == OFFHAND_SLOT)
            return (!inv.offHand.isEmpty() && inv.offHand.get(0).getItem() instanceof DaemonItem) ? inv.offHand.get(0) : ItemStack.EMPTY;
        if (slot < 0 || slot >= inv.main.size()) return ItemStack.EMPTY;
        return inv.main.get(slot).getItem() instanceof DaemonItem ? inv.main.get(slot) : ItemStack.EMPTY;
    }

    private static ItemStack targetDaemon(ServerPlayerEntity player) {
        Integer t = daemonTargetSlot.get(player.getUuid());
        if (t != null) {
            ItemStack s = daemonAt(player, t);
            if (!s.isEmpty()) return s;
            daemonTargetSlot.remove(player.getUuid());
        }
        return firstDaemon(player);
    }

    /** Right-click rename (QoL, Deuce 2026-07-04): label a Kernel so multi-kernel farms stay legible. Name
     *  lands on CUSTOM_NAME (shows on tooltip, target cards, pasture KERNEL row); ≤32 chars; empty clears. */
    private static void renameHeldKernel(ServerPlayerEntity player, String name) {
        ItemStack held = player.getMainHandStack();
        if (!(held.getItem() instanceof BreedingUpgradeItem)) return;
        String clean = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (clean.length() > 32) clean = clean.substring(0, 32);
        if (clean.isEmpty()) held.remove(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
        else held.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                Text.literal(clean).styled(st -> st.withItalic(false)));
        GpLog.i("notebook", "kernel_rename", "player", player.getUuid().toString(), "name", clean);
    }

    private static int slotCost(AugmentType at) {
        return 1;   // uniform 1 slot per augment for v1
    }

    // ── GPU economy (§7.5 — now LIVE): baked constants, deliberately NO config (anti-p2w, same rule as
    // drop rates). Quality augments (shiny/IV/EV/breeding-meta) cost more than throughput ones; a Daemon
    // buff tier is a flat install fee on top of its ongoing Data drain. Re-picking a parameterized augment's
    // VALUE stays free (the augment was already bought); removal never refunds.
    private static final int GPU_QUALITY = 2;
    private static final int GPU_THROUGHPUT = 1;
    private static final int GPU_PER_BUFF_TIER = 2;

    private static int gpuCost(AugmentType at) {
        return at.function.cls == com.greenerpastures.economy.TetherClass.QUALITY ? GPU_QUALITY : GPU_THROUGHPUT;
    }

    /** Consume {@code n} GPUs from the player's main+offhand (manual decrement — never trust insertStack-style
     *  seams; mirrors pull()). Returns false (and consumes nothing) if they hold fewer than {@code n}. */
    private static boolean consumeGpu(ServerPlayerEntity player, int n) {
        if (n <= 0) return true;
        if (countGpu(player) < n) return false;
        PlayerInventory inv = player.getInventory();
        int left = n;
        for (var list : java.util.List.of(inv.main, inv.offHand)) {
            for (int i = 0; i < list.size() && left > 0; i++) {
                ItemStack st = list.get(i);
                if (!st.isOf(GpItems.GPU)) continue;
                int take = Math.min(left, st.getCount());
                st.decrement(take);
                left -= take;
            }
        }
        inv.markDirty();
        return left == 0;
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
        KernelRef ref = targetKernel(player);
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
                        at.installedOn(ref.stack()), gpuCost(at)));
            }
        }
        sendGated(player, "augmenter", new NotebookAugmenterS2C(has, tierLabel, used, slotCap, catalog));
        sendGated(player, "augmeta", new NotebookAugmenterMetaS2C(augmenterMetaJson(player, has ? ref.stack() : null)));
    }

    /** The picker meta (#34/#35) riding beside the augmenter push: the Kernel's current selector values +
     *  EV spread, and the server-authoritative nature/ball catalogs (the React pickers can never drift). */
    /** Every Kernel in the inventory as a target card: slot · tier · augment count · ⛧; target = the one
     *  the tab currently operates on (the resolved stack, so first-found fallback marks correctly). */
    private static JsonArray kernelCandidatesJson(ServerPlayerEntity player, ItemStack resolved) {
        JsonArray arr = new JsonArray();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i <= inv.main.size(); i++) {
            boolean off = i == inv.main.size();
            ItemStack st = off ? (inv.offHand.isEmpty() ? ItemStack.EMPTY : inv.offHand.get(0)) : inv.main.get(i);
            if (!(st.getItem() instanceof BreedingUpgradeItem bu)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("slot", off ? OFFHAND_SLOT : i);
            o.addProperty("tier", bu.tier().name());
            if (st.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME))
                o.addProperty("name", st.getName().getString());
            Augments a = st.get(GpComponents.AUGMENTS);
            o.addProperty("augs", a == null ? 0 : a.toLevels().size());
            if (Boolean.TRUE.equals(st.get(GpComponents.CORRUPTED))) o.addProperty("corrupted", true);
            if (st == resolved) o.addProperty("target", true);
            arr.add(o);
        }
        return arr;
    }

    private static JsonArray daemonCandidatesJson(ServerPlayerEntity player) {
        JsonArray arr = new JsonArray();
        ItemStack resolved = targetDaemon(player);
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i <= inv.main.size(); i++) {
            boolean off = i == inv.main.size();
            ItemStack st = off ? (inv.offHand.isEmpty() ? ItemStack.EMPTY : inv.offHand.get(0)) : inv.main.get(i);
            if (!(st.getItem() instanceof DaemonItem)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("slot", off ? OFFHAND_SLOT : i);
            o.addProperty("on", DaemonItem.isOn(st));
            o.addProperty("buffs", DaemonItem.loadoutOf(st).toLevels().size());
            if (st == resolved) o.addProperty("target", true);
            arr.add(o);
        }
        return arr;
    }

    private static String augmenterMetaJson(ServerPlayerEntity player, ItemStack kernel) {
        JsonObject root = new JsonObject();
        root.add("kernels", kernelCandidatesJson(player, kernel));
        root.add("daemons", daemonCandidatesJson(player));
        JsonObject values = new JsonObject();
        if (kernel != null) {
            root.addProperty("corrupted", Boolean.TRUE.equals(kernel.get(GpComponents.CORRUPTED)));
            Integer cp = kernel.get(GpComponents.CORRUPT_PAIRS);
            if (cp != null && cp > 0) root.addProperty("corruptPairs", cp);
            Augments a = kernel.get(GpComponents.AUGMENTS);
            int nat = a == null ? 0 : a.level(com.greenerpastures.economy.AugmentFunction.NATURE);
            int ball = a == null ? 0 : a.level(com.greenerpastures.economy.AugmentFunction.BALL);
            if (nat > 0) {
                JsonObject o = new JsonObject();
                o.addProperty("value", nat);
                o.addProperty("label", String.valueOf(com.greenerpastures.pasture.breeding.NatureCatalog.byIndex(nat)));
                values.add("NATURE", o);
            }
            if (ball > 0) {
                JsonObject o = new JsonObject();
                o.addProperty("value", ball);
                o.addProperty("label", String.valueOf(com.greenerpastures.pasture.breeding.BallCatalog.byIndex(ball)));
                values.add("BALL", o);
            }
            com.greenerpastures.pasture.breeding.EvSpread ev = kernel.get(GpComponents.EV_SPREAD);
            if (ev != null && !ev.isEmpty()) {
                JsonObject o = new JsonObject();
                JsonArray spread = new JsonArray();
                for (int v : new int[]{ev.hp(), ev.atk(), ev.def(), ev.spa(), ev.spd(), ev.spe()}) spread.add(v);
                o.add("spread", spread);
                values.add("EV", o);
            }
        }
        root.add("values", values);
        root.add("natures", naturesJson());
        root.add("balls", ballsJson());
        return root.toString();
    }

    // The nature/ball catalogs are compile-time constants — build their JSON ONCE instead of 57 elements per
    // push per second (perf-audit R3 F3). Never mutated after creation, so sharing the instance is safe.
    private static JsonArray naturesJsonCache, ballsJsonCache;

    private static JsonArray naturesJson() {
        if (naturesJsonCache == null) {
            JsonArray a = new JsonArray();
            for (String n : com.greenerpastures.pasture.breeding.NatureCatalog.NATURES) a.add(n);
            naturesJsonCache = a;
        }
        return naturesJsonCache;
    }

    private static JsonArray ballsJson() {
        if (ballsJsonCache == null) {
            JsonArray a = new JsonArray();
            for (String b : com.greenerpastures.pasture.breeding.BallCatalog.BALLS) a.add(b);
            ballsJsonCache = a;
        }
        return ballsJsonCache;
    }

    /** Install an augment from the console. Arg grammar ({@link AugmentArg}): {@code "SHINY"} ·
     *  {@code "NATURE:7"} / {@code "BALL:12"} (1-based catalog index) · {@code "EV:hp,atk,def,spa,spd,spe"}.
     *  A parameterized augment that's ALREADY installed may re-pick its value in place (no extra slot). */
    private static void applyAugment(ServerPlayerEntity player, String rawArg) {
        KernelRef ref = targetKernel(player);
        if (ref == null) return;
        AugmentArg arg = AugmentArg.parse(rawArg);
        AugmentType at = arg == null ? null : augmentType(arg.type());
        if (at == null || !at.appliesTo(ref.stack())) return;
        if (Boolean.TRUE.equals(ref.stack().get(GpComponents.CORRUPTED))) {
            player.sendMessage(Text.literal("§5⛧§r This Kernel is §8corrupted§r — it is beyond modification."), false);
            return;
        }
        if (at.parameterized() != (arg.index() > 0 || arg.ev() != null)) return;   // param augments need a value; plain ones must not carry one

        boolean installed = at.installedOn(ref.stack());
        if (installed && !at.parameterized()) return;                              // no-dupe (re-pick is param-only)
        if (!installed) {                                                          // NEW install: slot gate + GPU fee (§7.5 live)
            BreedingTier tier = ((BreedingUpgradeItem) ref.stack().getItem()).tier();
            if (slotsUsed(ref.stack()) + slotCost(at) > tier.slots) return;
            int fee = gpuCost(at);
            if (!consumeGpu(player, fee)) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Not enough GPU — "
                        + at.effectSummary() + " costs " + fee + ", you have " + countGpu(player) + "."), false);
                return;
            }
        }

        String detail = "";
        switch (at) {
            case NATURE -> {
                if (com.greenerpastures.pasture.breeding.NatureCatalog.byIndex(arg.index()) == null) return;   // out of catalog
                ref.writer().accept(at.apply(ref.stack(), arg.index()));
                detail = com.greenerpastures.pasture.breeding.NatureCatalog.byIndex(arg.index());
            }
            case BALL -> {
                if (com.greenerpastures.pasture.breeding.BallCatalog.byIndex(arg.index()) == null) return;
                ref.writer().accept(at.apply(ref.stack(), arg.index()));
                detail = com.greenerpastures.pasture.breeding.BallCatalog.byIndex(arg.index());
            }
            case EV -> {
                int[] v = arg.ev();
                com.greenerpastures.pasture.breeding.EvSpread spread =
                        new com.greenerpastures.pasture.breeding.EvSpread(v[0], v[1], v[2], v[3], v[4], v[5]);   // ctor clamps 252/510
                if (spread.isEmpty()) return;
                ItemStack out = ref.stack().copy();
                out.set(GpComponents.EV_SPREAD, spread);
                ref.writer().accept(out);
                detail = spread.hp() + "/" + spread.atk() + "/" + spread.def() + "/" + spread.spa() + "/" + spread.spd() + "/" + spread.spe();
            }
            default -> ref.writer().accept(at.apply(ref.stack()));
        }
        GpLog.i("notebook", "augment_apply", "player", player.getUuid().toString(), "type", at.name(),
                "value", detail.isEmpty() ? "-" : detail, "repick", installed);
    }

    private static void removeAugment(ServerPlayerEntity player, String typeName) {
        KernelRef ref = targetKernel(player);
        if (ref == null) return;
        AugmentType at = augmentType(typeName);
        if (at == null || !at.appliesTo(ref.stack()) || !at.installedOn(ref.stack())) return;
        if (Boolean.TRUE.equals(ref.stack().get(GpComponents.CORRUPTED))) {
            player.sendMessage(Text.literal("§5⛧§r This Kernel is §8corrupted§r — it is beyond modification."), false);
            return;
        }
        ItemStack out = ref.stack().copy();
        if (at == AugmentType.EV) out.remove(GpComponents.EV_SPREAD);   // the primer's value IS the component
        Augments a = out.get(GpComponents.AUGMENTS);
        if (a != null && a.level(at.function) > 0) out.set(GpComponents.AUGMENTS, a.withLevel(at.function, 0));
        ref.writer().accept(out);
        GpLog.i("notebook", "augment_remove", "player", player.getUuid().toString(), "type", typeName);
    }

    // ── BioBank (per-player; browse-only in 6a) ─────────────────────────────────────────────────────────

    /** Withdraw the egg at {@code flatIndex} (the console's flattened BioBank order) back into the player's
     *  inventory — placed into a MAIN slot we picked ourselves (never {@code insertStack}; see {@link #pull}). */
    private static void withdrawEgg(ServerPlayerEntity player, int flatIndex) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var main = player.getInventory().main;
        int slot = -1;
        for (int i = 0; i < main.size(); i++) if (main.get(i).isEmpty()) { slot = i; break; }
        if (slot < 0) {                                   // no room — leave the egg in the BioBank (never destroy)
            player.sendMessage(net.minecraft.text.Text.literal("§c[Greener Pastures]§r Inventory full — the egg stays in the BioBank."), false);
            GpLog.i("notebook", "biobank_full", "player", player.getUuid().toString());
            return;
        }
        ItemStack egg = BioBankStore.get(server).withdraw(player.getUuid(), flatIndex);
        if (!egg.isEmpty()) {
            main.set(slot, egg);
            player.getInventory().markDirty();
            GpLog.i("notebook", "biobank_withdraw", "player", player.getUuid().toString(), "index", Integer.toString(flatIndex));
        }
    }

    // ── pasture config (the React right-click-a-pasture screen; replaces the owo PastureScreen) ───────────────

    /** Build + push the focused pasture's full editable config (name · tier · link · maxPairs · roster). */
    public static void pushPastureConfig(ServerPlayerEntity player, BlockPos pos) {
        pushPastureConfig(player, pos, true);
    }

    /** {@code full=false} is the PREFETCH shape (R3 F6): cache-warming only — skip the snapshot re-capture and
     *  the per-mon reflective stats JSON (parent-inspector data). A real focus round-trips {@code full=true}
     *  moments later and silently upgrades the cache (stale-while-revalidate already handles it). */
    public static void pushPastureConfig(ServerPlayerEntity player, BlockPos pos, boolean full) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerWorld world = player.getServerWorld();
        if (world == null || !(world.getBlockEntity(pos) instanceof PokemonPastureBlockEntity pasture)) return;
        try (var span = com.greenerpastures.core.GpProf.begin(full ? "net.pasture_config" : "net.pasture_prefetch")) {
            PastureData pd = PastureRegistry.get(server).getOrCreate(world, pos);
            // Refresh this pasture's Pastures-tab snapshot too, so a rename / edit shows up there (Deuce, 2026-07-01).
            if (full) PastureSnapshotStore.get(server).capture(player.getUuid(), world, pos, pd, pasture);
            BreedingTier tier = pd.tier();
            boolean linked = pd.owner != null && pd.owner.equals(player.getUuid());
            List<MonEntry> roster = CobbreedingBridge.rosterOf(pasture, pd, full);
            long key = pos.asLong();
            sendGated(player, "cfg:" + key, new NotebookPastureConfigS2C(
                    key, pd.name, tier == null ? "" : tier.name(), linked, pd.maxPairs(), roster));
            sendGated(player, "graph:" + key, new NotebookGraphS2C(key, pd.graphJson == null ? "" : pd.graphJson));
            sendGated(player, "extra:" + key, new NotebookPastureExtraS2C(key, pastureExtraJson(server, pd, pasture)));
        }
    }

    /** The focused pasture's extras blob: health strip (#37) + the slotted Kernel's breeding-meta loadout. */
    private static String pastureExtraJson(MinecraftServer server, PastureData pd, PokemonPastureBlockEntity pasture) {
        JsonObject root = new JsonObject();
        JsonArray health = new JsonArray();
        for (PastureHealth.Flag f : gatherHealth(server, pd, pasture)) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("icon", f.icon());
            o.addProperty("text", f.text());
            health.add(o);
        }
        root.add("health", health);
        ItemStack kernel = pd.upgrades.getStack(0);
        if (kernel.getItem() instanceof BreedingUpgradeItem) {
            JsonObject k = new JsonObject();
            Augments a = kernel.get(GpComponents.AUGMENTS);
            int nat = a == null ? 0 : a.level(com.greenerpastures.economy.AugmentFunction.NATURE);
            int ball = a == null ? 0 : a.level(com.greenerpastures.economy.AugmentFunction.BALL);
            if (nat > 0) k.addProperty("nature", String.valueOf(com.greenerpastures.pasture.breeding.NatureCatalog.byIndex(nat)));
            if (ball > 0) k.addProperty("ball", String.valueOf(com.greenerpastures.pasture.breeding.BallCatalog.byIndex(ball)));
            com.greenerpastures.pasture.breeding.EvSpread ev = kernel.get(GpComponents.EV_SPREAD);
            if (ev != null && !ev.isEmpty()) {
                k.addProperty("ev", ev.hp() + "/" + ev.atk() + "/" + ev.def() + "/" + ev.spa() + "/" + ev.spd() + "/" + ev.spe());
            }
            if (a != null && a.level(com.greenerpastures.economy.AugmentFunction.ABILITY) > 0) k.addProperty("ha", true);
            if (a != null && a.level(com.greenerpastures.economy.AugmentFunction.EGG_MOVE) > 0) k.addProperty("moves", true);
            // Full augment map + corruption — the client dresses its DISPLAY stack with these so hovering the
            // slotted kernel shows the real tooltip, not a default-born one (Deuce, 2026-07-04).
            if (kernel.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME))
                k.addProperty("name", kernel.getName().getString());
            JsonObject augs = new JsonObject();
            if (a != null) a.levels().forEach(augs::addProperty);
            k.add("augs", augs);
            if (Boolean.TRUE.equals(kernel.get(GpComponents.CORRUPTED))) k.addProperty("corrupted", true);
            Integer kcp = kernel.get(GpComponents.CORRUPT_PAIRS);
            if (kcp != null && kcp > 0) k.addProperty("corruptPairs", kcp);
            root.add("kernel", k);
        }
        return root.toString();
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
                        pd.lastHarvestTick = world.getTime();   // anchor offline-progress at claim — never backfill the unowned past
                        pd.lastBreedTick = world.getTime();
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
        // Rev gate BEFORE assembly: flattening a hoard-scale bank (thousands of eggs → cards + entries) every
        // second was the single biggest per-viewer cost (R3 F1/F2). Unchanged bank → nothing at all happens.
        if (!changed(player, "biobank_rev", bank == null ? -1L : bank.rev())) return;
        List<NotebookBioBankS2C.Entry> entries = new ArrayList<>();
        try (var span = com.greenerpastures.core.GpProf.begin("net.biobank_flatten")) {
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
        }
        ServerPlayNetworking.send(player, new NotebookBioBankS2C(entries.size(), entries));
    }

    /** Write Data onto blank media (§5c — the Notebook is the drive): consume 1 blank + debit the
     *  denomination's value → hand back the written disk. Refuses BEFORE debiting; never destroys media. */
    private static void writeDisk(ServerPlayerEntity player, String denomId) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Item denomItem = Registries.ITEM.get(Identifier.tryParse(denomId == null ? "" : denomId));
        if (!(denomItem instanceof com.greenerpastures.economy.DataDiskItem disk) || disk.value <= 0) return;
        var inv = player.getInventory();
        int blankSlot = -1;
        for (int i = 0; i < inv.main.size(); i++) {
            if (inv.main.get(i).isOf(GpItems.DISK_BLANK)) { blankSlot = i; break; }
        }
        if (blankSlot < 0) {
            player.sendMessage(Text.literal("§c[Greener Pastures]§r No blank disk to write onto."), false);
            return;
        }
        DataStore data = DataStore.get(server);
        if (data.balanceOf(player.getUuid()) < disk.value) {
            player.sendMessage(Text.literal("§c[Greener Pastures]§r Not enough Data — that disk holds "
                    + String.format("%,d", disk.value) + "."), false);
            return;
        }
        ItemStack blank = inv.main.get(blankSlot);
        if (blank.getCount() == 1) {
            if (!data.tryDebit(player.getUuid(), disk.value)) return;
            inv.main.set(blankSlot, new ItemStack(denomItem));           // media swaps in place — always room
        } else {
            int empty = -1;
            for (int i = 0; i < inv.main.size(); i++) if (inv.main.get(i).isEmpty()) { empty = i; break; }
            if (empty < 0) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Inventory full — make room for the written disk."), false);
                return;
            }
            if (!data.tryDebit(player.getUuid(), disk.value)) return;
            blank.decrement(1);
            inv.main.set(empty, new ItemStack(denomItem));
        }
        inv.markDirty();
        GpLog.i("disk", "write", "player", player.getUuid().toString(), "denom", denomId, "value", Long.toString(disk.value));
    }

    /** Send the Rituals tab (v2): the player's LEARNED hidden recipes (full reveal), the still-hidden
     *  count (Steam-style teaser), and the dedicated ritual loot pool. */
    public static void pushRituals(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        com.greenerpastures.ritual.RitualLedger ledger = com.greenerpastures.ritual.RitualLedger.get(server);
        com.greenerpastures.ritual.RitualBook book = com.greenerpastures.ritual.RitualSystem.config().activeRituals();
        java.util.Set<String> learned = ledger.learnedOf(player.getUuid());
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        JsonArray lockedArr = new JsonArray();
        int hidden = 0;
        for (com.greenerpastures.ritual.Ritual r : book.rituals()) {
            if (!r.enabled()) continue;
            if (!learned.contains(r.id())) {
                if (r.hint().isEmpty()) { hidden++; }
                else {                                        // hinted teaser: the PRIZE + the riddle, no recipe
                    JsonObject lock = new JsonObject();
                    lock.addProperty("name", r.name());
                    lock.addProperty("hint", r.hint());
                    lockedArr.add(lock);
                }
                continue;
            }
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("name", r.name());
            JsonObject species = new JsonObject();
            r.requirement().speciesMinCounts().forEach(species::addProperty);
            o.add("species", species);
            JsonObject types = new JsonObject();
            r.requirement().typeMinCounts().forEach(types::addProperty);
            o.add("types", types);
            o.addProperty("minDistinct", r.requirement().minDistinctTypes());
            JsonArray sig = new JsonArray();
            r.requirement().signatureSpeciesAnyOf().forEach(sig::add);
            o.add("signature", sig);
            o.addProperty("output", r.outputItem());
            o.addProperty("qty", r.outputQty());
            o.addProperty("hits", ledger.hitsOf(player.getUuid(), r.id()));
            o.addProperty("pulls", ledger.pullsOf(player.getUuid(), r.id()));
            if (r.pastureSpan() > 1) o.addProperty("span", r.pastureSpan());
            if (!r.outputPool().isEmpty()) o.addProperty("pool", true);
            if (!r.requirement().groupMinCounts().isEmpty()) {
                JsonArray groups = new JsonArray();
                for (var g : r.requirement().groupMinCounts()) {
                    JsonObject go = new JsonObject();
                    JsonArray any = new JsonArray();
                    g.anyOf().forEach(any::add);
                    go.add("anyOf", any);
                    go.addProperty("min", g.min());
                    groups.add(go);
                }
                o.add("groups", groups);
            }
            arr.add(o);
        }
        root.add("learned", arr);
        root.add("locked", lockedArr);
        root.addProperty("hidden", hidden);
        JsonObject loot = new JsonObject();
        ledger.lootOf(player.getUuid()).forEach(loot::addProperty);
        root.add("loot", loot);
        sendGated(player, "rituals", new NotebookRitualsS2C(GSON.toJson(root)));
    }

    /** Take ritual loot into the inventory — same manual capacity+placement contract as {@link #pull}
     *  (never trust insertStack, refuse loudly, the pool is only debited by what actually landed). */
    private static void ritualPull(ServerPlayerEntity player, String itemId, int mode) {
        MinecraftServer server = player.getServer();
        if (server == null || itemId == null || itemId.isEmpty()) return;
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == Items.AIR) return;
        com.greenerpastures.ritual.RitualLedger ledger = com.greenerpastures.ritual.RitualLedger.get(server);
        long have = ledger.lootOf(player.getUuid()).getOrDefault(itemId, 0L);
        if (have <= 0) return;
        var main = player.getInventory().main;
        ItemStack probe = new ItemStack(item);
        int maxStack = probe.getMaxCount();
        long capacity = 0;
        for (ItemStack s : main) {
            if (s.isEmpty()) capacity += maxStack;
            else if (ItemStack.areItemsAndComponentsEqual(s, probe)) capacity += Math.max(0, s.getMaxCount() - s.getCount());
        }
        long want = switch (mode) {
            case 0 -> 1L;
            case 1 -> Math.min(maxStack, have);
            default -> have;
        };
        want = Math.min(Math.min(want, have), capacity);
        if (want <= 0) {
            player.sendMessage(net.minecraft.text.Text.literal("§c[Greener Pastures]§r Inventory full — nothing pulled."), false);
            GpLog.i("ritual", "pull_full", "player", player.getUuid().toString(), "item", itemId);
            return;
        }
        long placed = 0;
        for (int i = 0; i < main.size() && placed < want; i++) {
            ItemStack s = main.get(i);
            if (s.isEmpty() || !ItemStack.areItemsAndComponentsEqual(s, probe)) continue;
            int add = (int) Math.min(s.getMaxCount() - s.getCount(), want - placed);
            if (add > 0) { s.increment(add); placed += add; }
        }
        for (int i = 0; i < main.size() && placed < want; i++) {
            if (!main.get(i).isEmpty()) continue;
            int add = (int) Math.min(maxStack, want - placed);
            main.set(i, new ItemStack(item, add));
            placed += add;
        }
        player.getInventory().markDirty();
        if (placed > 0) ledger.takeLoot(player.getUuid(), itemId, placed);
        GpLog.i("ritual", "pull", "player", player.getUuid().toString(), "item", itemId, "n", Long.toString(placed));
    }

    /** The Vaal roll (Deuce 2026-07-04): consume ONE Illicit Data Disk, corrupt the first held Kernel with
     *  {@link com.greenerpastures.pasture.breeding.compiler.KernelCorruption}'s baked table. Irreversible. */
    private static void corruptKernel(ServerPlayerEntity player) {
        KernelRef ref = targetKernel(player);
        if (ref == null) return;
        if (Boolean.TRUE.equals(ref.stack().get(GpComponents.CORRUPTED))) {
            player.sendMessage(Text.literal("§5⛧§r Already corrupted — the disk finds nothing left to take."), false);
            return;
        }
        // consume exactly one Illicit Data Disk from main+offhand
        var inv = player.getInventory();
        boolean paid = false;
        for (var list : java.util.List.of(inv.main, inv.offHand)) {
            for (int i = 0; i < list.size() && !paid; i++) {
                if (list.get(i).isOf(GpItems.DISK_ROCKET)) { list.get(i).decrement(1); paid = true; }
            }
        }
        if (!paid) {
            player.sendMessage(Text.literal("§c[Greener Pastures]§r You need an Illicit Data Disk to corrupt."), false);
            return;
        }
        inv.markDirty();

        var roll = com.greenerpastures.pasture.breeding.compiler.KernelCorruption.roll(CORRUPT_RNG);
        ItemStack out = ref.stack().copy();
        String detail = "";
        switch (roll.outcome()) {
            case BLESSED -> {
                // a random plain augment force-installed, IGNORING slot capacity (the beyond-cap gift)
                AugmentType[] pool = {AugmentType.SHINY, AugmentType.SPEED, AugmentType.ENRICHMENT,
                        AugmentType.DROP_RATE, AugmentType.DROP_YIELD, AugmentType.IV_FLOOR,
                        AugmentType.ABILITY, AugmentType.EGG_MOVES};
                AugmentType gift = pool[CORRUPT_RNG.nextInt(pool.length)];
                out = gift.apply(out);
                detail = gift.effectSummary() + " §7(installed beyond capacity)§r";
            }
            case WILD -> {
                if (roll.variant() == 0) {
                    Augments a = out.get(GpComponents.AUGMENTS);
                    int cur = a == null ? 0 : a.level(com.greenerpastures.economy.AugmentFunction.DROP_RATE);
                    int doubled = Math.max(1, cur) * 2;
                    out.set(GpComponents.AUGMENTS, (a == null ? Augments.NONE : a)
                            .withLevel(com.greenerpastures.economy.AugmentFunction.DROP_RATE, doubled));
                    detail = "its drop-rate mod DOUBLED (" + String.format("%.2f", doubled / 100.0) + "%)";
                } else {
                    Integer bonus = out.get(GpComponents.CORRUPT_PAIRS);
                    out.set(GpComponents.CORRUPT_PAIRS, (bonus == null ? 0 : bonus) + 1);
                    detail = "+1 breeding pair — beyond its tier";
                }
            }
            case NOTHING -> detail = "";
            case BRICKED -> {
                if (roll.variant() == 0) {
                    out.set(GpComponents.AUGMENTS, Augments.NONE);
                    out.remove(GpComponents.EV_SPREAD);
                    out.remove(GpComponents.CORRUPT_PAIRS);
                    detail = "every augment wiped";
                } else {
                    BreedingTier tier = ((BreedingUpgradeItem) out.getItem()).tier();
                    BreedingTier lower = tier.ordinal() == 0 ? tier : BreedingTier.values()[tier.ordinal() - 1];
                    out = new ItemStack(com.greenerpastures.pasture.breeding.BetterPasture.ITEMS.get(lower));
                    detail = "the Kernel DEGRADED to " + lower.name();
                }
            }
        }
        out.set(GpComponents.CORRUPTED, Boolean.TRUE);
        ref.writer().accept(out);
        String line = com.greenerpastures.pasture.breeding.compiler.KernelCorruption.reveal(roll, detail);
        player.sendMessage(Text.literal(line), false);
        com.greenerpastures.notify.Inbox.push(player.getUuid(), "⛧",
                "Corruption: " + roll.outcome() + (detail.isEmpty() ? "" : " — " + detail.replaceAll("§.", "")));
        GpLog.i("corrupt", "kernel", "player", player.getUuid().toString(),
                "outcome", roll.outcome().name(), "variant", roll.variant(), "detail", detail.replaceAll("§.", ""));
    }

    private static final java.util.Random CORRUPT_RNG = new java.util.Random();

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
