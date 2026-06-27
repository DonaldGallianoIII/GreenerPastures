"""PokeSnack analytics engine for Cobblemon spawn prediction.

Models the two-stage Cobblemon spawn algorithm and the Bait-Seasoning recipe system
(reverse-engineered empirically) to predict spawn odds and optimize PokeSnack recipes
for hunting a target species. See DESIGN.md for the full model.
"""
from .model import Seasoning, SpawnEntry, ModifierBundle
from .gamedata import SEASONINGS, TIER_TABLE, BASE_SHINY_RATE
from .pool import SpawnPool, load_pool
from .engine import combine, per_bite_distribution, weight_multiplier
from . import analytics, optimize

__all__ = [
    "Seasoning", "SpawnEntry", "ModifierBundle",
    "SEASONINGS", "TIER_TABLE", "BASE_SHINY_RATE",
    "SpawnPool", "load_pool",
    "combine", "per_bite_distribution", "weight_multiplier",
    "analytics", "optimize",
]
