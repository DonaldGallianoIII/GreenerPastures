package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Server → client: the <b>Kernel Augmenter</b> tab (INTERACTIVE_SPEC §3.5) — the first held Kernel's tier +
 * slot capacity/usage + the augment catalog ({@link Aug}: type · effect label · slot cost · applied). Targets
 * the first {@code BreedingUpgradeItem} in the player's inventory. GPU/Data costs are <b>deferred</b> (§7.5);
 * the only live gate is <b>slot capacity + no-dupe</b>.
 */
public record NotebookAugmenterS2C(boolean hasKernel, String tier, int slotsUsed, int slotCap, List<Aug> catalog)
        implements CustomPayload {

    public record Aug(String type, String label, int slotCost, boolean applied) {
        public static final PacketCodec<RegistryByteBuf, Aug> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, Aug::type,
                PacketCodecs.STRING, Aug::label,
                PacketCodecs.VAR_INT, Aug::slotCost,
                PacketCodecs.BOOL, Aug::applied,
                Aug::new);
    }

    public static final Id<NotebookAugmenterS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_augmenter"));

    public static final PacketCodec<RegistryByteBuf, NotebookAugmenterS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, NotebookAugmenterS2C::hasKernel,
            PacketCodecs.STRING, NotebookAugmenterS2C::tier,
            PacketCodecs.VAR_INT, NotebookAugmenterS2C::slotsUsed,
            PacketCodecs.VAR_INT, NotebookAugmenterS2C::slotCap,
            Aug.CODEC.collect(PacketCodecs.toList()), NotebookAugmenterS2C::catalog,
            NotebookAugmenterS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
