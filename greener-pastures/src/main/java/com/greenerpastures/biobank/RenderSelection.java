package com.greenerpastures.biobank;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a BioBank's stored eggs into <b>keep</b> vs <b>render</b> — the filter on the
 * BioBank → Renderer step ("you only ever render the cull, never the eggs you wanted"). The keep rule
 * is a {@link ValueRule}; everything it doesn't deem worth keeping becomes the render batch (the cull →
 * Data). Minecraft-free. The {@link RenderLedger} then previews that render batch with its own
 * independent safety scan (defense-in-depth).
 */
public final class RenderSelection {
    private RenderSelection() {}

    public record Result(List<EggSummary> keep, List<EggSummary> render) {
        public int keptCount() { return keep.size(); }
        public int renderCount() { return render.size(); }
    }

    /** Keep eggs the rule deems valuable; everything else is the render batch. Input order preserved. */
    public static Result partition(List<EggSummary> eggs, ValueRule keep) {
        List<EggSummary> k = new ArrayList<>(), r = new ArrayList<>();
        for (EggSummary e : eggs) {
            (keep.isValuable(e) ? k : r).add(e);
        }
        return new Result(List.copyOf(k), List.copyOf(r));
    }
}
