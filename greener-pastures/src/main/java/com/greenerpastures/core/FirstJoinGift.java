package com.greenerpastures.core;

import com.greenerpastures.economy.GpItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Hands every player ONE {@link GpItems#NOTEBOOK} the first time they join a world with the mod installed
 * (Deuce, 2026-07-05: the Field Guide item died — it was just a skin over the Notebook's Guide tab, which
 * the gifted Notebook carries anyway; gifting THE item also softens the onboarding wall). Persisted per
 * world, so relogs and tossing don't re-gift; full inventory → drops at their feet (never lost).
 */
public final class FirstJoinGift extends PersistentState {
    private static final String ID = "greenerpastures_greeted";

    private final Set<UUID> greeted = new HashSet<>();

    public static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            FirstJoinGift g = get(server);
            UUID id = handler.player.getUuid();
            if (g.greeted.contains(id)) return;
            g.greeted.add(id);
            g.markDirty();
            handler.player.getInventory().offerOrDrop(new ItemStack(GpItems.NOTEBOOK));
            GpLog.i("guide", "first_join_gift", "player", id.toString());
        }));
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (UUID u : greeted) list.add(NbtString.of(u.toString()));
        nbt.put("greeted", list);
        return nbt;
    }

    private static FirstJoinGift fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        FirstJoinGift g = new FirstJoinGift();
        for (var el : nbt.getList("greeted", 8 /* NbtString */)) {
            try { g.greeted.add(UUID.fromString(el.asString())); } catch (IllegalArgumentException ignored) { }
        }
        return g;
    }

    private static final Type<FirstJoinGift> TYPE = new Type<>(FirstJoinGift::new, FirstJoinGift::fromNbt, null);

    private static FirstJoinGift get(MinecraftServer server) {
        return server.getWorld(World.OVERWORLD).getPersistentStateManager().getOrCreate(TYPE, ID);
    }
}
