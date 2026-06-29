package com.greenerpastures.buff;

import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.DaemonItem;
import com.greenerpastures.economy.DarkEconomy;
import com.greenerpastures.economy.DataStore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The MC adapter that grants the Daemon's "root" buffs each second to the players holding a held + fed Daemon.
 * The data model is the pure {@link BuffResolver}: the held Daemon's level (Mk I/II/III) is the tier, drain is
 * tier-scaled and summed. This is the <b>rented-while-fed</b> contract — we debit the resolved Data/sec first;
 * if the player can't pay, the buffs simply aren't applied this second and lapse on their own.
 *
 * <p><b>Interim scope:</b> only {@link BuffCategory#EFFECT} buffs (Haste, Saturation) are applied so far, so the
 * resolver is asked to bill <i>only</i> for that category ({@link #SUPPORTED}) — a player is never charged for a
 * buff the mod hasn't wired up. The {@code ENCHANT} (beyond-vanilla enchant boost) and {@code HOOK} (auto-smelt,
 * vein-mine, magnet, …) adapters land next and widen {@link #SUPPORTED}.
 */
public final class DaemonBuffs {
    private DaemonBuffs() {}

    /** Drain + (re)apply cadence: once per second. */
    private static final int INTERVAL = 20;
    /** Status effects are refreshed every second; give them slack so they never flicker between applications. */
    private static final int EFFECT_DURATION = INTERVAL * 3;
    /** Buff categories this adapter can currently deliver — drain is billed only for these. Widens as we wire more. */
    private static final Set<BuffCategory> SUPPORTED = EnumSet.of(BuffCategory.EFFECT);

    /** Fractional Data carried between seconds, so sub-1/sec drains accrue honestly instead of rounding to free. */
    private static final Map<UUID, Double> drainCarry = new HashMap<>();

    private static int tickAccum;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DaemonBuffs::onServerTick);
        GpLog.i("buff", "adapter_init", "interval", INTERVAL, "supported", SUPPORTED.toString());
    }

    private static void onServerTick(MinecraftServer server) {
        if (++tickAccum < INTERVAL) return;
        tickAccum = 0;

        BuffConfig cfg = BuffSystem.config();
        if (!cfg.enabled()) return;

        DataStore data = DataStore.get(server);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyToPlayer(player, cfg, data);
        }
    }

    private static void applyToPlayer(ServerPlayerEntity player, BuffConfig cfg, DataStore data) {
        UUID id = player.getUuid();
        int level = heldDaemonLevel(player);
        if (level <= 0) {                       // no Daemon in hand → nothing to sustain
            drainCarry.remove(id);
            return;
        }

        ResolvedBuffs buffs = BuffResolver.resolve(cfg, level, SUPPORTED);
        if (buffs.isEmpty()) {
            drainCarry.remove(id);
            return;
        }

        // Rented-while-fed: a truly broke account gets nothing, even for a sub-1/sec drain.
        if (data.balanceOf(id) <= 0) {
            drainCarry.remove(id);
            return;
        }

        double owed = buffs.dataPerSec() + drainCarry.getOrDefault(id, 0.0);
        long pay = (long) Math.floor(owed);
        if (pay > 0) {
            if (!data.tryDebit(id, pay)) {      // can't afford the whole owed → starve (no debt banking)
                drainCarry.put(id, 0.0);
                return;
            }
            owed -= pay;
        }
        drainCarry.put(id, owed);               // keep the fractional remainder for next second

        applyEffects(player, buffs);
        GpLog.d("buff", "tick", "player", id.toString(), "lvl", level,
                "buffs", buffs.tiers().size(), "paid", pay);
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

    private static void applyEffects(ServerPlayerEntity player, ResolvedBuffs buffs) {
        for (Map.Entry<BuffId, Integer> e : buffs.tiers().entrySet()) {
            if (e.getKey().category != BuffCategory.EFFECT) continue;
            RegistryEntry<StatusEffect> effect = effectFor(e.getKey());
            if (effect == null) continue;
            int amplifier = e.getValue() - 1;   // tier 1 ⇒ level I (amplifier 0)
            // ambient + no particles + show icon: visible it's on, but no clutter (it's a passive worker buff)
            player.addStatusEffect(new StatusEffectInstance(effect, EFFECT_DURATION, amplifier, true, false, true));
        }
    }

    private static RegistryEntry<StatusEffect> effectFor(BuffId id) {
        return switch (id) {
            case HASTE -> StatusEffects.HASTE;
            case SATURATION -> StatusEffects.SATURATION;
            default -> null;                    // other EFFECT ids (if added) map here
        };
    }
}
