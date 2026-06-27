# Analysis — Shiny Ditto @ Dark Forest / Haunted Mansion

Hand-computed with the confirmed model (to be automated by the engine). Source data:
`data/spawn_pool.md`. Base shiny rate **1/8192**, no charm/config boost.

## Model
```
P(shiny Ditto / bite) = P_uncommon(tier) × share_Ditto(m_N) × (M_shiny / 8192)
E[shiny Ditto / snack] = 9 × P(shiny Ditto / bite)
snacks per shiny       = 1 / E[shiny Ditto / snack]
```
- `P_uncommon(tier)`: tier table lookup (tier0=10.28% … tier10=24.08% … tier30=20.09%).
- `share_Ditto(m_N) = 10.518·m_N / (20.195·m_N + 79.937)` — Normal boost (Chilan, m_N=10·#)
  applies to Ditto + Eevee only; Meowth+Persian (Dark, ~63%) are starved.
- `M_shiny = 1 + Σ(mᵢ−1)`: EGA +9, Starf +4, Golden Apple +1.
- Bucket tier additive: EGA +10, Golden Apple/Glistering Melon/Golden Carrot +1.

## Contender ladder (snacks per shiny, lower = better)
| Snack | Tier | Ditto share | Shiny× | Snacks/shiny |
|---|---|---|---|---|
| baseline (none) | 0 | 10.5% | 1 | ~84,300 |
| 3× Chilan | 0 | 46% | 1 | ~19,200 |
| 3× Golden Apple | 3 | 10.5% | 4 | ~10,200 |
| 3× Starf | 0 | 10.5% | 13 | ~6,500 |
| 3× Enchanted Golden Apple | 30 | 10.5% | 28 | ~1,540 |
| 1× EGA + 2× Chilan | 10 | 37% | 10 | ~870 |
| 1× EGA + 1 Chilan + 1 Starf | 10 | 37% | 14 | ~720 |
| **2× EGA + 1× Chilan** 🏆 | 20 | 37% | 19 | **~594** |

## Takeaways
- **Optimal (raw odds): 2× Enchanted Golden Apple + 1× Chilan** → ~594 snacks/shiny, **142× vs baseline**.
- Pure shiny-stacking (3 EGA) is a trap: 3rd slot is worth more as Normal boost (share 10.5%→37%) than extra shiny.
- Chilan only works because Meowth+Persian are Alolan **Dark** — it starves the 63% hog.
- 2 EGA beat 1 EGA: shiny 10→19× outweighs the tier-10→20 uncommon-proc dip.

## Constraint: NO Enchanted Golden Apples available
Re-optimized without EGA (shiny ceiling = Starf +4/slot; bucket tier maxes at 3).

| Snack | Tier | Ditto share | Shiny× | Snacks/shiny |
|---|---|---|---|---|
| baseline | 0 | 10.5% | 1 | ~84,300 |
| 3× Chilan (free) | 0 | 46% | 1 | ~19,200 |
| 3× Starf | 0 | 10.5% | 13 | ~6,500 |
| 1 Chilan + 1 Starf + 1 Golden Apple | 1 | 37% | 6 | ~2,710 |
| **1× Chilan + 2× Starf** 🏆 | 0 | 37% | 9 | **~2,640** |

- **No-EGA optimum: 1 Chilan + 2 Starf** → ~2,640 snacks/shiny (32× vs baseline).
- Chilan is now mandatory (no double-duty slot exists); starving the 63% Dark hog beats
  pure shiny. A no-Chilan build (2 Starf + Golden Apple) is ~6,400 — 2× worse.
- Bucket boosting is dead without EGA: tier 1–3 barely move uncommon-proc, not worth a slot.
- PENDING: confirm Starf is actually obtainable for the user; re-optimize within real kit.

## OPEN: cost model
Winner needs ~2 Enchanted Golden Apples/snack × ~594 snacks = huge gold cost. Cheap-berry
route (3× Starf, ~6,500 snacks) is ~11× slower but near-free. True cost-efficient pick needs
the user's gold↔grind-time exchange rate. TBD.
