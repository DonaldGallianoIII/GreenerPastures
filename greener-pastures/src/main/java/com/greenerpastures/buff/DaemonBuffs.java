package com.greenerpastures.buff;

import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.DaemonItem;
import com.greenerpastures.economy.DarkEconomy;
import com.greenerpastures.economy.DataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The MC adapter that grants the Daemon's "root" buffs to players holding a held + fed Daemon. The data model is
 * the pure {@link BuffResolver}: the held Daemon's level (Mk I/II/III) is the tier, drain is tier-scaled and
 * summed. This is the <b>rented-while-fed</b> contract — each second we debit the resolved Data/sec first; a
 * player who can't pay simply isn't buffed that second (their effects lapse on their own).
 *
 * <p>The loop runs every tick: once per second it <i>settles</i> (resolve → bill → apply status effects → cache
 * the paid buff set), and every tick it runs the <i>per-tick hooks</i> (the item magnet) from that cached set —
 * so a hook only ever runs for a buff the player paid for this second, but moves smoothly between billings.
 *
 * <p><b>Delivered so far:</b> the {@link BuffCategory#EFFECT} buffs (Haste, Saturation) and the item-magnet
 * {@link BuffCategory#HOOK}. The resolver is told to bill <i>only</i> for {@link #SUPPORTED}, so a player is
 * never charged for a buff the mod hasn't wired up. The beyond-vanilla enchant boost and the remaining hooks
 * (auto-smelt, vein-mine, …) land next and join {@link #SUPPORTED}.
 */
public final class DaemonBuffs {
    private DaemonBuffs() {}

    /** Drain + (re)apply cadence: once per second. */
    private static final int INTERVAL = 20;
    /** Status effects are refreshed every second; give them slack so they never flicker between applications. */
    private static final int EFFECT_DURATION = INTERVAL * 3;
    /** The buffs this adapter can currently deliver — drain is billed only for these. Widens as we wire more.
     *  FORTUNE/AUTO_SMELT/XP_BOOST/VEIN_MINE + the value-effect enchants (LURE/LUCK_OF_THE_SEA/FROST_WALKER/LOOTING)
     *  are delivered by mixins/events via {@link #paidBuffs}; the EFFECT + MAGNET here; the attribute enchants
     *  (Respiration/Swift Sneak/Feather Falling) by {@link DaemonAttributeBuffs} (folded in via its
     *  {@code DELIVERED} set so the bill can never drift from what's actually applied). LOOTING is the one
     *  combat-adjacent buff — Deuce opted it in (default-on); every other combat enchant stays absent. Unbreaking
     *  is the only catalog ENCHANT left undelivered (its seam carries no entity to scope it to a holder). */
    private static final Set<BuffId> SUPPORTED;
    static {
        EnumSet<BuffId> s = EnumSet.of(
                BuffId.HASTE, BuffId.SATURATION, BuffId.MAGNET,
                BuffId.FORTUNE, BuffId.AUTO_SMELT, BuffId.XP_BOOST, BuffId.VEIN_MINE, BuffId.POTION_DURATION,
                BuffId.LURE, BuffId.LUCK_OF_THE_SEA, BuffId.FROST_WALKER, BuffId.LOOTING);
        s.addAll(DaemonAttributeBuffs.DELIVERED);
        SUPPORTED = s;
    }

    /** Fractional Data carried between seconds, so sub-1/sec drains accrue honestly instead of rounding to free. */
    private static final Map<UUID, Double> drainCarry = new HashMap<>();
    /** The buff set a player actually paid for this second — drives the per-tick hooks between billings. */
    private static final Map<UUID, ResolvedBuffs> lastPaid = new HashMap<>();

    private static int tickAccum;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DaemonBuffs::onServerTick);
        DaemonVeinMine.init();   // event-driven HOOK (block-break), reads the same paid-buff state
        GpLog.i("buff", "adapter_init", "interval", INTERVAL, "supported", SUPPORTED.toString());
    }

    private static void onServerTick(MinecraftServer server) {
        boolean settleTick = (++tickAccum >= INTERVAL);
        if (settleTick) tickAccum = 0;

        BuffConfig cfg = BuffSystem.config();
        DataStore data = settleTick ? DataStore.get(server) : null;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (settleTick) settle(player, cfg, data);
            ResolvedBuffs paid = lastPaid.get(player.getUuid());
            if (paid != null) runHooks(player, paid);
        }
    }

    /** Once per second: resolve the player's buffs, bill the Data, apply status effects, cache what they paid for. */
    private static void settle(ServerPlayerEntity player, BuffConfig cfg, DataStore data) {
        UUID id = player.getUuid();
        if (player.isSpectator() || !cfg.enabled()) { clear(player); return; }

        int level = heldDaemonLevel(player);
        if (level <= 0) { clear(player); return; }              // no Daemon in hand

        ResolvedBuffs buffs = BuffResolver.resolve(cfg, level, SUPPORTED);
        if (buffs.isEmpty()) { clear(player); return; }

        // Rented-while-fed: a truly broke account gets nothing, even for a sub-1/sec drain.
        if (data.balanceOf(id) <= 0) { clear(player); return; }

        double owed = buffs.dataPerSec() + drainCarry.getOrDefault(id, 0.0);
        long pay = (long) Math.floor(owed);
        if (pay > 0) {
            if (!data.tryDebit(id, pay)) { clear(player); return; }   // can't afford the whole owed → starve
            owed -= pay;
        }
        drainCarry.put(id, owed);                            // keep the fractional remainder for next second
        lastPaid.put(id, buffs);

        applyEffects(player, buffs);
        DaemonAttributeBuffs.reconcile(player, buffs);       // attribute enchants: grant/scale/strip to match the bill
        GpLog.d("buff", "tick", "player", id.toString(), "lvl", level,
                "buffs", buffs.tiers().size(), "paid", pay);
    }

    /** Drop a player's buff state — clears the per-tick caches AND strips any attribute modifiers we added. */
    private static void clear(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        lastPaid.remove(id);
        drainCarry.remove(id);
        DaemonAttributeBuffs.clear(player);
    }

    /**
     * The buffs a player paid for this second, or {@code null}. Read by the enchant-boost mixins
     * ({@link DaemonEnchantBoost}) on the server thread — same thread that mutates {@link #lastPaid}, so no
     * locking is needed.
     */
    public static ResolvedBuffs paidBuffs(ServerPlayerEntity player) {
        return player == null ? null : lastPaid.get(player.getUuid());
    }

    /** Highest Mk level among the Daemon(s) the player is holding (main or off hand); 0 if none. */
    private static int heldDaemonLevel(PlayerEntity player) {
        int best = 0;
        ItemStack main = player.getMainHandStack();
        if (main.isOf(DarkEconomy.DAEMON)) best = Math.max(best, DaemonItem.levelOf(main));
        ItemStack off = player.getOffHandStack();
        if (off.isOf(DarkEconomy.DAEMON)) best = Math.max(best, DaemonItem.levelOf(off));
        return best;
    }

    // ── EFFECT buffs (refreshed each second) ──────────────────────────────────
    private static void applyEffects(ServerPlayerEntity player, ResolvedBuffs buffs) {
        for (Map.Entry<BuffId, Integer> e : buffs.tiers().entrySet()) {
            if (e.getKey().category != BuffCategory.EFFECT) continue;
            RegistryEntry<StatusEffect> effect = effectFor(e.getKey());
            if (effect == null) continue;
            int amplifier = e.getValue() - 1;               // tier 1 ⇒ level I (amplifier 0)
            // ambient + no particles + show icon: visible it's on, but no clutter (it's a passive worker buff)
            player.addStatusEffect(new StatusEffectInstance(effect, EFFECT_DURATION, amplifier, true, false, true));
        }
    }

    private static RegistryEntry<StatusEffect> effectFor(BuffId id) {
        return switch (id) {
            case HASTE -> StatusEffects.HASTE;
            case SATURATION -> StatusEffects.SATURATION;
            default -> null;
        };
    }

    // ── per-tick HOOK buffs (run every tick from the paid set) ────────────────
    private static void runHooks(ServerPlayerEntity player, ResolvedBuffs paid) {
        if (player.isSpectator()) return;
        int magnet = paid.tier(BuffId.MAGNET);
        if (magnet > 0) pullNearbyItems(player, magnet);
    }

    /** Item magnet: gently draw pick-up-able dropped items toward the holder. Radius scales with tier (4/6/8). */
    private static void pullNearbyItems(ServerPlayerEntity player, int tier) {
        double radius = 4.0 + (tier - 1) * 2.0;
        Vec3d target = player.getPos().add(0.0, 0.4, 0.0);
        Box box = player.getBoundingBox().expand(radius);
        List<ItemEntity> items = player.getWorld().getEntitiesByClass(
                ItemEntity.class, box, item -> item.isAlive() && !item.cannotPickup());
        for (ItemEntity item : items) {
            Vec3d to = target.subtract(item.getPos());
            double dist = to.length();
            if (dist < 0.6 || dist > radius) continue;       // close enough → let vanilla grab it; or out of range
            Vec3d vel = to.normalize().multiply(Math.min(0.85, 0.12 + dist * 0.05));
            item.setVelocity(vel);
            item.velocityModified = true;                    // sync the nudged motion to clients
        }
    }
}
