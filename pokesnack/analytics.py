"""Objectives and run breakdowns built on the probability engine."""
from __future__ import annotations

from dataclasses import dataclass
from math import expm1

from .engine import per_bite_distribution, shiny_per_bite
from .gamedata import resolve_snack

BITES_PER_SNACK = 9


def _p_at_least_one(p_per_bite, bites):
    # 1 - (1-p)^bites, numerically stable for tiny p.
    if p_per_bite <= 0:
        return 0.0
    return -expm1(bites * _log1p(-p_per_bite))


def _log1p(x):
    from math import log1p
    return log1p(x)


@dataclass
class SpeciesOutlook:
    species: str
    p_per_bite: float
    expected_per_snack: float
    shiny_p_per_bite: float
    expected_shiny_per_snack: float
    snacks_per_shiny: float           # inf if impossible
    p_shiny_in_one_snack: float


def outlook(pool, biome, context, snack_keys, species,
            base_shiny_rate=None) -> SpeciesOutlook:
    """Full forecast for one target species under a snack."""
    base = base_shiny_rate or pool.base_shiny_rate
    dist, bundle = per_bite_distribution(pool, biome, context, snack_keys)
    p = dist.get(species, 0.0)
    sp = shiny_per_bite(dist, bundle, species, base)
    exp_shiny_snack = BITES_PER_SNACK * sp
    return SpeciesOutlook(
        species=species,
        p_per_bite=p,
        expected_per_snack=BITES_PER_SNACK * p,
        shiny_p_per_bite=sp,
        expected_shiny_per_snack=exp_shiny_snack,
        snacks_per_shiny=(1.0 / exp_shiny_snack) if exp_shiny_snack > 0 else float("inf"),
        p_shiny_in_one_snack=_p_at_least_one(sp, BITES_PER_SNACK),
    )


def run_breakdown(pool, biome, context, snack_keys, n_snacks, base_shiny_rate=None):
    """Expected counts over an n_snacks session: bucket rolls, species seen, shinies."""
    from .gamedata import tier_distribution
    base = base_shiny_rate or pool.base_shiny_rate
    dist, bundle = per_bite_distribution(pool, biome, context, snack_keys)
    bites = BITES_PER_SNACK * n_snacks
    tier_row = tier_distribution(bundle.bucket_tier)

    bucket_rolls = {b: prob * bites for b, prob in tier_row.items()}
    species_seen = {sp: p * bites for sp, p in dist.items()}
    shiny_seen = {sp: p * bundle.shiny_mult / base * bites for sp, p in dist.items()}
    expected_total_shinies = sum(shiny_seen.values())
    return {
        "n_snacks": n_snacks,
        "bites": bites,
        "bucket_tier": bundle.bucket_tier,
        "shiny_mult": bundle.shiny_mult,
        "bucket_rolls": bucket_rolls,
        "species_seen": species_seen,
        "shiny_seen": shiny_seen,
        "expected_total_shinies": expected_total_shinies,
        "p_at_least_one_shiny_any": _p_at_least_one(
            expected_total_shinies / bites if bites else 0, bites),
    }


def head_to_head(pool, biome, context, snack_keys, species_a, species_b,
                 base_shiny_rate=None):
    """Given a shiny that is A or B, P(it is A) vs P(it is B). Shiny mult cancels."""
    base = base_shiny_rate or pool.base_shiny_rate
    dist, bundle = per_bite_distribution(pool, biome, context, snack_keys)
    a = shiny_per_bite(dist, bundle, species_a, base)
    b = shiny_per_bite(dist, bundle, species_b, base)
    tot = a + b
    if tot <= 0:
        return {species_a: 0.0, species_b: 0.0, "ratio_a_over_b": float("nan")}
    return {
        species_a: a / tot,
        species_b: b / tot,
        "ratio_a_over_b": (a / b) if b > 0 else float("inf"),
    }


def estimate_cost(snack_keys):
    """Rough material-cost tags for a snack (qualitative; refine with a real cost model)."""
    s = resolve_snack(snack_keys)
    return ", ".join(season.note for season in s if season.note) or "negligible"


def snack_ingredients(n_snacks, snack_keys=()):
    """Total ingredient draw to craft n_snacks of a given build: base recipe x n plus
    the seasonings consumed (each snack uses its 3 seasonings)."""
    from .gamedata import SNACK_BASE_RECIPE
    totals = {k: v * n_snacks for k, v in SNACK_BASE_RECIPE.items()}
    for key in snack_keys:
        totals[key] = totals.get(key, 0) + n_snacks
    return totals
