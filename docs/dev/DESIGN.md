# PokéSnack Analytics Engine — Design

A spawn-probability **analytics and optimization engine** for Cobblemon. It models
Cobblemon's spawn algorithm forward (conditions → ranked spawn probabilities), lets you
design **PokéSnacks** (consumable spawn-modifier items), and scores/optimizes a snack for
*hunting a target species* — always answering **"is it worth the cost?"**

> Status: **engine built** (`pokesnack/`, pure Python). Validated against hand-computed
> numbers in `tests/test_validation.py`. Data in `data/spawn_pool.json`. See `README.md`.

---

## 1. Core idea

Cobblemon decides each spawn in two stages, and a PokéSnack can bend both:

1. **Bucket roll** — pick a rarity bucket (`common`, `uncommon`, `rare`, `ultra-rare`)
   by configured bucket weights.
2. **Weighted selection** — among spawn-details that (a) are in the rolled bucket,
   (b) match the spawn **context** (grounded / surface / submerged / seafloor / fishing / lava),
   and (c) pass all `conditions` and fail all `anticonditions` for the current
   environment, pick one weighted by its `weight` (× any `weightMultiplier`).

So the probability the next successful spawn is target species **T** under environment **C** is:

```
P(T | C) =  Σ_d∈details(T)   P(bucket = d.bucket)
                            · 1[d eligible under C]
                            · d.weight / Σ_{e eligible in d.bucket} e.weight
```

A PokéSnack is a **pure transformation** of `(dataset, context, bucketDistribution)`.
Apply the snack, then run the *same* probability engine on the transformed inputs — the
delta between snack and baseline is the snack's effect. This keeps everything composable
and explainable.

> ⚠️ Exact default bucket weights, shiny odds, spawn cadence and cap come from the
> Cobblemon version/config in use — the engine reads them from the provided data source
> rather than hard-coding, since addons and config change them.

---

## 2. The four snack levers (all supported)

| Lever | What it transforms | Example |
|---|---|---|
| **Weight multipliers** | `d.weight` for a selector (species / type / egg group / label) | 3× weight to all Water-types |
| **Bucket shifts** | the bucket distribution, or a species' effective bucket | nudge `rare`→`uncommon`, or +X% to higher buckets |
| **Condition overrides** | the `SpawningContext` (force/relax conditions) | act as if it's night & raining |
| **Context / level / shiny** | context type, level range roll, shiny roll | force `fishing`, raise level band, ×shiny odds |

A snack = an ordered list of these modifiers + metadata: **cost** (ingredients/value),
**duration**, and **effect scope** (radius / per-player). Modifiers compose left-to-right.

---

## 3. The four objectives (all supported, cost-aware)

| Objective | Formula sketch | Needs |
|---|---|---|
| **P(target per spawn)** | `P(T \| C)` above | dataset + context |
| **Expected time-to-find** | `1 / (attempts·min⁻¹ × P(spawn) × P(T\|C))` | spawn rate λ + cap model |
| **Target + shiny** | `P(T\|C) × shinyOdds` | shiny config / charm |
| **Cost-efficiency** | `ΔP(T) / snack.cost`, plus break-even: `E[snacks consumed before hit] × cost` vs. benefit | cost model |

The optimizer treats these as a **multi-objective** problem → reports a **Pareto front**
(or a single pick under user-supplied weights), and every result carries a plain-language
**"worth it?" verdict**: marginal odds gained vs. cost paid, with a break-even estimate.

---

## 3a. Recipe system — Bait Seasonings (CONFIRMED mechanics)

PokéSnacks/PokéCakes (and Poké Bait) are crafted by absorbing **up to 3 Bait Seasonings**.
**Base recipe per snack:** 2 honey bottles + 1 vivichoke + 3 hearty grains + 3 moomoo milk
(+ the 3 seasonings). This base applies to *every* snack regardless of seasonings, so it
dominates the material draw on multi-thousand-snack grinds.
A snack has **9 bites → 9 spawn attempts per snack**. Each bite is an **independent** spawn
drawn from the *same* snack-modified distribution (per-bite effects, CONFIRMED). Core unit:

- `P(≥1 target per snack) = 1 − (1 − P(target | conditions, snack))⁹`
- `E[targets per snack] = 9 × P(target | conditions, snack)`
- **Cost = the 3 seasonings consumed, per 9 spawns.**

**Scope:** nature / gender / hidden-ability seasonings are OUT (user not hunting those) —
this removes all the probabilistic per-bite-% effects. Everything modeled is deterministic.
In-scope seasonings: **rarity bucket boosts, shiny multiplier, type & egg-group weight
multipliers, and EV-yield filters** (Pomeg etc.).

**Combination rule: effects are ADDITIVE, never multiplicative.**

There are two mechanical classes of seasoning:

- **Multiplier class** — effects that list an `Nx` factor. These split into TWO subrules:
  - **Shiny multiplier:** `M = 1 + Σ(mᵢ − 1)` (bonus-additive). *Confirmed via Starf (5x):
    1→5x, 2→9x, 3→13x = 1 + 4k.* The base shiny chance already exists at 1×, so seasonings
    add their bonus.
  - **Type / egg-group weight multipliers:** `M = Σ mᵢ` (pure sum). *CONFIRMED via Chilan
    (Normal 10x): 1→10x, 2→20x, 3→30x = 10n.* Applied to each matching target's spawn
    weight; cross-category bonuses also add (never multiply).

- **Filter/flag class** — "Attracts Pokémon with X" effects that list **no** number (EV
  yield, nature %, gender %, hidden ability). These **restrict/flag the pool** and
  **saturate at one copy** (confirmed: 1 Pomeg → 100% HP-EV mons; 2–3 Pomeg → no change).
  Stacking *different* filters intersects them (Pomeg + Razz → HP-EV *and* Atk-nature).

### Seasoning effect categories (→ which lever they drive)
| Category | Example seasonings | Lever |
|---|---|---|
| Rarity bucket boost | Golden Apple (+1), Glistering Melon (+1), Golden Carrot (+1), **Enchanted Golden Apple (+10)** | bucket shift |
| Shiny multiplier | Golden Apple (2x), Enchanted Golden (10x), Starf (5x) | shiny |
| Type attract (10x) | Passho (Water), Occa (Fire), Tanga (Bug)… one per type | weight mult (by type) |
| Egg-group attract (10x) | Lum (Dragon/Monster), Pecha (Water3/Bug), Cheri (Fairy/Grass)… | weight mult (by egg group) |
| EV-yield attract | Pomeg (HP), Kelpsy (Atk), Qualot (Def)… | weight mult (by EV yield) |
| Nature attract (25/50/75/100%) | Razz/Figy/Touga/Spelon (Atk-nature)… | post-spawn roll |
| Gender attract (25%) | Kee (female), Maranga (male) | post-spawn roll |
| Hidden ability (5%) | Enigma | post-spawn roll |
| Level boost | Leppa (+5), Hopo (+10) | level |
| IV boost (+5) | Lansat (HP)…Salac (Spe) | post-spawn (IV) |
| Friendship (+100) | Jaboca | post-spawn |
| Drops reroll | Rowap | post-spawn (loot) |
| Bite-time reduction | Apple (50%), Oran (33%), Golden Apple (25%)… | spawn rate (time-to-find) |
| Reel chance | Custap (70%), Micle (100%) | fishing success |
| Cosmetic only | Eggant | none (color) |

### Rarity bucket tier table — COMPLETE & DISCRETE (no interpolation)
Boosters are +1 (Golden Apple / Glistering Melon / Golden Carrot) or +10 (Enchanted Golden
Apple), summed across the 3 slots. Only these tiers are reachable:

| Tier | Common | Uncommon | Rare | Ultra Rare | Reachable via |
|---|---|---|---|---|---|
| 0 | 86.2% | 10.28% | 2.51% | 1.01% | no boosters |
| 1 | 77.1% | 15.01% | 5.38% | 2.51% | 1×(+1) |
| 2 | 67.84% | 18.73% | 8.84% | 4.59% | 2×(+1) |
| 3 | 59.37% | 21.31% | 12.35% | 6.97% | 3×(+1) |
| 10 | 28.48% | 24.08% | 27.32% | 20.13% | 1×ench |
| 11 | 26.51% | 23.83% | 28.36% | 21.3% | 1×ench +1 |
| 12 | 24.3% | 23.56% | 29.26% | 22.35% | 1×ench +2 |
| 20 | 17.29% | 21.62% | 33.34% | 27.76% | 2×ench |
| 21 | 16.75% | 21.42% | 33.63% | 28.2% | 2×ench +1 |
| 30 | 13.58% | 20.09% | 35.33% | 31% | 3×ench |

The engine ships this as a lookup table; bucket boost = sum tiers → look up distribution.

### Recipe layer = `combine(≤3 seasonings) → ModifierBundle`
A pure function: take the seasoning multiset, fold each into the additive bundle
(bucket tier sum, type/egg/EV weight bonuses, shiny multiplier, level add, per-bite roll
probabilities, bite-time reduction), and attach **cost = the seasoning multiset**.

## 4. Module layout (proposed: Python)

Python is the natural fit — analytics, `scipy.optimize`, pandas, scikit-learn for the ML
arm, and notebooks for snack experimentation. Engine-first: a clean library + thin CLI,
web/overlay can come later.

```
pokesnack/
  ingest/        # parse Cobblemon spawn JSON / datapacks / curated dataset → normalized records
  core/          # domain model: SpawningContext, SpawnDetail, Condition evaluation, BucketDistribution
  predict/       # probability engine: eligibility filter → P(species), level dist, shiny prob
  snacks/        # PokeSnack + Modifier types; apply(snack) → transformed (dataset, context, buckets)
  analytics/     # objective functions, baseline-vs-snack delta, "worth it" verdict
  optimize/      # search snack designs under cost budget; Pareto front / scalarized
  forecast/      # (phase 2) ML on observed spawn logs: calibrate λ & caps, validate predictions
  cli.py         # thin command-line / notebook entry points
```

**Why this split:** `predict` never knows about snacks; `snacks` only transforms inputs;
`analytics`/`optimize` only call `predict`. Each layer is independently testable, and the
deterministic engine is the ground truth the ML arm calibrates against.

---

## 5. Deterministic engine vs. ML forecasting

- **Deterministic (primary):** replicates Cobblemon's code exactly — "what *can* spawn and
  with what odds per attempt." Trustworthy, explainable, no training data needed.
- **ML (phase 2, optional):** real worlds add spawn caps, despawns, competing players, and
  uneven biome exposure. Feed observed spawn logs to (a) estimate the true effective spawn
  rate λ and cap effects (which the time-to-find objective needs), and (b) learn a residual
  correction so predictions match *your* server. ML calibrates the deterministic model; it
  doesn't replace it.

---

## 6. Build phases

1. **Ingestion + domain model** — parse the data source into `SpawnDetail` + species metadata.
2. **Probability engine** — `P(species | context)`, validated against hand-checked cases.
3. **PokéSnack model** — the four levers as composable transforms + cost metadata.
4. **Analytics** — four objectives + baseline delta + "worth it" verdict.
5. **Optimizer** — search snack designs under a cost budget (Pareto / weighted).
6. **ML forecasting** — calibrate λ/caps and validate against spawn logs.

---

## 7. Open questions (blocking implementation)

1. **Data source** (user providing): raw Cobblemon spawn JSON / datapack dir, or a curated
   dataset? Determines the `ingest` parser.
2. **Cost model**: what is a snack's "cost" measured in — crafting ingredients, an abstract
   point budget, in-game currency? Defines the cost-efficiency objective & optimizer constraint.
3. **Snack mechanics realism**: are PokéSnacks a real Cobblemon/addon feature with fixed
   rules, or a *new mechanic you're designing*? (Affects how freely the optimizer may invent
   modifier combinations.)
4. **Language confirm**: Python assumed — OK?
```
