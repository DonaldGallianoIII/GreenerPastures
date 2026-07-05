package com.greenerpastures.ritual;

import com.greenerpastures.pasture.breeding.compiler.AugmentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Tripwire (Deuce, 2026-07-05): em dashes read as AI-written. No shipped default text may carry one.
 *  Guards the strings reachable headless - ritual names/hints, augment summaries, type-drop item ids. */
class NoEmDashTest {

    @Test
    void noEmDashesInShippedDefaults() {
        for (Ritual r : RitualConfig.defaults().rituals().rituals()) {
            assertFalse(r.name().contains("—"), r.id() + " name has an em dash");
            assertFalse(r.hint().contains("—"), r.id() + " hint has an em dash");
        }
        for (TypeDrop d : RitualConfig.defaults().typeDrops().drops()) {
            assertFalse(d.item().contains("—"));
        }
        for (AugmentType at : AugmentType.values()) {
            assertFalse(at.effectSummary().contains("—"), at.name() + " summary has an em dash");
        }
    }
}
