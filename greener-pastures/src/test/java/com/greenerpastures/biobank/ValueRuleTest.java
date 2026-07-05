package com.greenerpastures.biobank;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Headless tests for the "valuable egg" safety rule - pure logic. */
class ValueRuleTest {

    private static EggSummary egg(boolean shiny, int ivTotal, int perfect) {
        return new EggSummary("charmander", shiny, ivTotal, perfect);
    }

    @Test
    void defaultFlagsShiny() {
        assertTrue(ValueRule.DEFAULT.isValuable(egg(true, 60, 0)));
    }

    @Test
    void defaultFlagsAnyPerfectIv() {
        assertTrue(ValueRule.DEFAULT.isValuable(egg(false, 100, 1)), "one 31-IV stat = worth keeping");
    }

    @Test
    void defaultDoesNotFlagAPlainEgg() {
        assertFalse(ValueRule.DEFAULT.isValuable(egg(false, 120, 0)), "non-shiny, no perfect IV = renderable");
    }

    @Test
    void ivTotalRuleIsOffByDefault() {
        assertFalse(ValueRule.DEFAULT.isValuable(egg(false, 186, 0)), "even a max-IV-total egg isn't flagged unless the rule is enabled");
    }

    @Test
    void customIvTotalThresholdFlagsHighEggs() {
        ValueRule keepHighIv = new ValueRule(true, 1, 150);
        assertTrue(keepHighIv.isValuable(egg(false, 160, 0)));
        assertFalse(keepHighIv.isValuable(egg(false, 149, 0)));
    }

    @Test
    void customPerfectThresholdRespectsTheCount() {
        ValueRule needTwoPerfect = new ValueRule(true, 2, 187);
        assertFalse(needTwoPerfect.isValuable(egg(false, 100, 1)), "1 perfect < required 2");
        assertTrue(needTwoPerfect.isValuable(egg(false, 100, 2)));
    }
}
