package com.greenerpastures.economy;

import com.greenerpastures.biobank.EggSummary;
import com.greenerpastures.biobank.ValueRule;

import java.util.ArrayList;
import java.util.List;

/**
 * A Renderer's decision for one batch of pasture eggs: split into KEEP vs RENDER (cull → Data), value the
 * render batch, and - above all - <b>enforce the sacred shiny rule</b>: a shiny egg is NEVER rendered,
 * whatever the {@link ValueRule} says. Minecraft-free + unit-tested.
 *
 * <p>The Renderer block is a thin adapter: it reads each tray egg into an {@link EggSummary}, calls
 * {@link #plan}, then removes exactly the {@code render} set and credits {@code data} to the owner. The
 * shiny guard lives here (not just in the adapter) so a mis-set {@link ValueRule} can never eat a shiny -
 * the invariant is proven by a test, independent of any config.
 */
public final class RenderRun {
    private RenderRun() {}

    /** The outcome: {@code keep} stays in the pasture, {@code render} is culled for {@code data} Data. */
    public record Plan(List<EggSummary> keep, List<EggSummary> render, long data) {
        public int keptCount() { return keep.size(); }
        public int renderCount() { return render.size(); }
    }

    /**
     * Decide keep-vs-render for a batch and value the render set as Data.
     *
     * @param eggs                 the batch (e.g. a pasture's egg tray), MC-free summaries
     * @param keep                 the value rule - eggs it deems valuable are kept
     * @param baseValuePerEgg      Data per rendered egg (the economy's balance constant)
     * @param enrichmentMultiplier ≥1 Enrichment-tether multiplier (sub-1 / NaN floors to 1×)
     */
    public static Plan plan(List<EggSummary> eggs, ValueRule keep, long baseValuePerEgg, double enrichmentMultiplier) {
        List<EggSummary> keptList = new ArrayList<>();
        List<EggSummary> renderList = new ArrayList<>();
        for (EggSummary e : eggs) {
            (isRendered(e, keep) ? renderList : keptList).add(e);
        }
        long data = RenderValuation.dataFor(renderList.size(), baseValuePerEgg, enrichmentMultiplier);
        return new Plan(List.copyOf(keptList), List.copyOf(renderList), data);
    }

    /**
     * The per-egg decision the Renderer uses: an egg is RENDERED (culled → Data) iff the value rule
     * doesn't keep it AND it isn't shiny. <b>SACRED</b> - a shiny short-circuits to "never rendered",
     * whatever the rule says. The block adapter calls this per tray slot so the sacred-shiny invariant
     * has exactly one tested home, shared with {@link #plan}.
     */
    public static boolean isRendered(EggSummary e, ValueRule keep) {
        return !e.shiny() && !keep.isValuable(e);
    }
}
