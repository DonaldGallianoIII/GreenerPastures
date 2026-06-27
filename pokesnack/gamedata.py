"""Fixed Cobblemon game data: rarity-bucket tier table + Bait Seasoning registry.

Numbers transcribed from the in-game Bait Seasoning / Poke Snack rarity tables.
"""
from __future__ import annotations

from .model import Seasoning

BASE_SHINY_RATE = 8192  # 1 / 8192 baseline; seasoning shiny multipliers scale this.

# One PokeSnack/PokeCake = this base recipe + up to 3 Bait Seasonings, and yields 9 bites.
SEASONING_SLOTS = 3
SNACK_BASE_RECIPE = {
    "honey_bottle": 2,
    "vivichoke": 1,
    "hearty_grains": 3,
    "moomoo_milk": 3,
}

BUCKETS = ("common", "uncommon", "rare", "ultra_rare")

# Tier -> raw [common, uncommon, rare, ultra_rare] percentages (sum ~100).
# Only these tiers are reachable: +1 boosters (x3) and/or +10 boosters (Enchanted
# Golden Apple), summed across the 3 seasoning slots.
_TIER_TABLE_RAW = {
    0:  [86.2,  10.28, 2.51,  1.01],
    1:  [77.1,  15.01, 5.38,  2.51],
    2:  [67.84, 18.73, 8.84,  4.59],
    3:  [59.37, 21.31, 12.35, 6.97],
    10: [28.48, 24.08, 27.32, 20.13],
    11: [26.51, 23.83, 28.36, 21.30],
    12: [24.30, 23.56, 29.26, 22.35],
    20: [17.29, 21.62, 33.34, 27.76],
    21: [16.75, 21.42, 33.63, 28.20],
    30: [13.58, 20.09, 35.33, 31.00],
}


def _normalize(row):
    total = sum(row)
    return {b: v / total for b, v in zip(BUCKETS, row)}


# Tier -> {bucket: probability} (normalized to sum 1).
TIER_TABLE = {tier: _normalize(row) for tier, row in _TIER_TABLE_RAW.items()}


def tier_distribution(tier: int) -> dict:
    """Bucket probabilities for a total bucket tier (validates reachability)."""
    if tier not in TIER_TABLE:
        raise ValueError(
            f"Bucket tier {tier} is not reachable with 3 seasoning slots. "
            f"Reachable tiers: {sorted(TIER_TABLE)}"
        )
    return TIER_TABLE[tier]


# --- Seasoning registry (spawn-affecting levers only) ------------------------
# Nature / gender / hidden-ability seasonings are intentionally omitted (out of
# scope). Type/egg multipliers are 10x in-game.

_TYPE_BERRIES = {
    "tanga": "Bug", "colbur": "Dark", "haban": "Dragon", "rindo": "Grass",
    "wacan": "Electric", "roseli": "Fairy", "chople": "Fighting", "occa": "Fire",
    "coba": "Flying", "kasib": "Ghost", "shuca": "Ground", "yache": "Ice",
    "chilan": "Normal", "kebia": "Poison", "payapa": "Psychic", "charti": "Rock",
    "babiri": "Steel", "passho": "Water",
}

_EGG_BERRIES = {
    "lum": ("Dragon", "Monster"), "pecha": ("Water3", "Bug"),
    "cheri": ("Fairy", "Grass"), "chesto": ("Human-Like", "Flying"),
    "rawst": ("Field",), "aspear": ("Water1", "Water2"),
    "persim": ("Mineral", "Amorphous"),
}

_EV_BERRIES = {
    "pomeg": "HP", "kelpsy": "Attack", "qualot": "Defense",
    "hondew": "SpAttack", "grepa": "SpDefense", "tamato": "Speed",
}


def _build_seasonings():
    s = {}

    # Bucket + shiny powerhouses
    s["golden_apple"] = Seasoning(
        "golden_apple", "Golden Apple", bucket_boost=1, shiny_mult=2.0,
        note="cost: 8 gold ingots; also -25% bite time")
    s["enchanted_golden_apple"] = Seasoning(
        "enchanted_golden_apple", "Enchanted Golden Apple", bucket_boost=10,
        shiny_mult=10.0, note="cost: 8 gold blocks; also -10% bite time")
    s["glistering_melon"] = Seasoning(
        "glistering_melon", "Glistering Melon Slice", bucket_boost=1,
        note="cheap bucket (gold nuggets), no shiny")
    s["golden_carrot"] = Seasoning(
        "golden_carrot", "Golden Carrot", bucket_boost=1,
        note="cheap bucket (gold nuggets), no shiny")

    # Pure shiny
    s["starf"] = Seasoning("starf", "Starf Berry", shiny_mult=5.0)

    # Type attract (10x, pure sum)
    for key, typ in _TYPE_BERRIES.items():
        s[key] = Seasoning(key, f"{key.title()} Berry", type_target=typ, type_mult=10.0,
                           note=f"+10x weight to {typ}-type (pure sum)")

    # Egg-group attract (10x)
    for key, groups in _EGG_BERRIES.items():
        s[key] = Seasoning(key, f"{key.title()} Berry", egg_targets=groups, egg_mult=10.0,
                           note=f"+10x weight to egg groups {groups}")

    # EV-yield filters (restrict pool; saturate at one copy)
    for key, stat in _EV_BERRIES.items():
        s[key] = Seasoning(key, f"{key.title()} Berry", ev_filter=stat,
                           note=f"filter to {stat}-EV-yield mons")

    return s


SEASONINGS = _build_seasonings()


def resolve_snack(keys):
    """Validate a snack (<=3 seasoning keys) and return Seasoning objects."""
    keys = list(keys)
    if len(keys) > 3:
        raise ValueError(f"A snack holds at most 3 seasonings, got {len(keys)}: {keys}")
    out = []
    for k in keys:
        if k not in SEASONINGS:
            raise ValueError(f"Unknown seasoning '{k}'. Known: {sorted(SEASONINGS)}")
        out.append(SEASONINGS[k])
    return out
