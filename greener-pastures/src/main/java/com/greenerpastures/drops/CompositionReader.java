package com.greenerpastures.drops;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.greenerpastures.ritual.Composition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads a pasture's tethered mons into the MC-free {@link Composition} the ritual engine reasons about, plus
 * a per-mon type view the Harvester needs for tier-1 type-drops. The ONE place the custom-drop system touches
 * Cobblemon's type API ({@code Pokemon.getTypes() → ElementalType.getShowdownId()}), isolated + fail-safe so a
 * Cobblemon edge can never crash the harvest tick (mirrors {@link DropsBridge}). Type / species keys are
 * lowercased to match the config.
 */
public final class CompositionReader {
    private CompositionReader() {}

    /** Per-mon type sets (for type-drops) + the aggregate {@link Composition} (for rituals), one read. */
    public record PastureMons(List<Set<String>> perMonTypes, Composition aggregate) {}

    public static PastureMons read(PokemonPastureBlockEntity pasture) {
        List<Set<String>> perMon = new ArrayList<>();
        Map<String, Integer> typeCounts = new HashMap<>();   // mons-per-type (a dual-type mon counts for both)
        Set<String> species = new HashSet<>();
        Map<String, Integer> speciesCounts = new HashMap<>();   // exact headcount per species (Rituals v2 gates)
        try {
            for (PokemonPastureBlockEntity.Tethering t : new ArrayList<>(pasture.getTetheredPokemon())) {
                Pokemon p;
                try { p = t.getPokemon(); } catch (Throwable ex) { continue; }
                if (p == null) continue;
                Set<String> types = new HashSet<>();
                try {
                    for (ElementalType et : p.getTypes()) {
                        if (et != null && et.getShowdownId() != null) {
                            types.add(et.getShowdownId().toLowerCase(Locale.ROOT));
                        }
                    }
                } catch (Throwable ignored) {
                    // a mon whose types won't read just contributes nothing
                }
                perMon.add(types);
                for (String ty : types) typeCounts.merge(ty, 1, Integer::sum);
                try {
                    String sp = p.getSpecies().getName();
                    if (sp != null && !sp.isBlank()) {
                        String key = sp.toLowerCase(Locale.ROOT);
                        species.add(key);
                        speciesCounts.merge(key, 1, Integer::sum);
                        // Regional forms as aspect-qualified keys ("meowth:alolan") so form-gated rituals
                        // (Ominous Bottle's Alolan crime syndicate) can demand the REAL crew. The plain
                        // species key above still counts too - form mons satisfy species-level gates.
                        try {
                            for (String aspect : p.getAspects()) {
                                if (aspect == null) continue;
                                String a = aspect.toLowerCase(Locale.ROOT);
                                if (a.equals("alolan") || a.equals("galarian") || a.equals("hisuian") || a.equals("paldean")) {
                                    String fk = key + ":" + a;
                                    species.add(fk);
                                    speciesCounts.merge(fk, 1, Integer::sum);
                                }
                            }
                        } catch (Throwable ignored2) { }
                    }
                } catch (Throwable ignored) {
                    // species unreadable → skip (rituals that need a signature species just won't fire)
                }
            }
        } catch (Throwable t) {
            // a Cobblemon API edge must never crash the harvest - return whatever we gathered
        }
        return new PastureMons(perMon, new Composition(typeCounts, species, speciesCounts));
    }
}
