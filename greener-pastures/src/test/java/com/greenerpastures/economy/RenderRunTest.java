package com.greenerpastures.economy;

import com.greenerpastures.biobank.EggSummary;
import com.greenerpastures.biobank.ValueRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the Renderer's keep/cull/value decision — esp. the SACRED shiny guard. */
class RenderRunTest {

    private static EggSummary plain(String sp)   { return new EggSummary(sp, false, 60, 0); }
    private static EggSummary shiny(String sp)   { return new EggSummary(sp, true, 60, 0); }
    private static EggSummary perfect(String sp) { return new EggSummary(sp, false, 186, 6); }

    @Test
    void rendersOnlyTheNonKeepersAndValuesThem() {
        List<EggSummary> eggs = List.of(plain("a"), plain("b"), perfect("c"), shiny("d"));
        RenderRun.Plan p = RenderRun.plan(eggs, ValueRule.DEFAULT, 10, 1.0);
        assertEquals(2, p.renderCount(), "the two plain eggs render");
        assertEquals(2, p.keptCount(), "the perfect + shiny are kept");
        assertEquals(20L, p.data(), "2 rendered × 10 base");
    }

    @Test
    void sacredShinyIsNeverRenderedEvenWhenTheRuleSaysItIsnTValuable() {
        // a deliberately broken rule: shiny NOT valuable, no IV rule -> would render everything.
        ValueRule cullsEverything = new ValueRule(false, 0, 187);
        List<EggSummary> eggs = List.of(plain("a"), shiny("b"), plain("c"));
        RenderRun.Plan p = RenderRun.plan(eggs, cullsEverything, 10, 1.0);
        assertEquals(2, p.renderCount(), "only the two plain eggs may render");
        assertTrue(p.keep().stream().anyMatch(EggSummary::shiny), "the shiny was force-kept");
        assertFalse(p.render().stream().anyMatch(EggSummary::shiny), "NO shiny is ever in the render set");
        assertEquals(20L, p.data(), "shiny excluded from the Data count");
    }

    @Test
    void enrichmentMultiplierScalesData() {
        List<EggSummary> eggs = List.of(plain("a"), plain("b"), plain("c"), plain("d"));
        assertEquals(80L, RenderRun.plan(eggs, ValueRule.DEFAULT, 10, 2.0).data(), "4×10×2");
    }

    @Test
    void allKeepersRenderNothing() {
        List<EggSummary> eggs = List.of(shiny("a"), perfect("b"));
        RenderRun.Plan p = RenderRun.plan(eggs, ValueRule.DEFAULT, 10, 1.0);
        assertEquals(0, p.renderCount());
        assertEquals(0L, p.data());
        assertEquals(2, p.keptCount());
    }

    @Test
    void emptyBatchIsZero() {
        RenderRun.Plan p = RenderRun.plan(List.of(), ValueRule.DEFAULT, 10, 1.0);
        assertEquals(0, p.keptCount());
        assertEquals(0, p.renderCount());
        assertEquals(0L, p.data());
    }

    @Test
    void isRenderedIsTheSharedPerEggPredicate() {
        assertTrue(RenderRun.isRendered(plain("a"), ValueRule.DEFAULT), "a plain egg renders");
        assertFalse(RenderRun.isRendered(shiny("b"), ValueRule.DEFAULT), "a shiny never renders");
        assertFalse(RenderRun.isRendered(perfect("c"), ValueRule.DEFAULT), "a kept (perfect-IV) egg doesn't render");
        assertFalse(RenderRun.isRendered(shiny("d"), new ValueRule(false, 0, 187)),
                "a shiny survives even a render-everything rule");
    }
}
