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
    public static Item SNACK_REPEL;
    public static Item NOTEBOOK;
    public static Item FIELD_GUIDE;
    public static Item DISK_BLANK, DISK_BYTE, DISK_KILOBYTE, DISK_MEGABYTE, DISK_GIGABYTE, DISK_TERABYTE, DISK_ROCKET;

    private static Item item(String id, Item.Settings settings) {
        return Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, id), new Item(settings));
    }

    private static Item disk(String id, long value) {
        return Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, id),
                new DataDiskItem(value, new Item.Settings()));
    }

    public static void init() {
        GPU           = item("gpu", new Item.Settings());
        SNACK_REPEL   = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "snack_repel"),
                new SnackRepelItem(new Item.Settings().maxCount(16)));
        NOTEBOOK      = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "notebook"),
                new NotebookItem(new Item.Settings().maxCount(1)));
        FIELD_GUIDE   = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "field_guide"),
                new com.greenerpastures.core.FieldGuideItem(new Item.Settings().maxCount(1)));
        // Data disks (§5c — Data's physical form): a binary denomination ladder, baked (no config, anti-p2w).
        // The Notebook's Dashboard WRITES a blank into a denomination; right-click READS it back to balance.
        DISK_BLANK    = disk("data_disk_blank", 0L);
        DISK_BYTE     = disk("data_disk_byte", 8L);
        DISK_KILOBYTE = disk("data_disk_kilobyte", 1_024L);
        DISK_MEGABYTE = disk("data_disk_megabyte", 16_384L);
        DISK_GIGABYTE = disk("data_disk_gigabyte", 262_144L);
        DISK_TERABYTE = disk("data_disk_terabyte", 4_194_304L);
        DISK_ROCKET   = Registry.register(Registries.ITEM, Identifier.of(GreenerPastures.MOD_ID, "data_disk_rocket"),
                new IllicitDiskItem(new Item.Settings().maxCount(16)));   // NOT a denomination — the corruption orb (Team Rocket contraband)

        // One dedicated creative tab for the whole mod (release polish): auto-collects every item registered
        // under our namespace — entries resolve LAZILY (on tab open), so registration order doesn't matter.
        Registry.register(Registries.ITEM_GROUP, Identifier.of(GreenerPastures.MOD_ID, "all"),
                net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()
                        .icon(() -> new net.minecraft.item.ItemStack(NOTEBOOK))
                        .displayName(net.minecraft.text.Text.translatable("itemGroup.greenerpastures"))
                        .entries((ctx, e) -> Registries.ITEM.getIds().stream()
                                .filter(id -> id.getNamespace().equals(GreenerPastures.MOD_ID))
                                .sorted()
                                .forEach(id -> e.add(Registries.ITEM.get(id))))
                        .build());
        // Keep the Notebook + Guide discoverable in vanilla Tools too (players look there first).
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            e.add(NOTEBOOK);
            e.add(FIELD_GUIDE);
        });
    }
}
