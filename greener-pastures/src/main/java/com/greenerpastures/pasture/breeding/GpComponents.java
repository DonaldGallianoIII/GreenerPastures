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

    /** Force class-load so the static registration above runs. Call once from module init. */
    public static void init() {}
}
