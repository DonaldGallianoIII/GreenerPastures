package com.greenerpastures.biobank;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the keep-vs-render split. */
class RenderSelectionTest {

    private static EggSummary shiny(String s)   { return new EggSummary(s, true, 100, 0); }
    private static EggSummary perfect(String s) { return new EggSummary(s, false, 150, 1); }
    private static EggSummary plain(String s)   { return new EggSummary(s, false, 90, 0); }

    @Test
    void keepsValuablesAndRendersTheRest() {
        RenderSelection.Result r = RenderSelection.partition(
                List.of(shiny("a"), plain("b"), perfect("c"), plain("d")), ValueRule.DEFAULT);
        assertEquals(2, r.keptCount(), "shiny + perfect are kept");
        assertEquals(2, r.renderCount(), "the two plain eggs are the cull");
        assertTrue(r.render().stream().noneMatch(EggSummary::shiny), "no shiny ends up in the render batch");
    }

    @Test
    void everythingPlainGoesToRender() {
        RenderSelection.Result r = RenderSelection.partition(
                List.of(plain("a"), plain("b")), ValueRule.DEFAULT);
        assertEquals(0, r.keptCount());
        assertEquals(2, r.renderCount());
    }

    @Test
    void emptyInIsEmptyOut() {
        RenderSelection.Result r = RenderSelection.partition(List.of(), ValueRule.DEFAULT);
        assertTrue(r.keep().isEmpty());
        assertTrue(r.render().isEmpty());
    }
}
