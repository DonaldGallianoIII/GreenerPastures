"""The probability engine: snack -> modifier bundle -> per-bite spawn distribution.

Two-stage Cobblemon model:
  1. bucket roll      P(bucket)        from the tier table (bucket-boost seasonings)
  2. within-bucket pick  w_i / sum w   weighted by boosted weight (type/egg seasonings)
"""
from __future__ import annotations

from .gamedata import resolve_snack, tier_distribution, BASE_SHINY_RATE
from .model import ModifierBundle, SpawnEntry


def combine(snack_keys) -> ModifierBundle:
    """Fold up to 3 seasonings into their aggregate effect (the recipe layer)."""
    seasonings = resolve_snack(snack_keys)
    bundle = ModifierBundle()
    shiny_bonus = 0.0
    for s in seasonings:
        bundle.bucket_tier += s.bucket_boost
        if s.shiny_mult is not None:
            shiny_bonus += (s.shiny_mult - 1.0)          # bonus-additive
        if s.type_target is not None:
            bundle.type_mults[s.type_target] = (          # pure sum
                bundle.type_mults.get(s.type_target, 0.0) + s.type_mult)
        for g in s.egg_targets:
            bundle.egg_mults[g] = bundle.egg_mults.get(g, 0.0) + s.egg_mult
        if s.ev_filter is not None:
            bundle.ev_filters.add(s.ev_filter)
    bundle.shiny_mult = 1.0 + shiny_bonus
    return bundle


def weight_multiplier(entry: SpawnEntry, bundle: ModifierBundle) -> float:
    """Stage-2 weight multiplier for one mon. Pure sum of every applicable Nx
    boost (type and/or egg group); 1.0 if nothing targets it."""
    applicable = []
    for t in entry.types:
        if t in bundle.type_mults:
            applicable.append(bundle.type_mults[t])
    for g in entry.egg_groups:
        if g in bundle.egg_mults:
            applicable.append(bundle.egg_mults[g])
    return sum(applicable) if applicable else 1.0


def per_bite_distribution(pool, biome, context, snack_keys=()):
    """Return (dist, bundle) where dist[species] = P(a given bite spawns that species).

    Assumes every bite produces a mon (the 9-bites model). Shiny is layered on
    separately via bundle.shiny_mult.
    """
    bundle = combine(snack_keys)
    tier_row = tier_distribution(bundle.bucket_tier)
    dist = {}
    for bucket, bucket_prob in tier_row.items():
        if bucket_prob <= 0:
            continue
        roster = pool.roster(biome, context, bucket)
        if bundle.ev_filters:
            roster = [e for e in roster
                      if bundle.ev_filters & set(getattr(e, "ev_yields", ()))]
        weighted = []
        total = 0.0
        for e in roster:
            w = e.weight * weight_multiplier(e, bundle)
            weighted.append((e.species, w))
            total += w
        if total <= 0:
            continue
        for species, w in weighted:
            dist[species] = dist.get(species, 0.0) + bucket_prob * (w / total)
    return dist, bundle


def shiny_per_bite(dist, bundle, species, base_shiny_rate=BASE_SHINY_RATE):
    """P(a given bite spawns a SHINY of `species`)."""
    return dist.get(species, 0.0) * bundle.shiny_mult / base_shiny_rate
