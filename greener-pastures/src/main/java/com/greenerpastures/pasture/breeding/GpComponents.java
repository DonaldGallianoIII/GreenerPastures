package com.greenerpastures.pasture.breeding;

import com.greenerpastures.GreenerPastures;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Greener Pastures' own item data-components. v1 = {@code greenerpastures:augments}, the compiled
 * program a Kernel (slotted upgrade) carries (see {@link Augments}). Registered with both a save
 * codec and a packet codec so the augment survives serialization AND syncs to the client GUI.
 */
public final class GpComponents {
    private GpComponents() {}

    public static final ComponentType<Augments> AUGMENTS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(GreenerPastures.MOD_ID, "augments"),
            ComponentType.<Augments>builder()
                    .codec(Augments.CODEC)
                    .packetCodec(Augments.PACKET_CODEC)
                    .build());

    /** The {@code greenerpastures:ev_spread} per-stat EV allocation a Kernel carries (BUG-002 — replaces the flat
     *  "+N on every stat" EV augment). Save + packet codecs so it persists AND can sync to a future GUI. */
    public static final ComponentType<EvSpread> EV_SPREAD = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(GreenerPastures.MOD_ID, "ev_spread"),
            ComponentType.<EvSpread>builder()
                    .codec(EvSpread.CODEC)
                    .packetCodec(EvSpread.PACKET_CODEC)
                    .build());

    /** {@code greenerpastures:corrupted} — the Illicit Data Disk's permanent mark (Vaal-orb style): a
     *  corrupted Kernel still WORKS but the Augmenter refuses it forever. */
    public static final ComponentType<Boolean> CORRUPTED = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(GreenerPastures.MOD_ID, "corrupted"),
            ComponentType.<Boolean>builder()
                    .codec(com.mojang.serialization.Codec.BOOL)
                    .packetCodec(net.minecraft.network.codec.PacketCodecs.BOOL)
                    .build());

    /** {@code greenerpastures:corrupt_pairs} — the WILD corruption's +N breeding pairs (a 9-pair Greener). */
    public static final ComponentType<Integer> CORRUPT_PAIRS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(GreenerPastures.MOD_ID, "corrupt_pairs"),
            ComponentType.<Integer>builder()
                    .codec(com.mojang.serialization.Codec.INT)
                    .packetCodec(net.minecraft.network.codec.PacketCodecs.VAR_INT)
                    .build());

    /** Force class-load so the static registration above runs. Call once from module init. */
    public static void init() {}
}
