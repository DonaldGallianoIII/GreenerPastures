"""Spawn pool ingestion: load the JSON dataset into SpawnEntry rosters."""
from __future__ import annotations

import json
from copy import deepcopy
from pathlib import Path

from .model import SpawnEntry

_DEFAULT_PATH = Path(__file__).resolve().parent.parent / "data" / "spawn_pool.json"


class SpawnPool:
    """Holds biome -> context -> bucket -> [SpawnEntry], with biome inheritance."""

    def __init__(self, raw: dict):
        self.base_shiny_rate = raw.get("base_shiny_rate", 8192)
        self._species_types = raw.get("species_types", {})
        self._species_eggs = raw.get("species_egg_groups", {})
        self._raw_biomes = raw["biomes"]
        self._resolved = {name: self._resolve_biome(name) for name in self._raw_biomes}

    # -- inheritance resolution ------------------------------------------------
    def _resolve_biome(self, name: str) -> dict:
        biome = self._raw_biomes[name]
        parent = biome.get("inherits")
        contexts = {}
        if parent:
            contexts = deepcopy(self._resolve_biome(parent))
        for ctx, buckets in biome.get("contexts", {}).items():
            contexts.setdefault(ctx, {})
            for bucket, entries in buckets.items():
                contexts[ctx][bucket] = entries  # child overrides whole bucket
        return contexts

    # -- queries ---------------------------------------------------------------
    def biomes(self):
        return sorted(self._raw_biomes)

    def display(self, biome: str) -> str:
        return self._raw_biomes[biome].get("display", biome)

    def contexts(self, biome: str):
        return sorted(self._resolved[biome])

    def buckets(self, biome: str, context: str):
        return self._resolved[biome][context]

    def roster(self, biome: str, context: str, bucket: str):
        if biome not in self._resolved:
            raise ValueError(f"Unknown biome '{biome}'. Known: {self.biomes()}")
        ctx = self._resolved[biome].get(context, {})
        out = []
        for species, weight in ctx.get(bucket, []):
            out.append(SpawnEntry(
                species=species,
                weight=float(weight),
                types=tuple(self._species_types.get(species, [])),
                egg_groups=tuple(self._species_eggs.get(species, [])),
            ))
        return out


def load_pool(path: str | Path | None = None) -> SpawnPool:
    path = Path(path) if path else _DEFAULT_PATH
    with open(path) as fh:
        return SpawnPool(json.load(fh))
