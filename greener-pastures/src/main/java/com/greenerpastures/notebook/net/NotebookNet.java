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
 * Notebook console networking - the shared server↔client <b>sync layer</b> (NOTEBOOK_INTERACTIVE_SPEC §2).
 *
 * <p>C2S {@link NotebookRequestC2S} asks for a tab's data; the server replies with the always-on
 * {@link NotebookStatusS2C} (Data balance + GPU item-count + Daemon-on) and, later, per-tab payloads.
 * Registered from the common entrypoint so the codecs exist on both sides and the receiver runs server-side.
 */
public final class NotebookNet {
    private NotebookNet() {}

    private static final Gson GSON = new Gson();

    /** Per-player last prefetch-sweep time (ms) - the console poll re-warms the client cache at most 1×/min. */
    private static final Map<UUID, Long> lastPrefetch = new HashMap<>();

    /** Per-player, per-channel LAST payload actually sent - the server-side change gate (perf-audit R3 S2).
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

    /** Player left - drop their gate/prefetch state (unbounded-per-player maps on 24/7 servers, R3 #5/F11). */
    private static final Map<UUID, Long> lastBiobankFlatten = new java.util.concurrent.ConcurrentHashMap<>();

    /** Live Game Corner boards - deliberately in-memory only: a relog mid-round deals a fresh board at
     *  the same level (also closes "peek then relog" save-scum). Ledger (level + daily cap) persists. */
    private static final Map<UUID, com.greenerpastures.arcade.VoltorbFlip.Board> arcadeBoards =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, com.greenerpastures.arcade.Treeline.Round> treelineRounds =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, com.greenerpastures.arcade.TopDeck.Round> topdeckRounds =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, com.greenerpastures.arcade.VibeCheck.Round> vibeRounds =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Live QUICK CLAW rounds + their server-clock start (nanoTime) - the judge\u2019s stopwatch. */
    private static final Map<UUID, Object[]> tagRounds =   // {SprintTag.Round, Long startNanos}
            new java.util.concurrent.ConcurrentHashMap<>();
    /** Last SLOTS spin per player, seq-numbered so the client can animate each fresh pull. */
    private static final Map<UUID, long[]> slotsLast =    // {seq, bet, paid, f0, f1, f2}
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void onDisconnect(UUID player) {
        lastPush.remove(player);
        lastPrefetch.remove(player);
        augTargetSlot.remove(player);
        daemonTargetSlot.remove(player);
        lastBiobankFlatten.remove(player);
        arcadeBoards.remove(player);
        treelineRounds.remove(player);
        topdeckRounds.remove(player);
        slotsLast.remove(player);
        vibeRounds.remove(player);
        tagRounds.remove(player);
    }

    /** A TOP DECK wager is debited at deal time, so a live round dying with the connection must
     *  refund it - and refunding is scum-proof: nothing secret was revealed, and ladder progress
     *  (worth MORE than the wager) is what the relog forfeits. */
    private static void refundLiveTopdeck(UUID player, MinecraftServer server) {
        var round = topdeckRounds.get(player);
        if (round == null || round.over) return;
        com.greenerpastures.arcade.ArcadeStore.get(server).refund(player, arcadeToday(), round.wager);
        GpLog.i("arcade", "td_refund", "player", player.toString(), "wager", round.wager);
    }

    /** Reset per-server-session state - called on SERVER_STARTED (a new SP world shares the JVM). */
    public static void resetSession() { lastPrefetch.clear(); lastPush.clear(); augTargetSlot.clear(); daemonTargetSlot.clear(); lastBiobankFlatten.clear(); arcadeBoards.clear(); treelineRounds.clear(); topdeckRounds.clear(); slotsLast.clear(); vibeRounds.clear(); tagRounds.clear(); }

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
        PayloadTypeRegistry.playS2C().register(NotebookLoomS2C.ID, NotebookLoomS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookDashboardS2C.ID, NotebookDashboardS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookGoalsS2C.ID, NotebookGoalsS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(NotebookGoalC2S.ID, NotebookGoalC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookPastureExtraS2C.ID, NotebookPastureExtraS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookAugmenterMetaS2C.ID, NotebookAugmenterMetaS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookRitualsS2C.ID, NotebookRitualsS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookSpecimensS2C.ID, NotebookSpecimensS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookArcadeS2C.ID, NotebookArcadeS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookTreelineS2C.ID, NotebookTreelineS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookTopdeckS2C.ID, NotebookTopdeckS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookSlotsS2C.ID, NotebookSlotsS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookVibeS2C.ID, NotebookVibeS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(NotebookTagS2C.ID, NotebookTagS2C.CODEC);
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
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            refundLiveTopdeck(handler.getPlayer().getUuid(), server);
            onDisconnect(handler.getPlayer().getUuid());
        });
    }

    /** Push every known (snapshotted) pasture's config + graph for {@code player} - pre-warms the client cache.
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
            pushPastureConfig(player, pos, false);   // prefetch shape: no snapshot capture (stats ride along - see below)
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
                pushLoom(player);
                pushRituals(player);
                pushSpecimens(player);
                pushArcade(player);
                pushTreeline(player);
                pushTopdeck(player);
                pushSlots(player);
                pushVibe(player);
                pushTag(player);
                long nowMs = System.currentTimeMillis();
                Long last = lastPrefetch.get(player.getUuid());
                if (last == null || nowMs - last > 60_000L) {   // re-warm the pasture-config cache at most 1×/min
                    lastPrefetch.put(player.getUuid(), nowMs);
                    prefetchConfigs(player);
                }
            }
        });
    }

    /** Send the viewing player's recent egg-ingest feed (kept/voided + filter) + totals - the console Log view. */
    public static void pushEggLog(ServerPlayerEntity player) {
        List<NotebookEggLogS2C.Entry> out = new ArrayList<>();
        for (EggLog.Entry e : EggLog.recent(player.getUuid())) out.add(new NotebookEggLogS2C.Entry(e.species(), e.voided(), e.filter()));
        sendGated(player, "egglog", new NotebookEggLogS2C(EggLog.kept(player.getUuid()), EggLog.voided(player.getUuid()), out));
    }

    /** Send the viewing player's Inbox (dismissible notifications - catch-up pings etc.) for the Inbox tab. */
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
        // The server-press DONATION FEED (global, 24h rolling window, not dismissible) rides the same
        // channel as its own section - the content gate below means a new donation reaches every viewer
        // on their next 1s poll, with zero chat noise.
        JsonArray donations = new JsonArray();
        for (com.greenerpastures.notify.DonationFeed.Entry e : com.greenerpastures.notify.DonationFeed.entries()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", e.id());
            o.addProperty("t", e.atMs());
            o.addProperty("who", e.who());
            o.addProperty("species", e.species());
            o.addProperty("eggs", e.eggs());
            o.addProperty("mult", e.mult());
            o.addProperty("tierUp", e.tierUp());
            donations.add(o);
        }
        root.add("donations", donations);
        sendGated(player, "notifs", new NotebookNotifsS2C(GSON.toJson(root)));
    }

    /** The LOOM tab (Deuce, 2026-07-20 - the Soul Tether's own bench): every tether in the player's main
     *  inventory + the inscription catalog (non-selector functions × tiers I-III with cost/amp/burn).
     *  Data balance deliberately NOT included - the UI reads it off the status channel, so this JSON only
     *  changes (and only re-pushes) when the tethers themselves do. */
    public static void pushLoom(ServerPlayerEntity player) {
        JsonObject root = new JsonObject();
        JsonArray tethers = new JsonArray();
        var main = player.getInventory().main;
        for (int i = 0; i < main.size(); i++) {
            ItemStack s = main.get(i);
            if (!(s.getItem() instanceof com.greenerpastures.economy.SoulTetherItem)) continue;
            com.greenerpastures.economy.Tether t = s.get(com.greenerpastures.economy.DarkEconomy.TETHER);
            JsonObject o = new JsonObject();
            o.addProperty("slot", i);
            o.addProperty("count", s.getCount());
            o.addProperty("fn", t == null ? "" : t.function());
            o.addProperty("tier", t == null ? 0 : t.tier());
            if (s.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME))
                o.addProperty("name", s.getName().getString());
            tethers.add(o);
        }
        root.add("tethers", tethers);
        JsonArray catalog = new JsonArray();
        for (com.greenerpastures.economy.AugmentFunction f : com.greenerpastures.economy.AugmentFunction.values()) {
            // Selectors (a choice can't be amplified) + retired targets (EV: no consumers since the
            // spread rework · IV Floor: rounding jank) never appear on the Loom - one gate, one truth.
            if (!f.tetherable()) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id);
            o.addProperty("label", f.label);
            o.addProperty("cls", f.cls == com.greenerpastures.economy.TetherClass.QUALITY ? "quality" : "throughput");
            JsonArray tiers = new JsonArray();
            for (int t = 1; t <= com.greenerpastures.economy.SoulTether.MAX_TIER; t++) {
                com.greenerpastures.economy.SoulTether st = new com.greenerpastures.economy.SoulTether(f.id, f.cls, t);
                JsonObject ti = new JsonObject();
                ti.addProperty("tier", t);
                ti.addProperty("cost", com.greenerpastures.economy.TetherEconomics.inscribeCost(t));
                ti.addProperty("boost", f.boostLabel(t));   // ONE formatter (AugmentFunction) - UI just prints it
                ti.addProperty("rent", String.format("%.2f", st.upkeepCentiPerSecond() / 100.0));   // Data/s
                tiers.add(ti);
            }
            o.add("tiers", tiers);
            catalog.add(o);
        }
        root.add("catalog", catalog);
        JsonArray refunds = new JsonArray();   // wipeRefund by current tier 0..3 - the UI shows honest net costs
        for (int t = 0; t <= com.greenerpastures.economy.SoulTether.MAX_TIER; t++) {
            refunds.add(com.greenerpastures.economy.TetherEconomics.wipeRefund(t));
        }
        root.add("refunds", refunds);
        sendGated(player, "loom", new NotebookLoomS2C(GSON.toJson(root)));
    }

    /** Loom rename (Deuce, 2026-07-20: "name soul tethers just like kernels, otherwise it gets hard to
     *  keep track of whats what") - same sanitize + CUSTOM_NAME contract as {@link #renameHeldKernel},
     *  but addressed by inventory slot since the Loom is where you're already looking at them. */
    private static void renameTether(ServerPlayerEntity player, String name, int invSlot) {
        var main = player.getInventory().main;
        if (invSlot < 0 || invSlot >= main.size()) return;
        ItemStack stack = main.get(invSlot);
        if (!(stack.getItem() instanceof com.greenerpastures.economy.SoulTetherItem)) return;
        String clean = name == null ? "" : name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (clean.length() > 32) clean = clean.substring(0, 32);
        if (clean.isEmpty()) stack.remove(net.minecraft.component.DataComponentTypes.CUSTOM_NAME);
        else stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                net.minecraft.text.Text.literal(clean).styled(st -> st.withItalic(false)));
        player.getInventory().markDirty();
        GpLog.i("loom", "rename", "player", player.getUuid().toString(), "slot", Integer.toString(invSlot), "name", clean);
    }

    /** Loom inscription: write {@code [function · tier]} onto ONE Soul Tether in main-inventory slot
     *  {@code invSlot}, paid in Data via {@link com.greenerpastures.economy.TetherInscription} (book-style:
     *  re-inscribing refunds half the old tier first, so flipping never profits). A stacked blank is split
     *  so exactly one tether is written - a component write on a stack of 16 would be a 16-for-1 dupe. */
    private static void inscribeTether(ServerPlayerEntity player, String arg, int invSlot) {
        MinecraftServer server = player.getServer();
        if (server == null || arg == null || arg.isBlank()) return;
        var main = player.getInventory().main;
        if (invSlot < 0 || invSlot >= main.size()) return;
        ItemStack stack = main.get(invSlot);
        if (!(stack.getItem() instanceof com.greenerpastures.economy.SoulTetherItem)) return;
        String[] parts = arg.split(":", 2);
        String fn = parts[0];
        int tier = 0;
        if (parts.length > 1) { try { tier = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { } }
        boolean wipe = tier <= 0 || fn.isBlank() || "wipe".equals(fn);
        if (!wipe) {
            com.greenerpastures.economy.AugmentFunction f = com.greenerpastures.economy.AugmentFunction.byId(fn);
            if (f == null || !f.tetherable()) return;   // unknown / selector / retired (EV, IV Floor) - refused
            tier = Math.min(tier, com.greenerpastures.economy.SoulTether.MAX_TIER);
        }
        com.greenerpastures.economy.DataStore data = com.greenerpastures.economy.DataStore.get(server);
        long balance = data.balanceOf(player.getUuid());
        com.greenerpastures.economy.TetherInscription.Result res = com.greenerpastures.economy.TetherInscription
                .inscribe(stack.get(com.greenerpastures.economy.DarkEconomy.TETHER), wipe ? "" : fn, wipe ? 0 : tier, balance);
        if (!res.ok()) {
            player.sendMessage(net.minecraft.text.Text.literal(
                    "§c[Greener Pastures]§r Not enough Data for that inscription."), false);
            return;
        }
        if (res.dataDelta() > 0 && !data.tryDebit(player.getUuid(), res.dataDelta())) return;   // race-safe re-check
        if (res.dataDelta() < 0) data.credit(player.getUuid(), -res.dataDelta());
        ItemStack target = stack.getCount() > 1 ? stack.split(1) : stack;   // one tether per inscription
        target.set(com.greenerpastures.economy.DarkEconomy.TETHER, res.tether());
        if (target != stack) player.getInventory().offerOrDrop(target);
        player.getInventory().markDirty();
        String what = wipe ? "wiped back to blank" : "inscribed: "
                + com.greenerpastures.economy.AugmentFunction.byId(fn).label + " · Tier " + tier;
        String cost = res.dataDelta() == 0 ? "" : res.dataDelta() > 0
                ? " (-" + String.format("%,d", res.dataDelta()) + " Data)"
                : " (+" + String.format("%,d", -res.dataDelta()) + " Data back)";
        player.sendMessage(net.minecraft.text.Text.literal("§a[Greener Pastures]§r Soul Tether " + what + cost + "."), false);
        GpLog.i("loom", wipe ? "wipe" : "inscribe", "player", player.getUuid().toString(),
                "fn", wipe ? "-" : fn, "tier", Integer.toString(wipe ? 0 : tier),
                "delta", Long.toString(res.dataDelta()));
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
        MinecraftServer server = player.getServer();
        if (server != null) {                                     // MissingNo. odometer (1 summon / 1M lifetime rendered)
            DataStore ds = DataStore.get(server);
            long life = ds.lifetimeEarnedOf(id);
            o.addProperty("lifetimeEarned", life);
            o.addProperty("mnClaimable", com.greenerpastures.glitch.MissingnoMath.claimable(life, ds.missingnoClaimedOf(id)));
            o.addProperty("mnProgress", com.greenerpastures.glitch.MissingnoMath.progressToNext(life));
        }
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
                        player.sendMessage(Text.literal("§c[Greener Pastures]§r No such species: \"" + species + "\" - goal not set."), false);
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
                case NotebookActionC2S.WITHDRAW -> { withdrawEgg(player, p.amount()); pushBiobank(player, true); }
                case NotebookActionC2S.COMPRESS_EGGS -> { compressEggs(player, p.arg()); pushBiobank(player, true); }
                case NotebookActionC2S.COMPRESS_SERVER -> { donateEggs(player, p.arg()); pushBiobank(player, true); }
                case NotebookActionC2S.INSCRIBE_TETHER -> { inscribeTether(player, p.arg(), p.amount()); pushLoom(player); pushStatus(player); }
                case NotebookActionC2S.RENAME_TETHER -> { renameTether(player, p.arg(), p.amount()); pushLoom(player); }
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
                case NotebookActionC2S.SUMMON_MISSINGNO -> { summonMissingno(player); pushDashboard(player); }
                case NotebookActionC2S.ARCADE_NEW -> { arcadeNew(player); pushArcade(player); }
                case NotebookActionC2S.ARCADE_FLIP -> { arcadeFlip(player, p.amount()); pushArcade(player); pushStatus(player); }
                case NotebookActionC2S.ARCADE_CASHOUT -> { arcadeCashout(player); pushArcade(player); pushStatus(player); }
                case NotebookActionC2S.TREELINE_NEW -> { treelineNew(player); pushTreeline(player); }
                case NotebookActionC2S.TREELINE_SEARCH -> { treelineSearch(player, p.amount()); pushTreeline(player); pushStatus(player); }
                case NotebookActionC2S.SHOP_BUY -> { shopBuy(player, p.amount(), p.arg()); pushArcade(player); }
                case NotebookActionC2S.TOPDECK_NEW -> { topdeckNew(player, p.amount()); pushTopdeck(player); pushArcade(player); }
                case NotebookActionC2S.TOPDECK_FLIP -> { topdeckFlip(player, p.amount()); pushTopdeck(player); pushArcade(player); }
                case NotebookActionC2S.TOPDECK_MERCY -> { topdeckMercy(player); pushTopdeck(player); }
                case NotebookActionC2S.TOPDECK_MERCY_PICK -> { topdeckMercyPick(player, p.arg()); pushTopdeck(player); pushArcade(player); }
                case NotebookActionC2S.TOPDECK_CASHOUT -> { topdeckCashout(player); pushTopdeck(player); pushArcade(player); }
                case NotebookActionC2S.SLOTS_SPIN -> { slotsSpin(player, p.amount()); pushSlots(player); pushArcade(player); }
                case NotebookActionC2S.VIBE_NEW -> { vibeRounds.put(player.getUuid(), com.greenerpastures.arcade.VibeCheck.deal(new java.util.Random())); pushVibe(player); }
                case NotebookActionC2S.VIBE_DRAW -> { vibeDraw(player); pushVibe(player); pushArcade(player); }
                case NotebookActionC2S.VIBE_CASH -> { vibeCash(player); pushVibe(player); pushArcade(player); }
                case NotebookActionC2S.TAG_NEW -> { tagNew(player); pushTag(player); }
                case NotebookActionC2S.TAG_CLICK -> { tagClick(player); pushTag(player); pushArcade(player); }
                case NotebookActionC2S.HR_BUY -> { highRollerBuy(player, p.amount()); pushArcade(player); }
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
     *  for this item and place stacks into slots ourselves - never via {@code insertStack}, whose return other
     *  mods can mixin-hijack (a backpack "absorbed" 3k ink sacs by reporting fit-into-nowhere). Storage is
     *  debited only for what we physically placed, so nothing can ever be destroyed. */
    private static void pull(ServerPlayerEntity player, String itemId, int mode) {
        MinecraftServer server = player.getServer();
        if (server == null || itemId == null || itemId.isEmpty()) return;
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == Items.AIR) {
            player.sendMessage(Text.literal("§c[Greener Pastures]§r That item no longer exists in this world (a mod or datapack changed) - it can't be withdrawn."), false);
            return;
        }
        NotebookStore store = NotebookStore.get(server);
        long have = store.storageOf(player.getUuid()).count(itemId);
        if (have <= 0) return;
        var main = player.getInventory().main;
        ItemStack probe = new ItemStack(item);
        int maxStack = probe.getMaxCount();

        long capacity = 0;                                   // real room in MAIN slots only - counted by us
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
            player.sendMessage(net.minecraft.text.Text.literal("§c[Greener Pastures]§r Inventory full - nothing pulled."), false);
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
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Not enough GPU - need "
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
        // #37 - per-snapshot ⚠ badge flags, from registry-side state (works for unloaded chunks; parent/bank
        // checks need the live block entity and only run for loaded ones).
        PastureRegistry reg = PastureRegistry.get(server);
        Map<String, ServerWorld> dims = new HashMap<>();   // dim string → world, resolved ONCE per push (R3 F4)
        for (ServerWorld w : server.getWorlds()) dims.put(w.getRegistryKey().getValue().toString(), w);
        JsonObject health = new JsonObject();
        try (var span = com.greenerpastures.core.GpProf.begin("net.health_pass")) {
            java.util.List<PastureSnapshot> ghosts = null;
            for (PastureSnapshot s : snaps) {
                BlockPos pos = BlockPos.fromLong(s.pos());
                PastureData pd = reg.get(s.dim(), pos);
                if (pd == null) {
                    // review U13: a destroyed pasture's snapshot lingered in the tab forever with stale
                    // counts. No registry record = the pasture is gone - prune the ghost from the store.
                    (ghosts != null ? ghosts : (ghosts = new java.util.ArrayList<>())).add(s);
                    continue;
                }
                String csv = PastureHealth.idsCsv(gatherHealth(server, dims.get(s.dim()), pos, pd));
                if (!csv.isEmpty()) health.addProperty(s.dim() + "|" + s.pos(), csv);
            }
            if (ghosts != null) {
                for (PastureSnapshot g : ghosts) PastureSnapshotStore.get(server).removeAt(g.dim(), g.pos());
                java.util.Set<Long> gone = new java.util.HashSet<>();
                for (PastureSnapshot g : ghosts) gone.add(g.pos());
                snaps = snaps.stream().filter(s2 -> !gone.contains(s2.pos())).toList();
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

    /** Health flags with a (possibly null) live block entity in hand - the pure core does the deciding.
     *  Species come straight off the tether list (NOT {@code rosterOf} - no per-mon stats JSON on a 1/s poll). */
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
        // Bucket occupancy decides the line chips: a line is only REAL with two live members; exactly one
        // member = a broken line (partner escaped/released, stale id pruned) - surfaced, never silent.
        java.util.Map<Integer, Integer> occ = new java.util.HashMap<>();
        for (Integer b : pd.pairings.values()) occ.merge(b, 1, Integer::sum);
        boolean completeLine = occ.values().stream().anyMatch(c -> c >= 2);
        boolean brokenLine = occ.values().stream().anyMatch(c -> c == 1);
        return PastureHealth.evaluate(pd.owner != null, pd.tier() != null, monCount, completeLine, brokenLine,
                pd.eggQueue.isFull(), fullSpecies);
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

    /** Claim one owed MissingNo. (Dashboard button): entitlement = lifetime rendered / 1M − already claimed.
     *  Lands in the party (PC overflow); both full → refuse WITHOUT burning the claim. */
    private static void summonMissingno(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        try {
            DataStore ds = DataStore.get(server);
            int claimable = com.greenerpastures.glitch.MissingnoMath.claimable(
                    ds.lifetimeEarnedOf(player.getUuid()), ds.missingnoClaimedOf(player.getUuid()));
            if (claimable <= 0) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r The glitch requires a million rendered - keep feeding the Daemon."), false);
                return;
            }
            com.cobblemon.mod.common.pokemon.Pokemon mon = com.greenerpastures.glitch.Missingno.create();
            boolean toParty = com.cobblemon.mod.common.util.PlayerExtensionsKt.party(player).add(mon);
            boolean accepted = toParty || com.cobblemon.mod.common.util.PlayerExtensionsKt.pc(player).add(mon);
            if (!accepted) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Party and PC are full - the glitch has nowhere to manifest."), false);
                return;
            }
            ds.claimMissingno(player.getUuid());
            player.sendMessage(Text.literal(toParty
                    ? "§d§kAB§r §5M̸i̷s̶s̴i̵n̷g̸N̵o̶. joins your party.§r §d§kAB§r"
                    : "§d§kAB§r §5M̸i̷s̶s̴i̵n̷g̸N̵o̶. manifested in your PC§r §7(party was full)§r. §d§kAB§r"), false);
            com.greenerpastures.notify.Inbox.push(player.getUuid(), "▓", "MissingNo. manifested - one million rendered.");
            GpLog.i("missingno", "summon", "player", player.getUuid().toString(),
                    "lifetime", Long.toString(ds.lifetimeEarnedOf(player.getUuid())),
                    "claimed", Integer.toString(ds.missingnoClaimedOf(player.getUuid())));
        } catch (Throwable t) {
            GpLog.w("missingno", "summon_fail", "err", String.valueOf(t));
            player.sendMessage(Text.literal("§c[Greener Pastures]§r The glitch failed to manifest - nothing was consumed."), false);
        }
    }

    // ── Specimen Disks (mon compression v1 - Deuce, 2026-07-05) ─────────────────────────────────────────

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
     *  guaranteed landing slot, THEN remove from party, THEN mint into the pre-verified slot - with
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
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Could not archive - the party refused the removal."), false);
                return;
            }
            ItemStack blank = inv.main.get(blankSlot);
            blank.decrement(1);
            if (inv.main.get(blankSlot).isEmpty()) inv.main.set(blankSlot, written);
            else {
                int empty = inv.getEmptySlot();
                if (empty >= 0) inv.main.set(empty, written);
                else inv.offerOrDrop(written);   // race fallback - the mon is NEVER lost
            }
            player.sendMessage(Text.literal("§a[Greener Pastures]§r Archived §b" + mon.getSpecies().getName()
                    + "§r to a Specimen Disk."), false);
            GpLog.i("specimen", "compress", "player", player.getUuid().toString(),
                    "species", mon.getSpecies().getName(), "level", Integer.toString(mon.getLevel()),
                    "shiny", Boolean.toString(mon.getShiny()));
        } catch (Throwable t) {
            GpLog.w("specimen", "compress_fail", "err", String.valueOf(t));
            player.sendMessage(Text.literal("§c[Greener Pastures]§r Archive failed - nothing was changed."), false);
        }
    }

    // ── Multi-item targeting (backlog #5): with 2+ Kernels/Daemons in the inventory, the tabs used to
    // operate on whichever was found first. The client picks a target card; we remember the SLOT per player
    // (session-scoped: cleared on disconnect + SERVER_STARTED) and re-validate it on EVERY use - if the slot
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

    // ── GPU economy (§7.5 - now LIVE): baked constants, deliberately NO config (anti-p2w, same rule as
    // drop rates). Quality augments (shiny/IV/EV/breeding-meta) cost more than throughput ones; a Daemon
    // buff tier is a flat install fee on top of its ongoing Data drain. Re-picking a parameterized augment's
    // VALUE stays free (the augment was already bought); removal never refunds.
    private static final int GPU_QUALITY = 2;
    private static final int GPU_THROUGHPUT = 1;
    private static final int GPU_PER_BUFF_TIER = 2;

    private static int gpuCost(AugmentType at) {
        return at.function.cls == com.greenerpastures.economy.TetherClass.QUALITY ? GPU_QUALITY : GPU_THROUGHPUT;
    }

    /** Consume {@code n} GPUs from the player's MAIN inventory only (review u9: the offhand is a deliberate
     *  parking spot - spending from it surprised players). Manual decrement - never trust insertStack-style
     *  seams; mirrors pull(). Returns false (and consumes nothing) if they hold fewer than {@code n}. */
    private static boolean consumeGpu(ServerPlayerEntity player, int n) {
        if (n <= 0) return true;
        if (countGpu(player) < n) return false;
        PlayerInventory inv = player.getInventory();
        int left = n;
        for (var list : java.util.List.of(inv.main)) {
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
        for (AugmentType at : AugmentType.values()) n += AugmentType.slotsForLevel(at.installedLevelOn(kernel));
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
                int lvl = at.installedLevelOn(ref.stack());
                int nextSlots = AugmentType.slotsForLevel(Math.min(at.maxLevel(), lvl + 1));   // what the NEXT action occupies
                catalog.add(new NotebookAugmenterS2C.Aug(at.name(), at.effectSummary(), nextSlots,
                        lvl, gpuCost(at), at.maxLevel()));
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

    // The nature/ball catalogs are compile-time constants - build their JSON ONCE instead of 57 elements per
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
            player.sendMessage(Text.literal("§5⛧§r This Kernel is §8corrupted§r - it is beyond modification."), false);
            return;
        }
        if (at.parameterized() != (arg.index() > 0 || arg.ev() != null)) return;   // param augments need a value; plain ones must not carry one

        int curLevel = at.installedLevelOn(ref.stack());
        boolean installed = curLevel > 0;
        boolean upgrade = installed && !at.parameterized() && curLevel < at.maxLevel();   // I → II (Deuce: 1.5×, 3 slots total)
        if (installed && !at.parameterized() && !upgrade) return;                  // maxed non-param → no-dupe
        if (!installed || upgrade) {                                               // install OR upgrade: slot gate + GPU fee
            BreedingTier tier = ((BreedingUpgradeItem) ref.stack().getItem()).tier();
            int targetLevel = installed ? curLevel + 1 : 1;
            if (at == AugmentType.SPEED) {
                // review u3: Speed on an already-floored kernel spent GPU for literally nothing. Compare the
                // best-case interval before/after - if the 2.5-min floor eats the whole gain, refuse for free.
                long base = com.greenerpastures.pasture.breeding.CobbreedingBridge.maxBreedingIntervalTicks();
                long before = com.greenerpastures.pasture.breeding.MultiPairBreeder.speedAdjustedInterval(
                        base, curLevel == 0 ? 0 : at.valueAt(curLevel), tier.baseSpeedFactor());
                long after = com.greenerpastures.pasture.breeding.MultiPairBreeder.speedAdjustedInterval(
                        base, at.valueAt(targetLevel), tier.baseSpeedFactor());
                if (after >= before) {
                    player.sendMessage(Text.literal("§e[Greener Pastures]§r This Kernel already breeds at the 2.5-minute floor - Speed "
                            + (installed ? "II" : "I") + " would change nothing. GPU saved."), false);
                    return;
                }
            }
            int slotsAfter = slotsUsed(ref.stack()) - AugmentType.slotsForLevel(curLevel)
                    + AugmentType.slotsForLevel(targetLevel);
            if (slotsAfter > tier.slots) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r No room - level " + targetLevel
                        + " needs " + AugmentType.slotsForLevel(targetLevel) + " slots total on this Kernel."), false);
                return;
            }
            int fee = gpuCost(at);
            if (!consumeGpu(player, fee)) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Not enough GPU - "
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
            default -> {
                int targetLevel = upgrade ? curLevel + 1 : 1;
                ref.writer().accept(at.apply(ref.stack(), at.storedValueFor(ref.stack(), targetLevel)));
                if (upgrade) detail = "level " + targetLevel;
            }
        }
        GpLog.i("notebook", "augment_apply", "player", player.getUuid().toString(), "type", at.name(),
                "value", detail.isEmpty() ? "-" : detail, "repick", installed && !upgrade, "upgrade", upgrade);
    }

    private static void removeAugment(ServerPlayerEntity player, String typeName) {
        KernelRef ref = targetKernel(player);
        if (ref == null) return;
        AugmentType at = augmentType(typeName);
        if (at == null || !at.appliesTo(ref.stack()) || !at.installedOn(ref.stack())) return;
        if (Boolean.TRUE.equals(ref.stack().get(GpComponents.CORRUPTED))) {
            player.sendMessage(Text.literal("§5⛧§r This Kernel is §8corrupted§r - it is beyond modification."), false);
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
     *  inventory - placed into a MAIN slot we picked ourselves (never {@code insertStack}; see {@link #pull}). */
    private static void withdrawEgg(ServerPlayerEntity player, int flatIndex) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var main = player.getInventory().main;
        int slot = -1;
        for (int i = 0; i < main.size(); i++) if (main.get(i).isEmpty()) { slot = i; break; }
        if (slot < 0) {                                   // no room - leave the egg in the BioBank (never destroy)
            player.sendMessage(net.minecraft.text.Text.literal("§c[Greener Pastures]§r Inventory full - the egg stays in the BioBank."), false);
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

    /** The Compression press (Deuce, 2026-07-19): feed 100 banked eggs of one species → a permanent, stacking
     *  +5% drop-proc multiplier for that species across every pasture the player owns. Shinies are never
     *  eligible (SACRED, the same rule as the egg pipeline) and the WORST eggs (lowest total IV) feed first,
     *  so the press eats the culls and the keepers stay banked. All-or-nothing: under 100 eligible = no-op. */
    private static void compressEggs(ServerPlayerEntity player, String species) {
        MinecraftServer server = player.getServer();
        if (server == null || species == null || species.isBlank()) return;
        int eaten = sacrificeWorst(server, player, species);
        if (eaten <= 0) return;
        com.greenerpastures.biobank.CompressionStore comp = com.greenerpastures.biobank.CompressionStore.get(server);
        comp.record(player.getUuid(), species, eaten);
        double mult = comp.get(player.getUuid()).multiplierOf(species);
        player.sendMessage(net.minecraft.text.Text.literal("§a[Greener Pastures]§r COMPRESSED: " + eaten + " "
                + capFirst(species) + " eggs pressed into a permanent +5% drop bonus - now ×"
                + String.format("%.2f", mult) + " on all your pastures."), false);
        GpLog.i("compression", "press", "player", player.getUuid().toString(), "species", species,
                "eggs", Integer.toString(eaten), "mult", String.format("%.2f", mult));
    }

    /** Donate 100 eggs into the COMMUNAL server press (Deuce, 2026-07-19): every 1000 pooled eggs of a
     *  species = a further +1% drop rate for EVERYONE's pastures - a "more" multiplier the harvest applies
     *  on top of each owner's personal ledger. Same feed rules as the personal press (worst first, shinies
     *  never, all-or-nothing). A tier crossing is broadcast: the whole server should see the bar fill. */
    private static void donateEggs(ServerPlayerEntity player, String species) {
        MinecraftServer server = player.getServer();
        if (server == null || species == null || species.isBlank()) return;
        int eaten = sacrificeWorst(server, player, species);
        if (eaten <= 0) return;
        com.greenerpastures.biobank.CompressionStore comp = com.greenerpastures.biobank.CompressionStore.get(server);
        long tiersBefore = comp.server().pressesOf(species);
        comp.recordServer(species, eaten);
        boolean tierUp = comp.server().pressesOf(species) > tiersBefore;
        double mult = comp.server().multiplierOf(species);
        long toNext = comp.server().toNextPress(species);
        // No chat broadcast (Deuce, 2026-07-19): donations land in the Inbox tab's DONATION FEED instead -
        // a global 24h rolling window anyone can go read. The donator still gets their private confirm.
        com.greenerpastures.notify.DonationFeed.push(player.getName().getString(), species, eaten, mult, tierUp);
        player.sendMessage(net.minecraft.text.Text.literal("§a[Greener Pastures]§r DONATED: " + eaten + " "
                + capFirst(species) + " eggs to the server press - " + (tierUp
                ? "that pull tipped it to ×" + String.format("%.2f", mult) + " for everyone!"
                : toNext + " more to the next +1% for everyone (now ×" + String.format("%.2f", mult) + ").")), false);
        GpLog.i("compression", "donate", "player", player.getUuid().toString(), "species", species,
                "eggs", Integer.toString(eaten), "server_mult", String.format("%.2f", mult),
                "to_next", Long.toString(toNext), "tier_up", Boolean.toString(tierUp));
    }

    /** The shared press feed: remove exactly one BATCH of {@code species} from the player's BioBank -
     *  worst total-IV first, shinies never eligible, all-or-nothing. Returns eggs eaten (0 = refused,
     *  with the reason messaged). */
    private static int sacrificeWorst(MinecraftServer server, ServerPlayerEntity player, String species) {
        java.util.function.Predicate<ItemStack> eligible = egg -> {
            EggCard c = EggReader.card(egg);
            return c == null || !c.shiny();
        };
        java.util.Comparator<ItemStack> worstFirst = java.util.Comparator.comparingInt(egg -> {
            EggCard c = EggReader.card(egg);
            return c == null ? -1 : c.ivTotal();      // unreadable = worthless = first into the press
        });
        int eaten = BioBankStore.get(server).sacrifice(player.getUuid(), species,
                com.greenerpastures.biobank.CompressionLedger.BATCH, eligible, worstFirst);
        if (eaten < com.greenerpastures.biobank.CompressionLedger.BATCH) {
            player.sendMessage(net.minecraft.text.Text.literal("§c[Greener Pastures]§r The press needs "
                    + com.greenerpastures.biobank.CompressionLedger.BATCH + " non-shiny "
                    + capFirst(species) + " eggs in the BioBank."), false);
            return 0;
        }
        return eaten;
    }

    private static String capFirst(String s) {
        return s == null || s.isEmpty() ? "?" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "45" not "45.0" - whole magnitudes print clean in the TETHER FX chips. */
    private static String trimNum(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** Centipercent → percent with two decimals ("250" → "2.50"). */
    private static String centiPct(double centi) {
        return String.format("%.2f", centi / 100.0);
    }

    // ── pasture config (the React right-click-a-pasture screen; replaces the owo PastureScreen) ───────────────

    /** Build + push the focused pasture's full editable config (name · tier · link · maxPairs · roster). */
    public static void pushPastureConfig(ServerPlayerEntity player, BlockPos pos) {
        pushPastureConfig(player, pos, true);
    }

    /** {@code full=false} is the PREFETCH shape (R3 F6): cache-warming only - skip the snapshot re-capture and
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
            // ALWAYS with stats. The R3 F6 stats-less prefetch shape ping-ponged with the full shape through
            // the change gate: the 1-min prefetch sweep kept overwriting live IVs/genders with zeros, so the
            // graph showed 0/186 parents and a false "this pair can't breed" (Deuce, live 2026-07-07). At
            // 1/min x <=16 loaded pastures the stats read is cheap; only the snapshot capture stays full-gated.
            List<MonEntry> roster = CobbreedingBridge.rosterOf(pasture, pd, true);
            long key = pos.asLong();
            sendGated(player, "cfg:" + key, new NotebookPastureConfigS2C(
                    key, pd.name, tier == null ? "" : tier.name(), linked, pd.maxPairs(), roster));
            sendGated(player, "graph:" + key, new NotebookGraphS2C(key, pd.graphJson == null ? "" : pd.graphJson));
            sendGated(player, "extra:" + key, new NotebookPastureExtraS2C(key, pastureExtraJson(server, pd, pasture)));
        }
    }

    // ── Game Corner (Voltorb Flip - Deuce, 2026-07-06). Server-authoritative; payouts NEVER touch
    // the MissingNo odometer (DataStore.credit, the neutral path); the house pays ≤1 kB/day. ─────────

    private static String arcadeToday() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
    }

    private static void arcadeNew(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var ledger = com.greenerpastures.arcade.ArcadeStore.get(server).of(player.getUuid(), arcadeToday());
        var board = com.greenerpastures.arcade.VoltorbFlip.generate(ledger.level, new java.util.Random());
        arcadeBoards.put(player.getUuid(), board);
        GpLog.d("arcade", "new", "player", player.getUuid().toString(), "level", board.level);
    }

    private static void arcadeFlip(ServerPlayerEntity player, int tile) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var board = arcadeBoards.get(player.getUuid());
        if (board == null || board.over) return;
        var store = com.greenerpastures.arcade.ArcadeStore.get(server);
        var ledger = store.of(player.getUuid(), arcadeToday());
        var flip = com.greenerpastures.arcade.VoltorbFlip.flip(board, tile);
        if (!flip.wasNew()) return;
        if (flip.bust()) {
            int next = com.greenerpastures.arcade.VoltorbFlip.nextLevel(board.level, false, true);
            store.record(player.getUuid(), arcadeToday(), 0, next);
            GpLog.i("arcade", "bust", "player", player.getUuid().toString(), "level", board.level, "to", next);
        } else if (flip.cleared()) {
            settleArcade(player, server, board, true);
        } else {
            GpLog.d("arcade", "flip", "player", player.getUuid().toString(), "value", flip.value(), "coins", board.coins);
        }
    }

    private static void arcadeCashout(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var board = arcadeBoards.get(player.getUuid());
        if (board == null || board.over || board.coins <= 0) return;
        board.over = true;
        settleArcade(player, server, board, false);
    }

    /** Bank the pot: clamp into the day's kB, credit (NEUTRAL - never the odometer), move the level. */
    private static void settleArcade(ServerPlayerEntity player, MinecraftServer server,
                                     com.greenerpastures.arcade.VoltorbFlip.Board board, boolean cleared) {
        var store = com.greenerpastures.arcade.ArcadeStore.get(server);
        var ledger = store.of(player.getUuid(), arcadeToday());
        long pay = com.greenerpastures.arcade.VoltorbFlip.payable(board.coins, ledger.earnedToday);
        // Winnings are GAME CORNER COINS (Deuce 2026-07-06) - the arcade never mints Data.
        int next = com.greenerpastures.arcade.VoltorbFlip.nextLevel(board.level, cleared, false);
        store.record(player.getUuid(), arcadeToday(), pay, next);
        boolean capped = pay < board.coins;
        GpLog.i("arcade", cleared ? "clear" : "cashout", "player", player.getUuid().toString(),
                "level", board.level, "coins", board.coins, "paid", pay, "capped", capped, "to", next);
        if (capped) {
            player.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a76[Game Corner]\u00a7r the house's ledger closes at one kilobyte a day - "
                    + pay + " of " + board.coins + " paid. Come back tomorrow."), false);
        }
    }

    /** The Game Corner tab: chips + flipped tiles only mid-round; the full board once it's over. */
    public static void pushArcade(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var ledger = com.greenerpastures.arcade.ArcadeStore.get(server).of(player.getUuid(), arcadeToday());
        var board = arcadeBoards.get(player.getUuid());
        JsonObject root = new JsonObject();
        root.addProperty("level", board != null ? board.level : ledger.level);
        root.addProperty("dailyLeft", com.greenerpastures.arcade.VoltorbFlip.DAILY_CAP <= 0
                ? -1 : Math.max(0, com.greenerpastures.arcade.VoltorbFlip.DAILY_CAP - ledger.earnedToday));   // -1 = uncapped
        root.addProperty("gcoins", ledger.coins);
        JsonObject shop = new JsonObject();
        long nowMs = System.currentTimeMillis();
        shop.addProperty("endsAt", com.greenerpastures.arcade.GameShop.windowEndsAt(nowMs));
        JsonArray offers = new JsonArray();
        boolean eggOk = com.greenerpastures.pasture.breeding.CobbreedingBridge.isAvailable();
        for (var w : com.greenerpastures.arcade.GameShop.offersFor(player.getUuid(), nowMs, eggOk, ledger.shopRolls)) {
            JsonObject o = new JsonObject();
            o.addProperty("id", w.itemId());        // the client bridge resolves the REAL item texture from this
            o.addProperty("name", w.name());
            o.addProperty("price", w.price());
            o.addProperty("count", w.count());
            offers.add(o);
        }
        shop.add("offers", offers);
        root.add("shop", shop);
        JsonArray hr = new JsonArray();
        for (var w : com.greenerpastures.arcade.HighRoller.CATALOG) {
            JsonObject o = new JsonObject();
            o.addProperty("id", w.itemId());
            o.addProperty("name", w.name());
            o.addProperty("price", w.price());
            hr.add(o);
        }
        root.add("highroller", hr);
        root.addProperty("playing", board != null && !board.over);
        root.addProperty("over", board != null && board.over);
        root.addProperty("cleared", board != null && board.cleared);
        root.addProperty("coins", board == null ? 0 : board.coins);
        if (board != null) {
            JsonArray rows = new JsonArray();
            JsonArray cols = new JsonArray();
            for (int i = 0; i < com.greenerpastures.arcade.VoltorbFlip.SIZE; i++) {
                JsonObject r = new JsonObject();
                r.addProperty("sum", board.rowSum(i));
                r.addProperty("volts", board.rowVoltorbs(i));
                rows.add(r);
                JsonObject c = new JsonObject();
                c.addProperty("sum", board.colSum(i));
                c.addProperty("volts", board.colVoltorbs(i));
                cols.add(c);
            }
            root.add("rows", rows);
            root.add("cols", cols);
            JsonArray tiles = new JsonArray();
            for (int i = 0; i < com.greenerpastures.arcade.VoltorbFlip.TILES; i++) {
                // mid-round: flipped values only, -1 for face-down. Over: reveal everything (HGSS style).
                tiles.add(board.isFlipped(i) || board.over ? board.tile(i) : -1);
            }
            root.add("tiles", tiles);
            JsonArray flippedArr = new JsonArray();
            for (int i = 0; i < com.greenerpastures.arcade.VoltorbFlip.TILES; i++) flippedArr.add(board.isFlipped(i));
            root.add("flipped", flippedArr);
        }
        sendGated(player, "arcade", new NotebookArcadeS2C(GSON.toJson(root)));
    }

    // ── TREELINE (Game Corner cabinet #2 - Deuce's artifact, 2026-07-06). Same house rules as
    // Voltorb Flip: server owns the secrets, payouts are credit() (odometer-neutral), uncapped era. ──

    private static void treelineNew(ServerPlayerEntity player) {
        treelineRounds.put(player.getUuid(), com.greenerpastures.arcade.Treeline.generate(new java.util.Random()));
        GpLog.d("arcade", "tl_new", "player", player.getUuid().toString());
    }

    private static void treelineSearch(ServerPlayerEntity player, int treeId) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var round = treelineRounds.get(player.getUuid());
        if (round == null || round.over) return;
        var sweep = com.greenerpastures.arcade.Treeline.search(round, treeId);
        switch (sweep.outcome()) {
            case FOUND -> {
                // coins, not Data (the arcade economy is a closed loop - only ITEMS leave the shop)
                com.greenerpastures.arcade.ArcadeStore.get(server).record(player.getUuid(), arcadeToday(), sweep.payout(),
                        com.greenerpastures.arcade.ArcadeStore.get(server).of(player.getUuid(), arcadeToday()).level);
                GpLog.i("arcade", "tl_found", "player", player.getUuid().toString(),
                        "sweepsUsed", com.greenerpastures.arcade.Treeline.CLICK_BUDGET - sweep.clicksLeft(), "paid", sweep.payout());
            }
            case LOST -> GpLog.i("arcade", "tl_lost", "player", player.getUuid().toString());
            default -> { }
        }
    }

    /** Redeem a shop shelf slot for Game Corner Coins. The ware is RE-DERIVED server-side from the
     *  current window + the player's purchase count - a stale client can't buy last window's stock -
     *  and items land through the manual capacity-aware path (never insertStack; full inventory
     *  refuses BEFORE the debit). Every successful buy bumps shopRolls, which refreshes the whole
     *  shelf (Deuce 2026-07-06); {@code expectedName} guards the click-vs-refresh race so a
     *  double-click can never buy whatever just rotated into the slot. */
    private static void shopBuy(ServerPlayerEntity player, int slot, String expectedName) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        long nowMs = System.currentTimeMillis();
        boolean eggOk = com.greenerpastures.pasture.breeding.CobbreedingBridge.isAvailable();
        var store = com.greenerpastures.arcade.ArcadeStore.get(server);
        long rolls = store.of(player.getUuid(), arcadeToday()).shopRolls;
        var ware = com.greenerpastures.arcade.GameShop.wareAt(player.getUuid(), nowMs, slot, eggOk, rolls);
        if (ware == null) return;
        if (expectedName != null && !expectedName.isEmpty() && !expectedName.equals(ware.name())) {
            player.sendMessage(net.minecraft.text.Text.literal("§6[Game Corner]§r the shelves just turned - check the new stock."), false);
            return;
        }
        ItemStack stack;
        if (com.greenerpastures.arcade.GameShop.MYSTERY_EGG_ID.equals(ware.itemId())) {
            stack = com.greenerpastures.pasture.breeding.CobbreedingBridge.shopMysteryEgg(new java.util.Random());
            if (stack == null) {
                player.sendMessage(net.minecraft.text.Text.literal("\u00a76[Game Corner]\u00a7r the egg machine jammed - your Coins are safe."), false);
                return;
            }
        } else {
            Item item = Registries.ITEM.get(Identifier.tryParse(ware.itemId()));
            if (item == net.minecraft.item.Items.AIR) {
                GpLog.w("arcade", "shop_unknown_item", "id", ware.itemId());
                return;
            }
            stack = new ItemStack(item, ware.count());
        }
        PlayerInventory inv = player.getInventory();
        int free = -1;
        for (int i = 0; i < inv.main.size(); i++) {
            if (inv.main.get(i).isEmpty()) { free = i; break; }
        }
        if (free < 0) {
            player.sendMessage(net.minecraft.text.Text.literal("\u00a76[Game Corner]\u00a7r no room in your pack - the counter holds your order."), false);
            return;
        }
        if (!store.trysSpend(player.getUuid(), arcadeToday(), ware.price())) {
            player.sendMessage(net.minecraft.text.Text.literal("\u00a76[Game Corner]\u00a7r not enough Coins - the machines await."), false);
            return;
        }
        inv.main.set(free, stack);
        inv.markDirty();
        store.bumpRolls(player.getUuid(), arcadeToday());   // every buy turns the shelves (window turn still applies)
        GpLog.i("arcade", "shop_buy", "player", player.getUuid().toString(), "item", ware.itemId(),
                "count", ware.count(), "price", ware.price(), "rolls", rolls + 1,
                "coinsLeft", store.of(player.getUuid(), arcadeToday()).coins);
    }

    /** High Roller Room purchase: fixed shelves, capacity-refuse BEFORE debit, special builds for
     *  the Prime Egg (Cobbreeding) and the Legend Disk (a legendary minted straight onto specimen
     *  media - tradeable, which makes it server currency; that is a feature). */
    private static void highRollerBuy(ServerPlayerEntity player, int slot) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var ware = com.greenerpastures.arcade.HighRoller.wareAt(slot);
        if (ware == null) return;
        ItemStack stack;
        String legendSpecies = null;
        switch (ware.itemId()) {
            case com.greenerpastures.arcade.HighRoller.PRIME_EGG_ID -> {
                stack = com.greenerpastures.pasture.breeding.CobbreedingBridge.shopMysteryEgg(
                        new java.util.Random(), com.greenerpastures.arcade.HighRoller.PRIME_EGG_PERFECT_IVS);
                if (stack == null) {
                    player.sendMessage(net.minecraft.text.Text.literal("§6[Game Corner]§r the incubator is offline (needs Cobbreeding) - your Coins are safe."), false);
                    return;
                }
            }
            case com.greenerpastures.arcade.HighRoller.LEGEND_DISK_ID -> {
                var mon = com.greenerpastures.pasture.breeding.CobbreedingBridge.mintLegendMon(new java.util.Random());
                if (mon == null) {
                    player.sendMessage(net.minecraft.text.Text.literal("§6[Game Corner]§r no legends answered the call - your Coins are safe."), false);
                    return;
                }
                stack = new ItemStack(com.greenerpastures.economy.GpItems.SPECIMEN_DISK);
                stack.set(GpComponents.SPECIMEN, mon.saveToNBT(player.getServerWorld().getRegistryManager(),
                        new net.minecraft.nbt.NbtCompound()));
                stack.set(GpComponents.SPECIMEN_SUMMARY, new com.greenerpastures.specimen.SpecimenSummary(
                        mon.getSpecies().getName(), mon.getLevel(), mon.getShiny(),
                        mon.getGender().name().toLowerCase(java.util.Locale.ROOT)));
                legendSpecies = mon.getSpecies().getName();
            }
            default -> {
                Item item = Registries.ITEM.get(Identifier.tryParse(ware.itemId()));
                if (item == net.minecraft.item.Items.AIR) {
                    GpLog.w("arcade", "hr_unknown_item", "id", ware.itemId());
                    return;
                }
                stack = new ItemStack(item, 1);
            }
        }
        PlayerInventory inv = player.getInventory();
        int free = -1;
        for (int i = 0; i < inv.main.size(); i++) {
            if (inv.main.get(i).isEmpty()) { free = i; break; }
        }
        if (free < 0) {
            player.sendMessage(net.minecraft.text.Text.literal("§6[Game Corner]§r no room in your pack - the counter holds your order."), false);
            return;
        }
        var store = com.greenerpastures.arcade.ArcadeStore.get(server);
        if (!store.trysSpend(player.getUuid(), arcadeToday(), ware.price())) {
            player.sendMessage(net.minecraft.text.Text.literal("§6[Game Corner]§r not enough Coins - the High Roller Room can wait."), false);
            return;
        }
        inv.main.set(free, stack);
        inv.markDirty();
        GpLog.i("arcade", "hr_buy", "player", player.getUuid().toString(), "item", ware.itemId(),
                "price", ware.price(), "legend", legendSpecies == null ? "-" : legendSpecies,
                "coinsLeft", store.of(player.getUuid(), arcadeToday()).coins);
    }

    // ── TOP DECK (Game Corner cabinet #3 - Deuce's pitch, 2026-07-06). Wager debited at deal,
    // secret draw never leaves the server, ladder 2x/6x/20x with cashout between rungs. ──

    private static void topdeckNew(ServerPlayerEntity player, int wager) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var live = topdeckRounds.get(player.getUuid());
        if (live != null && !live.over) {
            player.sendMessage(net.minecraft.text.Text.literal("\u00a76[Game Corner]\u00a7r finish the round on the table first."), false);
            return;
        }
        if (wager < com.greenerpastures.arcade.TopDeck.MIN_BET || wager > com.greenerpastures.arcade.TopDeck.MAX_BET) {
            player.sendMessage(net.minecraft.text.Text.literal("\u00a76[Game Corner]\u00a7r the house takes "
                    + com.greenerpastures.arcade.TopDeck.MIN_BET + " to " + com.greenerpastures.arcade.TopDeck.MAX_BET + " Coins a hand."), false);
            return;
        }
        var store = com.greenerpastures.arcade.ArcadeStore.get(server);
        if (!store.trysSpend(player.getUuid(), arcadeToday(), wager)) {
            player.sendMessage(net.minecraft.text.Text.literal("\u00a76[Game Corner]\u00a7r not enough Coins - the machines await."), false);
            return;
        }
        var round = com.greenerpastures.arcade.TopDeck.deal(
                com.greenerpastures.arcade.TopDeckPool.EMOTIONS, wager, new java.util.Random());
        if (round == null) {    // catalog bug, never expected - give the coins back rather than eat them
            store.refund(player.getUuid(), arcadeToday(), wager);
            return;
        }
        topdeckRounds.put(player.getUuid(), round);
        GpLog.i("arcade", "td_new", "player", player.getUuid().toString(), "wager", wager);
    }

    private static void topdeckFlip(ServerPlayerEntity player, int pos) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var round = topdeckRounds.get(player.getUuid());
        if (round == null || round.over) return;
        int stageBefore = round.stage;
        var outcome = com.greenerpastures.arcade.TopDeck.flip(round, pos,
                com.greenerpastures.arcade.TopDeckPool.EMOTIONS, new java.util.Random());
        switch (outcome) {
            case HIT -> {
                if (round.over) {   // top rung auto-cash
                    com.greenerpastures.arcade.ArcadeStore.get(server).record(player.getUuid(), arcadeToday(),
                            round.payout, com.greenerpastures.arcade.ArcadeStore.get(server).of(player.getUuid(), arcadeToday()).level);
                    GpLog.i("arcade", "td_top", "player", player.getUuid().toString(),
                            "wager", round.wager, "paid", round.payout);
                } else {
                    GpLog.i("arcade", "td_hit", "player", player.getUuid().toString(), "rung", stageBefore);
                }
            }
            case LOST -> GpLog.i("arcade", "td_miss", "player", player.getUuid().toString(),
                    "rung", stageBefore, "lost", round.wager);
            default -> { }
        }
    }

    private static void topdeckCashout(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var round = topdeckRounds.get(player.getUuid());
        if (com.greenerpastures.arcade.TopDeck.cashout(round) == com.greenerpastures.arcade.TopDeck.Outcome.CASHED) {
            com.greenerpastures.arcade.ArcadeStore.get(server).record(player.getUuid(), arcadeToday(),
                    round.payout, com.greenerpastures.arcade.ArcadeStore.get(server).of(player.getUuid(), arcadeToday()).level);
            GpLog.i("arcade", "td_cash", "player", player.getUuid().toString(),
                    "wager", round.wager, "paid", round.payout);
        }
    }

    private static void topdeckMercy(ServerPlayerEntity player) {
        var round = topdeckRounds.get(player.getUuid());
        var options = com.greenerpastures.arcade.TopDeck.mercyStart(round,
                com.greenerpastures.arcade.TopDeckPool.EMOTIONS, new java.util.Random());
        if (options != null) {
            GpLog.i("arcade", "td_mercy", "player", player.getUuid().toString(),
                    "species", round.species.get(round.mercyIndex), "options", options.size());
        }
    }

    private static void topdeckMercyPick(ServerPlayerEntity player, String emotion) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var round = topdeckRounds.get(player.getUuid());
        var outcome = com.greenerpastures.arcade.TopDeck.mercyPick(round, emotion);
        if (outcome == com.greenerpastures.arcade.TopDeck.Outcome.MERCY_WON) {
            com.greenerpastures.arcade.ArcadeStore.get(server).refund(player.getUuid(), arcadeToday(), round.wager);
            GpLog.i("arcade", "td_mercy_won", "player", player.getUuid().toString(), "refund", round.wager);
        } else if (outcome == com.greenerpastures.arcade.TopDeck.Outcome.MERCY_LOST) {
            GpLog.i("arcade", "td_mercy_lost", "player", player.getUuid().toString());
        }
    }

    /** TOP DECK state: the og 20 (species + the emotion each wore) are public - they fanned face-up.
     *  Mid-rung the client gets ONLY flipped positions' stranger faces; the survivor's position ships
     *  once the round settles. Mercy options ship only after MERCY starts. */
    public static void pushTopdeck(ServerPlayerEntity player) {
        var round = topdeckRounds.get(player.getUuid());
        JsonObject root = new JsonObject();
        root.addProperty("active", round != null && !round.over);
        root.addProperty("minBet", com.greenerpastures.arcade.TopDeck.MIN_BET);
        root.addProperty("maxBet", com.greenerpastures.arcade.TopDeck.MAX_BET);
        JsonArray ladder = new JsonArray();
        for (long m : com.greenerpastures.arcade.TopDeck.MULT) ladder.add(m);
        root.add("ladder", ladder);
        if (round != null) {
            JsonArray cards = new JsonArray();
            for (int i = 0; i < com.greenerpastures.arcade.TopDeck.DECK; i++) {
                JsonObject c = new JsonObject();
                c.addProperty("s", round.species.get(i));
                c.addProperty("e", round.ogEmotions.get(i));
                cards.add(c);
            }
            root.add("cards", cards);
            root.addProperty("stage", round.stage);
            root.addProperty("wager", round.wager);
            root.addProperty("over", round.over);
            root.addProperty("won", round.won);
            root.addProperty("payout", round.payout);
            root.addProperty("flipsLeft", round.flipsLeft);
            JsonArray flips = new JsonArray();
            for (int i = 0; i < com.greenerpastures.arcade.TopDeck.DECK; i++) {
                if (round.flipped[i] && round.strangerSpecies[i] != null) {
                    JsonObject f = new JsonObject();
                    f.addProperty("pos", i);
                    f.addProperty("s", round.strangerSpecies[i]);
                    f.addProperty("e", round.strangerEmotion[i]);
                    flips.add(f);
                }
            }
            root.add("flips", flips);
            if (round.revealTarget >= 0) root.addProperty("reveal", round.revealTarget);
            JsonObject mercy = new JsonObject();
            mercy.addProperty("available", round.mercyAvailable && !round.mercyUsed);
            mercy.addProperty("started", round.mercyIndex >= 0 && !round.mercyUsed);
            mercy.addProperty("used", round.mercyUsed);
            mercy.addProperty("won", round.mercyWon);
            if (round.mercyIndex >= 0) {
                mercy.addProperty("species", round.species.get(round.mercyIndex));
                mercy.addProperty("card", round.mercyIndex);
                JsonArray opts = new JsonArray();
                for (String e : round.mercyOptions) opts.add(e);
                mercy.add("options", opts);
            }
            root.add("mercy", mercy);
        }
        sendGated(player, "topdeck", new NotebookTopdeckS2C(GSON.toJson(root)));
    }

    // ── SLOTS (Game Corner cabinet #4 - "the classic", 2026-07-06). One action per pull:
    // debit, roll three uniform faces, pay the enumerable paytable. RTP 457/512. ──

    private static void slotsSpin(ServerPlayerEntity player, int bet) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        if (!com.greenerpastures.arcade.SlotMachine.betValid(bet)) {
            player.sendMessage(net.minecraft.text.Text.literal("\u00a76[Game Corner]\u00a7r this machine takes "
                    + com.greenerpastures.arcade.SlotMachine.MIN_BET + " to "
                    + com.greenerpastures.arcade.SlotMachine.MAX_BET + " Coins a pull."), false);
            return;
        }
        var store = com.greenerpastures.arcade.ArcadeStore.get(server);
        if (!store.trysSpend(player.getUuid(), arcadeToday(), bet)) {
            player.sendMessage(net.minecraft.text.Text.literal("\u00a76[Game Corner]\u00a7r not enough Coins - the machines await."), false);
            return;
        }
        int[] reels = com.greenerpastures.arcade.SlotMachine.spin(new java.util.Random());
        long paid = com.greenerpastures.arcade.SlotMachine.payout(reels, bet);
        if (paid > 0) {
            store.record(player.getUuid(), arcadeToday(), paid, store.of(player.getUuid(), arcadeToday()).level);
        }
        long[] prev = slotsLast.get(player.getUuid());
        long seq = prev == null ? 1 : prev[0] + 1;
        slotsLast.put(player.getUuid(), new long[]{seq, bet, paid, reels[0], reels[1], reels[2]});
        GpLog.i("arcade", "sl_spin", "player", player.getUuid().toString(),
                "bet", bet, "reels", reels[0] + "-" + reels[1] + "-" + reels[2], "paid", paid);
    }

    // ── VIBE CHECK (Game Corner cabinet #5 - the free deck, 2026-07-06). No wager: a tuned-small
    // faucet where the drama is the deck's known 8/4 composition depleting under a doubling pot. ──

    private static void vibeDraw(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var round = vibeRounds.get(player.getUuid());
        var outcome = com.greenerpastures.arcade.VibeCheck.draw(round,
                com.greenerpastures.arcade.TopDeckPool.EMOTIONS, new java.util.Random());
        if (outcome == com.greenerpastures.arcade.VibeCheck.Outcome.SOUR) {
            GpLog.i("arcade", "vibe_sour", "player", player.getUuid().toString(),
                    "drawnBefore", round.drawn.size() - 1);
        } else if (outcome == com.greenerpastures.arcade.VibeCheck.Outcome.HAPPY && round.over) {
            com.greenerpastures.arcade.ArcadeStore.get(server).record(player.getUuid(), arcadeToday(),
                    round.payout, com.greenerpastures.arcade.ArcadeStore.get(server).of(player.getUuid(), arcadeToday()).level);
            GpLog.i("arcade", "vibe_clear", "player", player.getUuid().toString(), "paid", round.payout);
        }
    }

    private static void vibeCash(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var round = vibeRounds.get(player.getUuid());
        if (com.greenerpastures.arcade.VibeCheck.cashout(round) == com.greenerpastures.arcade.VibeCheck.Outcome.CASHED) {
            com.greenerpastures.arcade.ArcadeStore.get(server).record(player.getUuid(), arcadeToday(),
                    round.payout, com.greenerpastures.arcade.ArcadeStore.get(server).of(player.getUuid(), arcadeToday()).level);
            GpLog.i("arcade", "vibe_cash", "player", player.getUuid().toString(),
                    "paid", round.payout, "draws", round.drawn.size());
        }
    }

    /** VIBE CHECK state: drawn cards only - the rest of the deck stays face-down on the server. */
    public static void pushVibe(ServerPlayerEntity player) {
        var round = vibeRounds.get(player.getUuid());
        JsonObject root = new JsonObject();
        root.addProperty("deckSize", com.greenerpastures.arcade.VibeCheck.DECK_SIZE);
        root.addProperty("sourTotal", com.greenerpastures.arcade.VibeCheck.SOUR);
        root.addProperty("active", round != null && !round.over);
        if (round != null) {
            JsonArray drawn = new JsonArray();
            for (var c : round.drawn) {
                JsonObject o = new JsonObject();
                o.addProperty("s", c.species());
                o.addProperty("e", c.emotion());
                o.addProperty("happy", c.happy());
                drawn.add(o);
            }
            root.add("drawn", drawn);
            root.addProperty("pot", round.pot);
            root.addProperty("remaining", round.remaining());
            root.addProperty("sourLeft", round.sourRemaining());
            root.addProperty("over", round.over);
            root.addProperty("won", round.won);
            root.addProperty("payout", round.payout);
        }
        sendGated(player, "vibe", new NotebookVibeS2C(GSON.toJson(root)));
    }

    // ── QUICK CLAW (Game Corner cabinet #6, 2026-07-06): the sprint-clicker. The server deals the
    // chase and judges the CLICK PACKET'S ARRIVAL on its own clock - reaction time can't be spoofed. ──

    private static void tagNew(ServerPlayerEntity player) {
        var prev = tagRounds.get(player.getUuid());
        if (prev != null) com.greenerpastures.arcade.SprintTag.forfeit((com.greenerpastures.arcade.SprintTag.Round) prev[0]);
        var round = com.greenerpastures.arcade.SprintTag.deal(
                com.greenerpastures.arcade.CrowdPool.POOL, new java.util.Random());
        if (round == null) return;
        tagRounds.put(player.getUuid(), new Object[]{round, System.nanoTime()});
        GpLog.d("arcade", "tag_new", "player", player.getUuid().toString(), "target", round.species);
    }

    private static void tagClick(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        var entry = tagRounds.get(player.getUuid());
        if (entry == null) return;
        var round = (com.greenerpastures.arcade.SprintTag.Round) entry[0];
        long elapsedMs = (System.nanoTime() - (Long) entry[1]) / 1_000_000L;
        long paid = com.greenerpastures.arcade.SprintTag.judge(round, elapsedMs);
        if (paid > 0) {
            var store = com.greenerpastures.arcade.ArcadeStore.get(server);
            store.record(player.getUuid(), arcadeToday(), paid, store.of(player.getUuid(), arcadeToday()).level);
        }
        if (paid >= 0) {
            GpLog.i("arcade", "tag_click", "player", player.getUuid().toString(),
                    "target", round.species, "elapsedMs", elapsedMs, "paid", Math.max(0, paid),
                    "escaped", round.escaped);
        }
    }

    /** QUICK CLAW state: the whole chase is choreography (timing is judged server-side), so the
     *  full path ships up front and the settled result follows the click. */
    public static void pushTag(ServerPlayerEntity player) {
        var entry = tagRounds.get(player.getUuid());
        JsonObject root = new JsonObject();
        root.addProperty("active", entry != null && !((com.greenerpastures.arcade.SprintTag.Round) entry[0]).over);
        if (entry != null) {
            var r = (com.greenerpastures.arcade.SprintTag.Round) entry[0];
            root.addProperty("species", r.species);
            root.addProperty("fromLeft", r.fromLeft);
            root.addProperty("yStart", r.yStartPct);
            root.addProperty("yEnd", r.yEndPct);
            root.addProperty("spawnDelayMs", r.spawnDelayMs);
            root.addProperty("crossMs", r.crossMs);
            root.addProperty("over", r.over);
            root.addProperty("paid", r.paid);
            root.addProperty("escaped", r.escaped);
        }
        sendGated(player, "tag", new NotebookTagS2C(GSON.toJson(root)));
    }

    /** SLOTS state: the symbol strip (static) + the seq-numbered last spin. */
    public static void pushSlots(ServerPlayerEntity player) {
        JsonObject root = new JsonObject();
        JsonArray symbols = new JsonArray();
        for (String sy : com.greenerpastures.arcade.SlotMachine.SYMBOLS) symbols.add(sy);
        root.add("symbols", symbols);
        root.addProperty("minBet", com.greenerpastures.arcade.SlotMachine.MIN_BET);
        root.addProperty("maxBet", com.greenerpastures.arcade.SlotMachine.MAX_BET);
        JsonArray pay = new JsonArray();
        pay.add(com.greenerpastures.arcade.SlotMachine.PAY_JACKPOT);
        pay.add(com.greenerpastures.arcade.SlotMachine.PAY_TRIPLE);
        pay.add(com.greenerpastures.arcade.SlotMachine.PAY_TWO_VOLT);
        pay.add((double) com.greenerpastures.arcade.SlotMachine.PAY_PAIR_NUM
                / com.greenerpastures.arcade.SlotMachine.PAY_PAIR_DEN);
        root.add("paytable", pay);
        long[] last = slotsLast.get(player.getUuid());
        if (last != null) {
            root.addProperty("seq", last[0]);
            root.addProperty("bet", last[1]);
            root.addProperty("paid", last[2]);
            JsonArray reels = new JsonArray();
            reels.add(last[3]); reels.add(last[4]); reels.add(last[5]);
            root.add("reels", reels);
        }
        sendGated(player, "slots", new NotebookSlotsS2C(GSON.toJson(root)));
    }

    /** TREELINE state: layout always; contents only where swept; the target\'s tree only once over. */
    public static void pushTreeline(ServerPlayerEntity player) {
        var round = treelineRounds.get(player.getUuid());
        JsonObject root = new JsonObject();
        root.addProperty("active", round != null);
        if (round != null) {
            root.addProperty("over", round.over);
            root.addProperty("won", round.won);
            root.addProperty("payout", round.payout);
            root.addProperty("clicksLeft", round.clicksLeft);
            root.addProperty("budget", com.greenerpastures.arcade.Treeline.CLICK_BUDGET);
            if (round.over) root.addProperty("targetTreeId", round.targetTreeId);
            JsonArray trees = new JsonArray();
            var target = round.tree(round.targetTreeId);
            for (var t : round.trees) {
                JsonObject o = new JsonObject();
                o.addProperty("id", t.id);
                o.addProperty("x", Math.round(t.x * 100.0) / 100.0);
                o.addProperty("y", Math.round(t.y * 100.0) / 100.0);
                o.addProperty("scale", Math.round(t.scale * 1000.0) / 1000.0);
                o.addProperty("searched", t.searched);
                if (t.searched && t.contains != null) {
                    o.addProperty("reveal", "target".equals(t.contains) ? "target" : "decoy");
                    if (!"target".equals(t.contains) && target != null) {
                        o.addProperty("arrow", com.greenerpastures.arcade.Treeline.arrowToward(target.x - t.x, target.y - t.y));
                    }
                }
                trees.add(o);
            }
            root.add("trees", trees);
            JsonArray critters = new JsonArray();
            for (var c : round.critters) {
                JsonObject o = new JsonObject();
                o.addProperty("isTarget", c.isTarget());
                o.addProperty("startX", Math.round(c.startX() * 100.0) / 100.0);
                o.addProperty("startY", Math.round(c.startY() * 100.0) / 100.0);
                o.addProperty("exitY", Math.round(c.exitY() * 100.0) / 100.0);
                critters.add(o);
            }
            root.add("critters", critters);
        }
        sendGated(player, "treeline", new NotebookTreelineS2C(GSON.toJson(root)));
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
            // Magnitude chips (Deuce, live QA 2026-07-06: drop chance / hatch / IV floor were invisible in
            // LOADOUT). Server-formatted, mirroring BreedingUpgradeItem's tooltip switch - one truth.
            JsonArray chips = new JsonArray();
            int v;
            if (a != null && (v = a.level(com.greenerpastures.economy.AugmentFunction.SHINY)) > 0)
                chips.add("✦ +" + v + "% shiny");
            int drop = a == null ? 0 : a.level(com.greenerpastures.economy.AugmentFunction.DROP_RATE);
            if (drop == 0 && kernel.getItem() instanceof BreedingUpgradeItem bu)
                drop = bu.tier().baseDropRateCentipercent();   // bare kernel still shows its born-with drop chance
            if (drop > 0) chips.add("⛏ +" + String.format("%.2f", drop / 100.0) + "% drops");
            if (a != null && (v = a.level(com.greenerpastures.economy.AugmentFunction.DROP_YIELD)) > 0)
                chips.add("⛏ +" + v + " drop yield");
            if (a != null && (v = a.level(com.greenerpastures.economy.AugmentFunction.IV_FLOOR)) > 0)
                chips.add("▲ IV floor " + v);
            if (a != null && (v = a.level(com.greenerpastures.economy.AugmentFunction.SPEED)) > 0)
                chips.add("⚡ speed " + (v == 1 ? "×1.5" : v == 2 ? "×2" : v == 3 ? "×3" : "lvl " + v));
            if (a != null && (v = a.level(com.greenerpastures.economy.AugmentFunction.HATCH)) > 0)
                chips.add("🐣 hatch ×" + com.greenerpastures.pasture.breeding.HatchHaste.factorLabel(v));
            if (a != null && (v = a.level(com.greenerpastures.economy.AugmentFunction.ENRICHMENT)) > 0)
                chips.add("◈ enrichment +" + v + "%");
            if (!chips.isEmpty()) k.add("chips", chips);
            // TETHER FX (Deuce, 2026-07-21: "show how the tether is impacting this pasture") - a
            // before → after line per mod the slotted tethers touch. Numbers come from a potential
            // resolution (unlimited balance) so the player always sees what the tether DOES; a second
            // resolution against the owner's live Data decides the STARVED flag (dim + warn, never lie).
            java.util.List<com.greenerpastures.economy.SoulTether> slotted = pd.slottedTethers();
            if (!slotted.isEmpty()) {
                Map<com.greenerpastures.economy.AugmentFunction, Integer> baseLv = pd.baseAugmentLevels();
                java.util.EnumSet<com.greenerpastures.economy.AugmentFunction> fns =
                        java.util.EnumSet.allOf(com.greenerpastures.economy.AugmentFunction.class);
                com.greenerpastures.economy.EffectiveAugments pot = com.greenerpastures.economy.TetherRuntime
                        .resolveFor(baseLv, slotted, Long.MAX_VALUE / 4, fns).effective();
                long ownerBal = pd.owner == null ? 0L
                        : com.greenerpastures.economy.DataStore.get(server).balanceOf(pd.owner);
                boolean powered = com.greenerpastures.economy.TetherRuntime
                        .resolveFor(baseLv, slotted, ownerBal, fns).amplified();
                JsonArray fx = new JsonArray();
                for (com.greenerpastures.economy.AugmentFunction f : com.greenerpastures.economy.AugmentFunction.values()) {
                    int b = baseLv.getOrDefault(f, 0);
                    double e = pot.magnitude(f);
                    if (b <= 0 || e <= b) continue;
                    String line = switch (f) {
                        case SHINY      -> "✦ shiny " + b + "% → " + trimNum(e) + "%";
                        case DROP_RATE  -> "⛏ drops +" + centiPct(b) + "% → +" + centiPct(e) + "%";
                        case ENRICHMENT -> "◈ enrichment +" + b + "% → +" + trimNum(e) + "%";
                        case SPEED      -> "⚡ speed lv " + b + " → lv " + pot.speedLevel();
                        case DROP_YIELD -> "⛏ yield +" + b + " → +" + pot.dropYieldBonus();
                        case HATCH      -> "🐣 hatch ×" + com.greenerpastures.pasture.breeding.HatchHaste.factorLabel(b)
                                + " → ×" + com.greenerpastures.pasture.breeding.HatchHaste.factorLabel(pot.hatchLevel());
                        default -> null;
                    };
                    if (line != null) fx.add(line);
                }
                // A tether with NO matching Kernel mod does nothing (base = free, amplification = rented) -
                // say it in the player's face instead of showing a slotted tether with silent zero effect.
                java.util.Set<com.greenerpastures.economy.AugmentFunction> baseless = new java.util.LinkedHashSet<>();
                for (com.greenerpastures.economy.SoulTether st : slotted) {
                    com.greenerpastures.economy.AugmentFunction f =
                            com.greenerpastures.economy.AugmentFunction.byId(st.function());
                    if (f != null && f.tetherable() && baseLv.getOrDefault(f, 0) <= 0) baseless.add(f);
                }
                for (com.greenerpastures.economy.AugmentFunction f : baseless) {
                    fx.add("⚠ " + f.label + " tether idle - install the matching Kernel augment first");
                }
                if (!fx.isEmpty()) {
                    k.add("tetherFx", fx);
                    if (!powered) k.addProperty("tetherStarved", true);
                }
            }
            // Full augment map + corruption - the client dresses its DISPLAY stack with these so hovering the
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
        // Soul Tether functional slots (Deuce, 2026-07-20): the Kernel's unlocked slot count + what sits in
        // each - the React TETHERS row renders these cells next to the KERNEL row.
        BreedingTier bt = pd.tier();
        int unlockedSlots = bt == null ? 0 : Math.min(bt.slots, pd.upgrades.size() - 1);
        root.addProperty("slots", unlockedSlots);
        JsonArray slotArr = new JsonArray();
        for (int i = 0; i < unlockedSlots; i++) {
            ItemStack ts = pd.upgrades.getStack(1 + i);
            com.greenerpastures.economy.Tether tt = ts.get(com.greenerpastures.economy.DarkEconomy.TETHER);
            JsonObject o = new JsonObject();
            o.addProperty("idx", i);
            o.addProperty("has", !ts.isEmpty());
            o.addProperty("fn", tt == null ? "" : tt.function());
            o.addProperty("tier", tt == null ? 0 : tt.tier());
            if (ts.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME))
                o.addProperty("name", ts.getName().getString());
            slotArr.add(o);
        }
        root.add("tethers", slotArr);
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
                        pd.lastHarvestTick = world.getTime();   // anchor offline-progress at claim - never backfill the unowned past
                        pd.lastBreedTick = world.getTime();
                        reg.markDirty();
                        player.sendMessage(Text.literal(r.outcome() == PastureClaim.Outcome.CLAIMED
                                ? "§a[Greener Pastures]§r Linked - this pasture's drops, eggs & outputs collect into your Notebook (you pay its tether cost)."
                                : "§a[Greener Pastures]§r Unlinked - its outputs no longer collect to you."), false);
                    } else {
                        player.sendMessage(Text.literal("§c[Greener Pastures]§r This pasture is owned by someone else."), false);
                    }
                }
                case NotebookPastureActionC2S.KERNEL -> {
                    int src = -1; try { src = Integer.parseInt(p.arg()); } catch (Exception ignored) { }
                    toggleKernel(player, reg.getOrCreate(world, pos), src); reg.markDirty();
                }
                case NotebookPastureActionC2S.TETHER -> {
                    toggleTether(player, reg.getOrCreate(world, pos), p.arg()); reg.markDirty();
                    pushLoom(player);   // the tether left / re-entered the inventory - refresh the bench
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

    /** Slot an INSCRIBED Soul Tether into functional slot {@code idx} (pd.upgrades slot {@code 1+idx}), or
     *  return the slotted one. {@code arg = "idx:invSlot"}; invSlot -1 = return. Blanks refuse - an
     *  uninscribed tether amplifies nothing and must visit the Loom first (that's the whole loop). */
    private static void toggleTether(ServerPlayerEntity player, PastureData pd, String arg) {
        if (arg == null || arg.isBlank()) return;
        int idx, src;
        try {
            String[] parts = arg.split(":", 2);
            idx = Integer.parseInt(parts[0]);
            src = parts.length > 1 ? Integer.parseInt(parts[1]) : -1;
        } catch (NumberFormatException e) { return; }
        BreedingTier tier = pd.tier();
        int unlocked = tier == null ? 0 : Math.min(tier.slots, pd.upgrades.size() - 1);
        if (idx < 0 || idx >= unlocked) return;                     // slot not unlocked by this Kernel
        int upSlot = 1 + idx;
        ItemStack cur = pd.upgrades.getStack(upSlot);
        if (src < 0) {                                              // return the slotted tether
            if (cur.isEmpty()) return;
            ItemStack out = cur.copy();
            pd.upgrades.setStack(upSlot, ItemStack.EMPTY);
            player.getInventory().insertStack(out);
            if (!out.isEmpty()) pd.upgrades.setStack(upSlot, out);  // inventory full → keep it slotted (never destroy)
            else GpLog.i("loom", "tether_return", "player", player.getUuid().toString(), "slot", Integer.toString(idx));
            return;
        }
        if (!cur.isEmpty()) return;                                 // occupied - return it first
        var main = player.getInventory().main;
        if (src >= main.size()) return;
        ItemStack s = main.get(src);
        if (!(s.getItem() instanceof com.greenerpastures.economy.SoulTetherItem)) return;
        com.greenerpastures.economy.Tether t = s.get(com.greenerpastures.economy.DarkEconomy.TETHER);
        if (t == null || t.isBlank()) return;                       // blank - the Loom comes first
        pd.upgrades.setStack(upSlot, s.split(1));
        player.getInventory().markDirty();
        GpLog.i("loom", "tether_slot", "player", player.getUuid().toString(),
                "slot", Integer.toString(idx), "fn", t.function(), "tier", Integer.toString(t.tier()));
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
     *  + a size cap, stores it on {@link PastureData}. Not echoed back - the editor is the authority while open. */
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
            if (json.length() > 65536) return;   // sanity cap - a pasture graph is never this big
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
        pushBiobank(player, false);
    }

    /** {@code force} skips the 5s flatten floor - user-initiated actions (withdraw) get instant feedback. */
    public static void pushBiobank(ServerPlayerEntity player, boolean force) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        BioBankData bank = BioBankStore.get(server).get(player.getUuid());
        // 5s floor FIRST (review M2): during ACTIVE breeding the rev bumps every second - re-flattening a
        // hoard-scale bank per second per viewer was the cost. The floor must run BEFORE the rev gate:
        // changed() records the rev it sees, so gating first EATS the change signal on a throttled call and
        // the UI freezes on stale state forever (BUG-017 - Deuce's ghost abra, live QA 2026-07-06).
        long nowMs = System.currentTimeMillis();
        if (!force) {
            Long lastFlat = lastBiobankFlatten.get(player.getUuid());
            if (lastFlat != null && nowMs - lastFlat < 5_000L) return;   // rev signal stays unconsumed - the 1s poll retries
        }
        // Rev gate before assembly: flattening a hoard-scale bank (thousands of eggs → cards + entries) every
        // second was the single biggest per-viewer cost (R3 F1/F2). Unchanged bank → nothing at all happens.
        // The compression rev is folded in so a COMMUNAL donation reaches every viewer's console, not just
        // the donator's (their own bank rev doesn't move when someone else feeds the server press).
        long compRev = com.greenerpastures.biobank.CompressionStore.get(server).rev();
        long sig = (bank == null ? -1L : bank.rev()) + compRev * 0x1_0000_0000L;
        if (!changed(player, "biobank_rev", sig)) return;
        lastBiobankFlatten.put(player.getUuid(), nowMs);
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
        // Compression ledgers ride the biobank packet - the combined rev gate above covers bank mutations
        // AND compression mutations (including someone ELSE feeding the communal press).
        com.greenerpastures.biobank.CompressionStore comp = com.greenerpastures.biobank.CompressionStore.get(server);
        List<NotebookBioBankS2C.Press> presses = pressList(comp.get(player.getUuid()));
        List<NotebookBioBankS2C.Press> serverPresses = pressList(comp.server());
        ServerPlayNetworking.send(player, new NotebookBioBankS2C(entries.size(), entries, presses, serverPresses));
    }

    private static List<NotebookBioBankS2C.Press> pressList(com.greenerpastures.biobank.CompressionLedger led) {
        if (led == null || led.isEmpty()) return List.of();
        List<NotebookBioBankS2C.Press> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : led.snapshot().entrySet()) {
            out.add(new NotebookBioBankS2C.Press(e.getKey(), e.getValue()));
        }
        return out;
    }

    /** Write Data onto blank media (§5c - the Notebook is the drive): consume 1 blank + debit the
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
            player.sendMessage(Text.literal("§c[Greener Pastures]§r Not enough Data - that disk holds "
                    + String.format("%,d", disk.value) + "."), false);
            return;
        }
        ItemStack blank = inv.main.get(blankSlot);
        if (blank.getCount() == 1) {
            if (!data.tryDebit(player.getUuid(), disk.value)) return;
            inv.main.set(blankSlot, new ItemStack(denomItem));           // media swaps in place - always room
        } else {
            int empty = -1;
            for (int i = 0; i < inv.main.size(); i++) if (inv.main.get(i).isEmpty()) { empty = i; break; }
            if (empty < 0) {
                player.sendMessage(Text.literal("§c[Greener Pastures]§r Inventory full - make room for the written disk."), false);
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
            // Pity is the whole APEX safety net - surface it (review: it was invisible). Single-pasture
            // rituals report the MAX pity across the player's pastures; spanning ones read the ledger.
            int pity = 0;
            if (r.pastureSpan() > 1) {
                pity = ledger.spanStateOf(player.getUuid(), r.id())[1];
            } else {
                for (var w : server.getWorlds()) {
                    for (var pdEntry : com.greenerpastures.pasture.breeding.PastureRegistry.get(server).inWorld(w).entrySet()) {
                        var pd = pdEntry.getValue();
                        if (!player.getUuid().equals(pd.owner)) continue;
                        int[] st = pd.ritualState.get(r.id());
                        if (st != null && st.length == 2) pity = Math.max(pity, st[1]);
                    }
                }
            }
            o.addProperty("pity", pity);
            o.addProperty("hardPity", r.hardPity());
            if (r.pastureSpan() > 1) o.addProperty("span", r.pastureSpan());
            if (!r.outputPool().isEmpty()) {
                o.addProperty("pool", true);
                JsonArray poolItems = new JsonArray();   // the card's spoils tile aggregates the WHOLE pool
                r.outputPool().forEach(poolItems::add);  // (BUG-020: 8 discs banked, tile read 0 - it only knew pool[0])
                o.add("poolItems", poolItems);
            }
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

    /** Take ritual loot into the inventory - same manual capacity+placement contract as {@link #pull}
     *  (never trust insertStack, refuse loudly, the pool is only debited by what actually landed). */
    private static void ritualPull(ServerPlayerEntity player, String itemId, int mode) {
        MinecraftServer server = player.getServer();
        if (server == null || itemId == null || itemId.isEmpty()) return;
        Item item = Registries.ITEM.get(Identifier.of(itemId));
        if (item == Items.AIR) {
            player.sendMessage(Text.literal("§c[Greener Pastures]§r That item no longer exists in this world (a mod or datapack changed) - it can't be withdrawn."), false);
            return;
        }
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
            player.sendMessage(net.minecraft.text.Text.literal("§c[Greener Pastures]§r Inventory full - nothing pulled."), false);
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
            player.sendMessage(Text.literal("§5⛧§r Already corrupted - the disk finds nothing left to take."), false);
            return;
        }
        // consume exactly one Illicit Data Disk from the MAIN inventory (offhand = parking spot, review u9)
        var inv = player.getInventory();
        boolean paid = false;
        for (var list : java.util.List.of(inv.main)) {
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
                // Never-worse gift (review: BLESSED could overwrite a level-II augment back to I): pick an
                // augment that can still IMPROVE - uninstalled installs at I, level-I upgrades to II (beyond
                // slot cap either way). All maxed (absurdly rare) -> the bless fizzles into "nothing".
                AugmentType gift = null;
                int giftLevel = 1;
                int start = CORRUPT_RNG.nextInt(pool.length);
                for (int i = 0; i < pool.length; i++) {
                    AugmentType cand = pool[(start + i) % pool.length];
                    int cur = cand.installedLevelOn(out);
                    if (cur < AugmentType.CORRUPT_MAX_LEVEL && cand.maxLevel() > 1) { gift = cand; giftLevel = cur + 1; break; }
                    if (cur < 1) { gift = cand; giftLevel = 1; break; }   // binaries: install if absent
                }
                if (gift == null) {
                    detail = "the blessing found nothing left to improve";
                } else {
                    out = gift.apply(out, gift.storedValueFor(out, giftLevel));
                    detail = gift.effectSummary() + (giftLevel >= 3 ? " §7(TIER III - the glitch's gift)§r"
                            : giftLevel == 2 ? " §7(upgraded to II beyond capacity)§r"
                            : " §7(installed beyond capacity)§r");
                }
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
                    BreedingTier wildTier = ((BreedingUpgradeItem) out.getItem()).tier();
                    if (wildTier == BreedingTier.GREENER) {
                        // +1 pair is IMPOSSIBLE on a Greener (8-pair board cap, 16-mon roster - review M3).
                        // Deuce's replacement: WILD pushes one installed augment a tier past the mortal
                        // ceiling instead (up to the corruption-only III).
                        AugmentType pushed = null;
                        int pushedTo = 0;
                        for (AugmentType cand : AugmentType.values()) {
                            if (cand.maxLevel() <= 1) continue;
                            int cur = cand.installedLevelOn(out);
                            if (cur >= 1 && cur < AugmentType.CORRUPT_MAX_LEVEL
                                    && (pushed == null || cur > pushed.installedLevelOn(out))) {
                                pushed = cand; pushedTo = cur + 1;
                            }
                        }
                        if (pushed != null) {
                            out = pushed.apply(out, pushed.storedValueFor(out, pushedTo));
                            detail = pushed.effectSummary() + " §7(WILD - pushed to tier " + pushedTo + ")§r";
                        } else {
                            Augments aw = out.get(GpComponents.AUGMENTS);
                            int curDr = aw == null ? 0 : aw.level(com.greenerpastures.economy.AugmentFunction.DROP_RATE);
                            int doubledDr = Math.max(1, curDr) * 2;
                            out.set(GpComponents.AUGMENTS, (aw == null ? Augments.NONE : aw)
                                    .withLevel(com.greenerpastures.economy.AugmentFunction.DROP_RATE, doubledDr));
                            detail = "its drop-rate mod DOUBLED (" + String.format("%.2f", doubledDr / 100.0) + "%)";
                        }
                    } else {
                        Integer bonus = out.get(GpComponents.CORRUPT_PAIRS);
                        out.set(GpComponents.CORRUPT_PAIRS, (bonus == null ? 0 : bonus) + 1);
                        detail = "+1 breeding pair - beyond its tier";
                    }
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
                    detail = "the Kernel DEGRADED to " + lower.name() + " - its augments went with it";
                }
            }
        }
        out.set(GpComponents.CORRUPTED, Boolean.TRUE);
        ref.writer().accept(out);
        String line = com.greenerpastures.pasture.breeding.compiler.KernelCorruption.reveal(roll, detail);
        player.sendMessage(Text.literal(line), false);
        com.greenerpastures.notify.Inbox.push(player.getUuid(), "⛧",
                "Corruption: " + roll.outcome() + (detail.isEmpty() ? "" : " - " + detail.replaceAll("§.", "")));
        GpLog.i("corrupt", "kernel", "player", player.getUuid().toString(),
                "outcome", roll.outcome().name(), "variant", roll.variant(), "detail", detail.replaceAll("§.", ""));
    }

    private static final java.util.Random CORRUPT_RNG = new java.util.Random();

    private static int countGpu(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        int n = 0;
        for (ItemStack s : inv.main) if (s.isOf(GpItems.GPU)) n += s.getCount();   // main only - the offhand is a parking spot (review u9)
        return n;
    }

    private static boolean anyDaemonOn(ServerPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack s : inv.main) if (s.getItem() instanceof DaemonItem && DaemonItem.isOn(s)) return true;
        for (ItemStack s : inv.offHand) if (s.getItem() instanceof DaemonItem && DaemonItem.isOn(s)) return true;
        return false;
    }
}
