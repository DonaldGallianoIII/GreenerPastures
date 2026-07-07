# PokeSnack Analytics Engine

Predicts Cobblemon spawn odds and optimizes **PokeSnack** recipes (Bait Seasonings) for
hunting a target species. Pure Python, no dependencies. See `DESIGN.md` for the full model
and `data/spawn_pool.md` for the hand-captured spawn data.

## Model in one breath
Each bite is a two-stage roll: **(1)** pick a rarity bucket from the tier table (shifted by
bucket-boost seasonings), **(2)** pick a mon inside that bucket weighted by `weight × boosts`
(type/egg seasonings reweight here). A snack = up to 3 seasonings = 9 bites. Combination
rules (reverse-engineered in-game): bucket tiers **add**, shiny is **bonus-additive**
`1+Σ(m−1)`, type/egg weights are a **pure sum** `Σm`.

## Quick start
```bash
python3 -m pokesnack biomes
# best shiny-Ditto snack if you have no Enchanted Golden Apples:
python3 -m pokesnack --biome mansion optimize ditto --exclude enchanted_golden_apple
# what a real 68-snack run looks like:
python3 -m pokesnack run --snack chilan,golden_apple,starf --snacks 68 --target ditto
# how unlucky was that shiny Duraludon?
python3 -m pokesnack versus duraludon ditto --snack chilan,golden_apple,starf
# restrict the search to only what you actually own:
python3 -m pokesnack optimize ditto --have chilan,starf,golden_apple,golden_carrot
```

As a library:
```python
from pokesnack import analytics, optimize
from pokesnack.pool import load_pool
pool = load_pool()
print(analytics.outlook(pool, "mansion", "grounded", ["chilan","starf","starf"], "ditto"))
print(optimize.best(pool, "mansion", "grounded", "ditto", available=["chilan","starf"]))
```

## Layout
- `pokesnack/gamedata.py` — tier table + Bait Seasoning registry (fixed game data).
- `pokesnack/pool.py` — loads `data/spawn_pool.json` (biome→context→bucket rosters).
- `pokesnack/engine.py` — `combine()` (recipe) + `per_bite_distribution()` (probabilities).
- `pokesnack/analytics.py` — outlook, run breakdown, head-to-head.
- `pokesnack/optimize.py` — rank/best snack over the 3-seasoning design space.
- `pokesnack/cli.py` — command line.
- `tests/test_validation.py` — reproduces the hand-computed numbers from the design chat.

## Validation
`python3 tests/test_validation.py` confirms the engine matches by-hand results:
1 Chilan + 2 Starf ≈ 2,637 snacks/shiny Ditto; the 68-snack run (~34 Dittos, ~13 Duraludon,
~0.025 shiny Ditto); shiny Dura-vs-Ditto = 28%/72%.

## Known data gaps
- Common bucket: only 7 of 40 species captured (+ an untyped `common_remainder` lump), so
  shiny-common-Normal estimates are a **lower bound**. Ditto/uncommon math is exact.
- Cost model is qualitative (material notes only); add a real gold↔time rate to make the
  "is it worth it?" verdict quantitative.
- EV-yield filter seasonings are wired but species EV-yield tags aren't in the dataset yet.
