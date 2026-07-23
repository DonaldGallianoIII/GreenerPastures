package com.greenerpastures.drops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * GP-authored drop entries layered ON TOP of a species' native Cobblemon table at harvest time - the
 * Tier-2 "speciesDrops overlay" from the drops audit ({@code cobblemon-drops-ref/UNDERREPRESENTED_DROPS.md}).
 * Minecraft-free + unit-tested. Keyed by the species' display name (Cobblemon {@code getName()}, e.g.
 * "Gimmighoul"), matched case-insensitively.
 *
 * <p>These fill farmability holes the base game leaves - chiefly <b>gold</b>, which no vanilla-mob-free
 * survival world can otherwise farm (the literal gold Pokémon dropped only {@code relic_coin} in Cobblemon).
 * An overlay rolls with the SAME cadence proc as the base table - one overlay roll per drop EVENT - so its
 * items obey the Drop Rate lever exactly like the mon's native ones. Quantity ranges that include 0 bake in
 * a "sometimes nothing" chance, mirroring how Persian's own {@code gold_nugget} entry already works.
 */
public final class SpeciesDropOverlay {
    private final Map<String, DropTable> bySpecies;   // key = lowercased display name

    public SpeciesDropOverlay(Map<String, DropTable> bySpecies) {
        Map<String, DropTable> m = new LinkedHashMap<>();
        if (bySpecies != null) {
            bySpecies.forEach((k, v) -> {
                if (k != null && v != null && !v.isEmpty()) m.put(k.toLowerCase(Locale.ROOT), v);
            });
        }
        this.bySpecies = m;
    }

    /**
     * The default GP overlay (drops audit, 2026-07-22): real gold for the gold Pokémon + Persian's ingot
     * chance. Entries use {@code quantityRange}-style ranges (chance 1.0, quantity may include 0) to match
     * the native Meowth/Persian gold-nugget style - a range starting at 0 IS the "chance to drop nothing".
     */
    public static SpeciesDropOverlay defaults() {
        final String GOLD = "minecraft:gold_ingot";
        Map<String, DropTable> m = new LinkedHashMap<>();
        // Gholdengo & Gimmighoul - the literal gold Pokémon - dropped only relic_coin in vanilla Cobblemon.
        m.put("Gholdengo",  new DropTable(List.of(new DropEntry(GOLD, 1.0, 0, 1))));
        m.put("Gimmighoul", new DropTable(List.of(new DropEntry(GOLD, 1.0, 1, 2))));
        // Persian already trickles gold_nugget - give it a matching small ingot chance in the same style.
        m.put("Persian",    new DropTable(List.of(new DropEntry(GOLD, 1.0, 0, 1))));
        return new SpeciesDropOverlay(m);
    }

    /** Extra drops for one drop EVENT of {@code species} → {@code item id → count} (empty if none). Never throws. */
    public Map<String, Integer> roll(String species, Random rng) {
        return roll(species, rng, 0);
    }

    /** Extra drops for one drop EVENT, with the Drop Yield lever ({@code yieldBonus}) widening each entry's
     *  quantity ceiling exactly like native drops - so a yield-boosted Gimmighoul gets a shot at extra gold,
     *  same as its overlay obeys Drop Rate via the proc cadence. → {@code item id → count}. Never throws. */
    public Map<String, Integer> roll(String species, Random rng, int yieldBonus) {
        if (species == null) return Collections.emptyMap();
        DropTable t = bySpecies.get(species.toLowerCase(Locale.ROOT));
        if (t == null || t.isEmpty()) return Collections.emptyMap();
        return t.widenedBy(yieldBonus).roll(rng);
    }

    /** Does this species carry any overlay entries? */
    public boolean has(String species) {
        return species != null && bySpecies.containsKey(species.toLowerCase(Locale.ROOT));
    }

    public boolean isEmpty() { return bySpecies.isEmpty(); }
}
