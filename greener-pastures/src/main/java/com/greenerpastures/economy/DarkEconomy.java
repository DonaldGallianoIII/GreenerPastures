package com.greenerpastures.economy;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.buff.DaemonLoadout;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * The dark economy's Minecraft layer (min-slice): the <b>Renderer</b> block (culls non-keeper eggs →
 * Data) and the <b>Daemon</b> item (the player's handle to their Data balance). Data itself lives
 * per-player in {@link DataStore}. The keep/cull/value logic is the unit-tested {@link RenderRun} /
 * {@code economy} + {@code biobank} cores; this is the thin registration + wiring.
 */
public final class DarkEconomy {
    private DarkEconomy() {}

    public static final Identifier DAEMON_ID = Identifier.of(GreenerPastures.MOD_ID, "daemon");

    public static Item DAEMON;
    /** The {@code greenerpastures:daemon_level} int a Daemon carries — its Mk tier (1–3), the buff strength ceiling.
     *  <b>Superseded by {@link #DAEMON_LOADOUT} (BUG-004)</b>; still registered so old saved Daemons decode cleanly. */
    public static ComponentType<Integer> DAEMON_LEVEL;
    /** The {@code greenerpastures:daemon_loadout} a Daemon carries — its compiled {@code buff → level} map (BUG-004). */
    public static ComponentType<DaemonLoadout> DAEMON_LOADOUT;
    /** The {@code greenerpastures:daemon_on} flag — is the Daemon toggled on (granting its loadout + showing glint). */
    public static ComponentType<Boolean> DAEMON_ON;
    /** The {@code greenerpastures:tether} data component a Soul Tether item carries ([function, tier]). */
    public static ComponentType<Tether> TETHER;
    public static Item SOUL_TETHER;

    public static void init() {
        DAEMON_LEVEL = Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(GreenerPastures.MOD_ID, "daemon_level"),
                ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());
        DAEMON_LOADOUT = Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(GreenerPastures.MOD_ID, "daemon_loadout"),
                ComponentType.<DaemonLoadout>builder()
                        .codec(DaemonLoadout.CODEC).packetCodec(DaemonLoadout.PACKET_CODEC).build());
        DAEMON_ON = Registry.register(Registries.DATA_COMPONENT_TYPE,
                Identifier.of(GreenerPastures.MOD_ID, "daemon_on"),
                ComponentType.<Boolean>builder().codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL).build());
        DAEMON = Registry.register(Registries.ITEM, DAEMON_ID, new DaemonItem(new Item.Settings().maxCount(1)));

        // Soul Tether: the [function, tier] data component + the (blank-until-inscribed) item.
        TETHER = Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of(GreenerPastures.MOD_ID, "tether"),
                ComponentType.<Tether>builder().codec(Tether.CODEC).packetCodec(Tether.PACKET_CODEC).build());
        SOUL_TETHER = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "soul_tether"),
                new SoulTetherItem(new Item.Settings().maxCount(16)));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> { e.add(DAEMON); e.add(SOUL_TETHER); });

        GreenerPastures.LOG.info("[dark-economy] loaded — Daemon + Data store + Soul Tether item.");
    }
}
