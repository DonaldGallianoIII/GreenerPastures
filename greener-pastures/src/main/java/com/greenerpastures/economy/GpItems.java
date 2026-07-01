package com.greenerpastures.economy;

import com.greenerpastures.GreenerPastures;
import com.greenerpastures.pasture.breeding.NotebookItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Art-first item registrations for the console economy — the <b>GPU</b> augment reagent, the <b>Data-disk</b>
 * denominations (the physical form of the {@code Data} balance), and the <b>Notebook</b> (the future unified
 * console). These are currently plain items carrying real 32×32 art; behaviour — reading/writing Data disks,
 * opening the console, applying augments — lands with the Notebook build (see {@code NOTEBOOK_CONSOLE_SPEC.md}).
 */
public final class GpItems {
    private GpItems() {}

    public static Item GPU;
    public static Item NOTEBOOK;
    public static Item DISK_BLANK, DISK_BYTE, DISK_KILOBYTE, DISK_MEGABYTE, DISK_GIGABYTE, DISK_TERABYTE, DISK_ROCKET;

    private static Item item(String id, Item.Settings settings) {
        return Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, id), new Item(settings));
    }

    public static void init() {
        GPU           = item("gpu", new Item.Settings());
        NOTEBOOK      = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "notebook"),
                new NotebookItem(new Item.Settings().maxCount(1)));
        DISK_BLANK    = item("data_disk_blank", new Item.Settings());
        DISK_BYTE     = item("data_disk_byte", new Item.Settings());
        DISK_KILOBYTE = item("data_disk_kilobyte", new Item.Settings());
        DISK_MEGABYTE = item("data_disk_megabyte", new Item.Settings());
        DISK_GIGABYTE = item("data_disk_gigabyte", new Item.Settings());
        DISK_TERABYTE = item("data_disk_terabyte", new Item.Settings());
        DISK_ROCKET   = item("data_disk_rocket", new Item.Settings());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            e.add(NOTEBOOK);
            e.add(GPU);
            e.add(DISK_BLANK);
            e.add(DISK_BYTE);
            e.add(DISK_KILOBYTE);
            e.add(DISK_MEGABYTE);
            e.add(DISK_GIGABYTE);
            e.add(DISK_TERABYTE);
            e.add(DISK_ROCKET);
        });
    }
}
