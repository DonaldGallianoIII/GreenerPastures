"""Core domain dataclasses."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional


@dataclass(frozen=True)
class Seasoning:
    """A Bait Seasoning. Only the spawn-affecting levers are modeled.

    Combination rules (confirmed empirically, see DESIGN.md):
      - bucket_boost: additive across slots (tiers sum).
      - shiny_mult:   bonus-additive  -> M = 1 + sum(m_i - 1).
      - type/egg mult: pure sum        -> M = sum(m_i).
    """
    key: str
    display: str
    bucket_boost: int = 0
    shiny_mult: Optional[float] = None
    type_target: Optional[str] = None
    type_mult: float = 0.0
    egg_targets: tuple = ()
    egg_mult: float = 0.0
    ev_filter: Optional[str] = None
    note: str = ""


@dataclass(frozen=True)
class SpawnEntry:
    """One species sitting in a (biome, context, bucket) roster."""
    species: str
    weight: float
    types: tuple = ()
    egg_groups: tuple = ()


@dataclass
class ModifierBundle:
    """The aggregate effect of a snack's seasonings on the spawn algorithm."""
    bucket_tier: int = 0
    shiny_mult: float = 1.0
    type_mults: dict = field(default_factory=dict)   # type name -> summed multiplier
    egg_mults: dict = field(default_factory=dict)     # egg group -> summed multiplier
    ev_filters: set = field(default_factory=set)
