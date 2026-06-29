package com.greenerpastures.buff;

import com.greenerpastures.core.GpLog;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * The beyond-vanilla enchant boost — delivered <b>without ever writing an ItemStack</b> (so there is no dupe /
 * desync / NBT surface; see {@code ENCHANT_BOOST.md}). The block-drop mixin {@link
 * com.greenerpastures.mixin.BlockDropBoostMixin} calls {@link #begin}/{@link #end} to scope a thread-local
 * window around a fed-Daemon holder's loot resolution; the read mixin {@link
 * com.greenerpastures.mixin.EnchantmentLevelMixin} calls {@link #boost} to add the resolved tier to the
 * enchant level the loot table reads. Pure read interception, gated to that window — never fires for tooltips,
 * anvils, or other players.
 *
 * <p>Server loot generation is single-threaded, so the {@link ThreadLocal} is safe and self-clearing (each
 * {@link #begin} clears first, so a window that failed to {@link #end} is reset at the next block break).
 * v1 delivers <b>Fortune</b> only (the marquee block-drop enchant); more enchants join {@link #KEYS} (and
 * {@code DaemonBuffs.SUPPORTED}) as their delivery is verified.
 */
public final class DaemonEnchantBoost {
    private DaemonEnchantBoost() {}

    /** Which buffs ride an enchant on the block-drop path, and the enchant they boost. */
    private static final Map<BuffId, RegistryKey<Enchantment>> KEYS = Map.of(
            BuffId.FORTUNE, Enchantments.FORTUNE);

    /** The active boosts for the current loot resolution (enchant → +tier); null when no window is open. */
    private static final ThreadLocal<Map<RegistryKey<Enchantment>, Integer>> ACTIVE = new ThreadLocal<>();

    /** Open a boost window for a block broken by {@code breaker}, if they're holding a fed Daemon. */
    public static void begin(ServerPlayerEntity breaker) {
        ACTIVE.remove();                                  // clear any leaked window first
        ResolvedBuffs paid = DaemonBuffs.paidBuffs(breaker);
        if (paid == null || paid.isEmpty()) return;
        Map<RegistryKey<Enchantment>, Integer> active = null;
        for (Map.Entry<BuffId, RegistryKey<Enchantment>> k : KEYS.entrySet()) {
            int tier = paid.tier(k.getKey());
            if (tier > 0) {
                if (active == null) active = new HashMap<>(2);
                active.put(k.getValue(), tier);
            }
        }
        if (active != null) ACTIVE.set(active);
    }

    /** Close the current boost window (always called at loot-resolution RETURN). */
    public static void end() {
        ACTIVE.remove();
    }

    /**
     * Add the active boost for {@code enchantment} to its read {@code original} level. Only rides an enchant the
     * gear <i>already has</i> ({@code original > 0}) — "beyond the vanilla max", not "grant from nothing" — and
     * only inside an open window. Clamped to the component's 255 ceiling.
     */
    public static int boost(RegistryEntry<Enchantment> enchantment, int original) {
        if (original <= 0 || enchantment == null) return original;
        Map<RegistryKey<Enchantment>, Integer> active = ACTIVE.get();
        if (active == null) return original;
        for (Map.Entry<RegistryKey<Enchantment>, Integer> b : active.entrySet()) {
            if (enchantment.matchesKey(b.getKey())) {
                int boosted = Math.min(255, original + b.getValue());
                GpLog.d("buff", "enchant_boost", "ench", b.getKey().getValue().toString(),
                        "from", original, "to", boosted);
                return boosted;
            }
        }
        return original;
    }
}
