package com.greenerpastures.buff;

import com.greenerpastures.core.GpLog;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MC adapter for the {@link AttributeBuff} enchant boosts — adds/updates/removes a <b>transient</b>
 * {@code EntityAttributeModifier} on the player so the rented buff is delivered the clean, non-destructive way:
 * it never writes an ItemStack (no dupe/desync surface) and syncs over the attribute channel the client already
 * trusts. The modifiers are temporary (never saved to NBT) — a lapsed buff just vanishes, and a relog/respawn
 * resets the player's attributes so the settle loop simply re-applies next second.
 *
 * <p>Called from {@link DaemonBuffs} once per second: {@link #reconcile} brings the player's modifiers in line
 * with what they paid for (granting/scaling present buffs, stripping absent ones), and {@link #clear} strips
 * everything when the player drops the Daemon or can't pay. Both run on the server thread only — the
 * {@link #attributed} guard keeps {@code clear} a cheap no-op for the (vast) majority of players we never touched.
 */
public final class DaemonAttributeBuffs {
    private DaemonAttributeBuffs() {}

    private static final String NS = "greenerpastures";

    /** Each attribute buff → the real vanilla player attribute it modifies. */
    private static final Map<AttributeBuff, RegistryEntry<EntityAttribute>> ATTRS = new EnumMap<>(AttributeBuff.class);
    static {
        ATTRS.put(AttributeBuff.RESPIRATION,     EntityAttributes.GENERIC_OXYGEN_BONUS);
        ATTRS.put(AttributeBuff.SWIFT_SNEAK,     EntityAttributes.PLAYER_SNEAKING_SPEED);
        ATTRS.put(AttributeBuff.FEATHER_FALLING, EntityAttributes.GENERIC_FALL_DAMAGE_MULTIPLIER);
        ATTRS.put(AttributeBuff.MINING_DAMAGE,   EntityAttributes.PLAYER_BLOCK_BREAK_SPEED);
    }

    /** The buffs this adapter delivers — folded into {@code DaemonBuffs.SUPPORTED} so they're billed only here. */
    public static final Set<BuffId> DELIVERED;
    static {
        EnumSet<BuffId> s = EnumSet.noneOf(BuffId.class);
        for (AttributeBuff a : AttributeBuff.values()) s.add(a.buff);
        DELIVERED = s;
    }

    /** Players we've granted at least one modifier to, so {@link #clear} is a no-op for everyone else. Server thread. */
    private static final Set<UUID> attributed = new HashSet<>();

    /** Bring the player's attribute modifiers in line with the buffs they paid for this second. */
    public static void reconcile(ServerPlayerEntity player, ResolvedBuffs paid) {
        boolean any = false;
        for (AttributeBuff ab : AttributeBuff.values()) {
            EntityAttributeInstance inst = player.getAttributeInstance(ATTRS.get(ab));
            if (inst == null) continue;                 // attribute somehow absent on this entity — skip defensively
            Identifier id = Identifier.of(NS, ab.modifierPath());
            int tier = paid.tier(ab.buff);
            if (tier <= 0) { inst.removeModifier(id); continue; }

            double value = ab.value(tier);
            EntityAttributeModifier existing = inst.getModifier(id);
            if (existing == null || existing.value() != value) {     // only touch when it actually changed (no packet spam)
                if (existing != null) inst.removeModifier(id);
                inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op(ab.operation)));
                GpLog.d("buff", "attr_apply", "buff", ab.buff.id, "tier", tier, "value", value);
            }
            any = true;
        }
        UUID uuid = player.getUuid();
        if (any) attributed.add(uuid); else attributed.remove(uuid);
    }

    /** Strip every modifier we may have added (cheap no-op if we never touched this player). */
    public static void clear(ServerPlayerEntity player) {
        if (!attributed.remove(player.getUuid())) return;
        for (AttributeBuff ab : AttributeBuff.values()) {
            EntityAttributeInstance inst = player.getAttributeInstance(ATTRS.get(ab));
            if (inst != null) inst.removeModifier(Identifier.of(NS, ab.modifierPath()));
        }
    }

    private static Operation op(AttributeBuff.Op op) {
        return switch (op) {
            case ADD_VALUE -> Operation.ADD_VALUE;
            case ADD_MULTIPLIED_BASE -> Operation.ADD_MULTIPLIED_BASE;
            case ADD_MULTIPLIED_TOTAL -> Operation.ADD_MULTIPLIED_TOTAL;
        };
    }
}
