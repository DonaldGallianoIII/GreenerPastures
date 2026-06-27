# Spawn Pool — captured from verbal description

> Hand-captured spawn data (no JSON available). This is the dataset the engine's `ingest`
> layer will read.
>
> **Two-stage spawn model:**
> 1. **Bucket roll** — common / uncommon / rare / ultra-rare, from the tier distribution
>    (shifted by bucket-boost seasonings).
> 2. **Within-bucket pick** — among mons in the procced bucket eligible for biome + context,
>    weighted by `weight`. Type/egg/EV seasonings reweight HERE. The `%` columns below are
>    the **baseline (tier 0, no-seasoning) within-bucket shares** = usable as weights.

---

## Biome: Dark Forest → Haunted Mansion · Context: grounded

**Hunt target: SHINY DITTO** (uncommon bucket).

### COMMON bucket — 40 species total (top 7 captured)
| Species | Within-bucket % | Type | Notes |
|---|---|---|---|
| Minccino | 10% | Normal | |
| Sinistea | 10% | Ghost | |
| Spinarak | 10% | Bug/Poison | |
| Tandemaus | 10% | Normal | |
| Espurr | 9.57% | Psychic | |
| Swirlix | 6.06% | Fairy | |
| Purrloin | 5.74% | Dark | |
| Patrat | ? (unlisted) | Normal | confirmed present via shiny catch; one of the unlisted |
| Lechonk | ? (unlisted) | Normal | confirmed present via shiny catch (→ Oinkologne) |
| _(+31 unlisted)_ | ~38.6% remaining | | fill later if needed |

> Observed shiny catches under 1 Chilan builds: Minccino, Tandemaus, Patrat, Lechonk (all
> common Normal-types) — Chilan ×10 floods the common bucket with Normals (~80% est.). Side
> effect only; does NOT affect Ditto (uncommon) odds. ~10 shiny common-Normals per shiny Ditto.

### UNCOMMON bucket — 14 species (COMPLETE) ← Ditto lives here
| Species | Within-bucket % | Type | Notes |
|---|---|---|---|
| Meowth (Alolan) | 53.010784% | **Dark** | "Team Rocket / dark cat"; Alolan (evolves to Persian, not Perrserker) |
| **Ditto** 🎯 | 10.518013% | Normal | hunt target |
| Mimikyu | 10.518013% | Ghost/Fairy | |
| Persian (Alolan) | 10.097293% | **Dark** | Alolan line (confirmed Dark) |
| Eevee | 9.676572% | Normal | only other Normal competitor |
| Rotom | 5.2590065% | Electric/Ghost | |
| Vaporeon | 0.13147517% | Water | |
| Jolteon | 0.13147517% | Electric | |
| Flareon | 0.13147517% | Fire | |
| Espeon | 0.13147517% | Psychic | |
| Umbreon | 0.13147517% | Dark | |
| Leafeon | 0.13147517% | Grass | |
| Glaceon | 0.13147517% | Ice | |
| Sylveon | 0.13147517% | Fairy | |

> Non-Normal share of uncommon ≈ 63% (Meowth+Persian Dark) → Normal boost strongly favors
> Ditto/Eevee. Sums to ~100.13% (rounding).

### RARE bucket — 2 species (COMPLETE) — default sub-area
| Species | Within-bucket % | Type | Notes |
|---|---|---|---|
| Yamask | 91% | Ghost | |
| Cofagrigus | 9% | Ghost | Yamask's evo |

### RARE bucket — DURALUDON sub-area (some portions of the mansion)
| Species | Within-bucket % | Type | Notes |
|---|---|---|---|
| Yamask | 54.05% | Ghost | |
| Duraludon | 40.54% | Steel/Dragon | only in some portions |
| Cofagrigus | 5.41% | Ghost | Yamask's evo |
> Yamask:Cofagrigus still ~10:1; Duraludon weight ≈ 0.75× Yamask. Bucket-boost snacks raise
> Duraludon odds (rare bucket) while Chilan does nothing for it (not Normal-type).

### RARE bucket — ENCASED PLATFORM (Applin rare-roster) ← new hunting ground
> Pillar up outside the Ditto mansion, build a platform, and **encase it** — spawn-proofing the
> surroundings funnels the whole spawn cap onto the one controlled surface. On this platform the
> rare bucket rolls **Applin in place of Duraludon**. Biome `mansion_applin` (inherits mansion).

| Species | Within-bucket % | Type | Notes |
|---|---|---|---|
| Yamask | 56.18% | Ghost | |
| Applin | 38.2% | Grass/Dragon | replaces Duraludon; effective weight ≈ 0.68× Yamask |
| Cofagrigus | 5.62% | Ghost | Yamask's evo |
> Same skeleton as the Duraludon sub-area (Yamask:Cofagrigus ≈ 10:1; third slot ~62 weight vs
> Duraludon's ~68). Applin is Grass/Dragon, so `rindo` (Grass) and `haban` (Dragon) type
> seasonings reweight it — and being rare-bucket, it loves **bucket-boost** (EGA), not Chilan.
> ⚠️ Uncommon (Ditto) bucket assumed unchanged → this swap does NOT affect shiny-Ditto odds.
> Open Q: does the platform also reshape the uncommon bucket / spawn density? If so, capture it.

### ULTRA-RARE bucket — 1 species (COMPLETE)
| Species | Within-bucket % | Type | Notes |
|---|---|---|---|
| Blacephalon | 100% | Fire/Ghost | Ultra Beast "clown" |
