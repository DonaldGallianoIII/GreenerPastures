package com.greenerpastures.notebook;

import com.greenerpastures.biobank.BioBankStore;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.egg.oracle.cull.EggReader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Eggs-as-data ingest (EGG_PIPELINE_SPEC). A Kernel'd <b>+ linked</b> pasture routes each bred egg here, and it
 * is stored <b>losslessly in the owner's BioBank — every egg, no auto-cull</b> (Deuce, 2026-07-01: the BioBank
 * is a keep-everything store; rendering an egg to Data is an <i>explicit</i> choice made in the visual-scripting
 * layer's void/Data node, never automatic here). Returns false only when the bank is full, so the breeder falls
 * back to the tray and no egg is lost. Never throws.
 */
public final class EggIngest {
    private EggIngest() {}

    public static boolean ingest(MinecraftServer server, UUID owner, ItemStack egg) {
        try {
            String species = EggReader.species(egg);
            boolean added = BioBankStore.get(server).deposit(owner, species, egg);
            GpLog.d("egg_ingest", added ? "bank" : "full", "owner", owner.toString(), "species", species);
            return added;   // false = bank full → the breeder keeps the physical egg (tray fallback)
        } catch (Throwable t) {
            GpLog.w("egg_ingest", "err", "err", String.valueOf(t));
            return false;
        }
    }
}
