"""Search the 3-seasoning design space for the best snack to hunt a target."""
from __future__ import annotations

from dataclasses import dataclass
from itertools import combinations_with_replacement

from .analytics import outlook
from .gamedata import SEASONINGS, tier_distribution


@dataclass
class Candidate:
    snack: tuple
    metric: float            # the value being maximized
    snacks_per_shiny: float
    expected_per_snack: float
    p_shiny_in_one_snack: float

    def label(self):
        return " + ".join(SEASONINGS[k].display for k in self.snack) or "(no snack)"


def _is_reachable(snack_keys):
    tier = sum(SEASONINGS[k].bucket_boost for k in snack_keys)
    try:
        tier_distribution(tier)
        return True
    except ValueError:
        return False


def candidate_snacks(available=None, min_slots=1, max_slots=3):
    """All distinct seasoning multisets of size [min_slots, max_slots]."""
    keys = sorted(available) if available is not None else sorted(SEASONINGS)
    seen = set()
    for n in range(min_slots, max_slots + 1):
        for combo in combinations_with_replacement(keys, n):
            if combo in seen:
                continue
            if not _is_reachable(combo):
                continue
            seen.add(combo)
            yield combo


def rank(pool, biome, context, target, available=None, shiny=True,
         max_slots=3, top=None, base_shiny_rate=None):
    """Rank candidate snacks for a target.

    objective: shiny=True maximizes shiny rate (minimizes snacks-per-shiny);
               shiny=False maximizes raw expected target per snack.
    """
    results = []
    for combo in candidate_snacks(available, min_slots=0 if False else 1, max_slots=max_slots):
        out = outlook(pool, biome, context, combo, target, base_shiny_rate)
        metric = out.shiny_p_per_bite if shiny else out.p_per_bite
        results.append(Candidate(
            snack=combo,
            metric=metric,
            snacks_per_shiny=out.snacks_per_shiny,
            expected_per_snack=out.expected_per_snack,
            p_shiny_in_one_snack=out.p_shiny_in_one_snack,
        ))
    results.sort(key=lambda c: c.metric, reverse=True)
    return results[:top] if top else results


def best(pool, biome, context, target, **kw):
    ranked = rank(pool, biome, context, target, top=1, **kw)
    return ranked[0] if ranked else None
