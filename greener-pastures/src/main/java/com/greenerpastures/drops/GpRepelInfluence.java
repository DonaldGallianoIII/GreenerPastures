package com.greenerpastures.drops;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;

import java.util.Map;
import java.util.function.Supplier;

/**
 * The Snack Repel's spawn-weight influence (Snack Overdrive pt.1): DIVIDES a candidate's spawn weight when
 * its form carries a repelled type — the exact inverse of Cobblemon's attract math ({@code newWeight *=
 * value} in {@code SpawnBaitInfluence.affectWeight}). Ours handles EVERY repelled type independently (their
 * TYPING path only ever applies the FIRST entry — decompile-verified), never returns a negative weight, and
 * fails soft: any resolution hiccup leaves the weight untouched. Added to the snack block's spawner by
 * {@code PokeSnackRepelMixin}; scope = snack-driven spawns only, ambient world spawning is untouched.
 */
public final class GpRepelInfluence implements SpawningInfluence {
    private final Supplier<Map<String, Integer>> repels;

    public GpRepelInfluence(Supplier<Map<String, Integer>> repels) {
        this.repels = repels;
    }

    @Override
    public float affectWeight(SpawnDetail detail, SpawnablePosition spawnablePosition, float weight) {
        try {
            Map<String, Integer> reps = repels.get();
            if (reps == null || reps.isEmpty() || weight <= 0.0f) return weight;
            if (!(detail instanceof PokemonSpawnDetail pd)) return weight;
            String speciesName = pd.getPokemon().getSpecies();
            if (speciesName == null) return weight;
            Species species = PokemonSpecies.getByName(speciesName);
            if (species == null) return weight;
            FormData form = species.getForm(pd.getPokemon().getAspects());
            float w = weight;
            for (Map.Entry<String, Integer> e : reps.entrySet()) {
                if (e.getValue() == null || e.getValue() < 2) continue;
                ElementalType t = ElementalTypes.get(e.getKey());
                if (t == null) continue;
                for (ElementalType formType : form.getTypes()) {
                    if (formType == t) {
                        w /= e.getValue();
                        break;
                    }
                }
            }
            return w;
        } catch (Throwable ignored) {
            return weight;   // repelling must never break spawning
        }
    }
}
