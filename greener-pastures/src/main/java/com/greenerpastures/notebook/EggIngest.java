package com.greenerpastures.notebook;

import com.greenerpastures.biobank.BioBankStore;
import com.greenerpastures.biobank.EggSummary;
import com.greenerpastures.biobank.ValueRule;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.DataStore;
import com.greenerpastures.economy.RenderRun;
import com.greenerpastures.economy.RenderValuation;
import com.greenerpastures.egg.oracle.cull.EggInfo;
import com.greenerpastures.egg.oracle.cull.EggReader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Eggs-as-data ingest (EGG_PIPELINE_SPEC). A Kernel'd <b>+ linked</b> pasture routes each bred egg here instead
 * of the tray: <b>keeper</b> → the owner's BioBank as data, <b>non-keeper</b> → Data (the Renderer's cull folded
 * in at birth), <b>shiny / unreadable</b> → always kept (the SACRED guard, shared with the Renderer via
 * {@link RenderRun}). Never throws; on any trouble it returns false so the caller keeps the physical egg.
 */
public final class EggIngest {
    private EggIngest() {}

    /** Mirrors {@code RendererBlockEntity.BASE_DATA_PER_EGG} — the balance constant for a culled egg. */
    private static final long BASE_DATA_PER_EGG = 2L;

    /**
     * Route one bred egg to the owner. Returns true when handled (kept → BioBank, or culled → Data); returns
     * false <i>only</i> when it's a keeper but the BioBank is full — the caller then falls back to the tray so
     * no egg is lost.
     */
    public static boolean ingest(MinecraftServer server, UUID owner, ItemStack egg) {
        try {
            EggInfo info = EggReader.read(egg);
            String species = EggReader.species(egg);
            boolean cull;
            if (info == null || info.shiny() || !info.ivsKnown()) {
                cull = false;                                              // SACRED: never cull a shiny or an unreadable egg
            } else {
                EggSummary summary = new EggSummary(species, false, info.ivTotal(), info.perfectCount());
                cull = RenderRun.isRendered(summary, ValueRule.DEFAULT);   // non-keeper → render to Data
            }
            if (cull) {
                long data = RenderValuation.dataFor(1, BASE_DATA_PER_EGG, 1.0);   // TODO: enrichment-tether amplification
                DataStore.get(server).credit(owner, data);
                GpLog.d("egg_ingest", "cull", "owner", owner.toString(), "species", species, "data", Long.toString(data));
                return true;
            }
            boolean added = BioBankStore.get(server).deposit(owner, species, egg);
            GpLog.d("egg_ingest", "keep", "owner", owner.toString(), "species", species, "added", Boolean.toString(added));
            return added;                                                  // false = bank full → tray fallback
        } catch (Throwable t) {
            GpLog.w("egg_ingest", "err", "err", String.valueOf(t));
            return false;                                                  // on any error, keep the physical egg
        }
    }
}
