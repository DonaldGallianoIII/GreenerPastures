package com.greenerpastures.notebook;

import com.greenerpastures.analytics.Analytics;
import com.greenerpastures.analytics.Event;
import com.greenerpastures.biobank.BioBankStore;
import com.greenerpastures.core.GpLog;
import com.greenerpastures.economy.DataStore;
import com.greenerpastures.economy.RenderValuation;
import com.greenerpastures.egg.oracle.cull.EggCard;
import com.greenerpastures.egg.oracle.cull.EggReader;
import com.greenerpastures.pasture.breeding.PastureData;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Eggs-as-data ingest (EGG_PIPELINE_SPEC). A Kernel'd <b>+ linked</b> pasture routes each bred egg here, and the
 * pasture's <b>Daemon graph</b> ({@link GraphEval}) decides its fate: <b>KEEP</b> → stored losslessly in the
 * owner's BioBank, or <b>VOID</b> → rendered to Data (credited + logged) when the player has wired a filter that
 * culls it. With no graph (or nothing wired to the egg output) it keeps <i>everything</i> — the old behaviour, so
 * existing pastures are unchanged. Shiny / unreadable eggs are always kept (SACRED, in {@link GraphEval}).
 *
 * <p>The <b>void log is the trust feature</b> (VISUAL_SCRIPTING_UI_IDEA.md): every void is an observable
 * {@code egg_voided} analytics event <i>and</i> a {@code GpLog} line — so "no eggs for hours" reads as
 * "produced N, voided N-1 by IV≥31, kept 1", not "bugged". Returns false only when the BioBank is full, so the
 * breeder trays the physical egg and nothing is lost. Never throws.
 */
public final class EggIngest {
    private EggIngest() {}

    /** Data credited per egg the graph renders (voids) — a modest per-egg trickle; the dark-economy income. */
    private static final long VOID_DATA_PER_EGG = 10L;

    private static final java.util.Random BREADCRUMB = new java.util.Random();

    public static boolean ingest(ServerWorld world, UUID owner, ItemStack egg, PastureData pd, BlockPos pos, UUID monId) {
        try (var span = com.greenerpastures.core.GpProf.begin("egg.ingest")) {
            MinecraftServer server = world.getServer();
            String species = EggReader.species(egg);
            EggCard card = EggReader.card(egg);
            GraphEval.Result r = GraphEval.route(pd.graphJson, monId, card);
            if (r.route() == GraphEval.Route.VOID) {
                long value = RenderValuation.dataFor(1, VOID_DATA_PER_EGG, 1.0);
                DataStore.get(server).creditEarned(owner, value);   // render income counts toward the MissingNo. odometer
                String filter = r.rejectedBy() == null ? "pipeline" : r.rejectedBy();
                Analytics.record(world, Event.of("egg_voided")
                        .put("species", species).put("shiny", card != null && card.shiny())
                        .put("iv_total", card != null ? card.ivTotal() : 0)
                        .put("nature", card != null ? card.nature() : "")
                        .put("filter", filter).put("data", value)
                        .put("x", pos.getX()).put("y", pos.getY()).put("z", pos.getZ()));
                GpLog.i("egg_ingest", "void", "owner", owner.toString(), "species", species,
                        "filter", filter, "data", Long.toString(value));
                EggLog.record(owner, species, true, filter);   // player-facing void feed
                EggLog.addData(owner, value);                  // dashboard "Data earned"
                // The breadcrumb (1/2000 renders): the void stream coughs up ILLICIT data — the disk lands in
                // the ritual spoils pool with a whisper, pointing players at the hidden Black Market ritual.
                if (BREADCRUMB.nextInt(2000) == 0) {
                    com.greenerpastures.ritual.RitualLedger.get(server).addLoot(owner,
                            "greenerpastures:data_disk_rocket", 1);
                    com.greenerpastures.notify.Inbox.push(owner, "\u26e7",
                            "Something ILLICIT surfaced in the void stream \u2014 check your Ritual spoils\u2026");
                    GpLog.i("egg_ingest", "illicit_breadcrumb", "owner", owner.toString());
                }
                return true;   // rendered to Data → egg consumed; the breeder must NOT tray-fallback
            }
            boolean added = BioBankStore.get(server).deposit(owner, species, egg);
            if (added) EggLog.record(owner, species, false, "");
            GpLog.d("egg_ingest", added ? "bank" : "full", "owner", owner.toString(), "species", species);
            return added;      // false = bank full → the breeder keeps the physical egg (tray fallback)
        } catch (Throwable t) {
            GpLog.w("egg_ingest", "err", "err", String.valueOf(t));
            return false;
        }
    }
}
