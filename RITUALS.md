# 🔮 Rituals & Type-Drops — design doc (DRAFT for Deuce to edit)

_The custom-drop layer that makes a Cobblemon-only world fully farmable through pastures — without turning
the pasture into a vending machine. Two tiers: **type-drops** (cheap, thematic staples) and **rituals**
(compose a pasture → bank gacha pulls → roll rare/boss loot with visible odds + pity). Everything surfaces
through the **Harvester** (ground-free, materials only, never Data). Numbers below are **calibration —
edit freely**; the shapes are what we're pinning._

> **Scope.** In a Cobblemon-only survival world the pasture farm should be a viable source for the valuable,
> grindy, or mob-gated stuff. Common staples = type-drops; rare/boss/loot-only items = rituals.

---

## Tier 1 — Type-Drops
**A tethered mon's TYPE adds bonus items to its Harvester roll.** Rides the Harvester proc we already built
(3%/mon/min base + Kernel/tether drop-rate). No setup — just have the type in the pasture. (Items marked
✅ *already drop somewhere* — listed so a themed source makes them a reliable farm, not a lucky one.)

| Type | Adds to drops | Notes |
|---|---|---|
| ❄️ Ice | `ice`, `packed_ice`, `snowball`✅ | the headline ice gap |
| 🔥 Fire | `blaze_rod`, `blaze_powder`, `magma_cream`✅ | unlocks brewing + nether progression |
| 👻 Ghost | `ghast_tear`, `soul_sand`, `soul_soil` | spooky reagents |
| 🪨 Rock / Ground | `quartz`, `iron_ore`/`gold_ore`/`diamond_ore` (the *blocks*), `flint`✅ | mining-on-legs |
| 🔮 Psychic | `lapis_lazuli`, `amethyst_shard`✅, `glowstone_dust` | unlocks enchanting |
| 🧚 Fairy | `glistering_melon_slice`, `glow_berries`, `lapis_lazuli` | "sparkly" goods |
| 🌱 Grass | `sugar_cane`, `beetroot`, `wheat`, assorted `*_log` (rotating) | the wood/paper gap |
| 💧 Water | `sugar_cane`, `prismarine_shard`✅, `kelp`✅ | waterside growth |
| ⚙️ Steel | `raw_iron`✅, `raw_gold`, `iron_ingot`✅, `netherite_scrap`? | reliable metal (scrap better as a ritual) |
| 🐛 Bug | `string`✅, `cobweb`, `honeycomb`✅ | |
| ☠️ Poison | `nether_wart`, `spider_eye`✅, `fermented_spider_eye` | brewing reagents |
| 🐉 Dragon | `dragon_breath`, `phantom_membrane`✅ | (the egg is a ritual) |

**Knobs:** per-type drop %, qty ranges, and whether a type rolls one fixed item or picks from its list each
proc. Dual-typed mons get both lists. Defaults TBD — we'll sim them like the staple rates.

---

## Tier 2 — Rituals (the gacha)
The rare / boss / loot-only tier. You don't *drop* a `dragon_egg` — you **engineer a pasture** that satisfies
a ritual, **bank pulls** while the composition holds, and **gamble** them at displayed odds with a pity
safety net.

### The loop
1. **Compose.** Fill a pasture so it satisfies a ritual's requirement (a set of **types**, optional **min
   counts**, and for the rarest, a **signature species**).
2. **Bank pulls.** Each Harvester tick, per mon, the ritual rolls a pull-proc =
   `(Harvester base 3% + Drop Rate augments/tethers) / rarityFactor` — **3× rarer than a typical staple drop**
   by default (`rarityFactor`, config). Each proc banks `1 + Drop Yield` pulls. So **Drop Rate speeds the
   gacha · Drop Yield fattens each proc · a fuller pasture pulls faster** — the same drop augments/tethers that
   boost staples feed the rituals. Pulls (+ pity) persist per-Harvester (NBT).
3. **Pull.** Open the Harvester's **Ritual** tab → see each active ritual's **% per pull**, your **banked
   pulls**, and **pity progress (x/N)**. Hit **PULL ×1 / PULL ALL** to spend.
4. **Roll.** Each pull rolls the base %; **hit → the item drops into the Harvester chest** + pity resets.
   **Miss → pity++**. At **hard pity N**, the next pull is **guaranteed**. (Optional **soft pity**: odds ramp
   from pull M, Genshin-style.)

> Pity is the point: a *careful* composition is never ruined by variance — worst case you wait to N.
> An idle **auto-pull** toggle is an optional knob (spend pulls as they bank, log results) for farm-and-forget.

### Roster (DRAFT — edit the recipes, odds, signatures)
| Ritual | Requirement (types · counts · signature species) | Output | Base %/pull | Hard pity |
|---|---|---|---|---|
| 🌋 **Nether Forge** | Fire + Dark + Ghost (≥1 each) | `netherite_scrap` | 5% | 30 |
| 🍎 **Forbidden Orchard** | ≥5 **distinct** types in the pasture | `enchanted_golden_apple` | 3% | 40 |
| 🛡️ **Last Stand** | **Sableye** (signature) + Fairy + Ghost | `totem_of_undying` | 4% | 35 |
| 🪽 **Endless Sky** | Flying + Dragon + Ghost | `elytra` | 2% | 50 |
| 🔱 **Tide Caller** | Water + Ice · signature: a water legendary (Kyogre/Suicune/Lugia?) | `trident` | 5% | 30 |
| ⭐ **Soul Convergence** | ≥3 Ghost + ≥1 Dark · signature: a spooky legendary (Darkrai/Giratina?) | `nether_star` | 1.5% | 60 |
| 🐉 **Dragon's Hoard** | ≥2 Dragon + ≥1 Flying · signature: a dragon legendary (Rayquaza/Dialga?) | `dragon_egg` | 1% | 80 |

**Signature species** = the gate that keeps the absolute rarest behind real effort (you must *own* the
legendary, not just any Dragon-type). Pick the Cobblemon mons you like — Sableye→totem is locked because it
literally looks like one. 😅

**Ideas parked for later:** `beacon` (full legendary roster?), `heart_of_the_sea`✅ already drops,
`music_disc_*` (a "Player" ritual), seasonal/anomaly rituals tying into the dark-economy easter eggs.
**Enchanted Golden Apple recipe (Deuce, 2026-06-29):** a **legendary Grass** + a **legendary Fairy** + a
**gold-dropping** mon — replace/augment the current "5 distinct types" Forbidden Orchard gate. (Recipes are
deferred for fine-tuning — all just config rows, no code.)

---

## How it's wired (when we build)
**Logic-first, headless-testable now; the gacha GUI is the one custom-UI piece (deferred while RC — or a
chat/`GpLog` readout in the interim).**

- **Composition read** (MC adapter): iterate the pasture's tethered mons → collect the set of **types**
  (Cobblemon `Species.getPrimaryType()/getSecondaryType()` → `ElementalType`; *to verify*) + **species** +
  per-type counts. One snapshot per Harvester tick.
- **`RitualBook`** (pure core, tested): the roster as data; `match(composition) → satisfied rituals`.
- **`Gacha`** (pure core, tested): per-ritual `{ bankedPulls, pity }`; `pull(state, rng, baseChance, pityN)
  → (hit?, newState)` with hard-pity force + optional soft-pity ramp. Deterministic under a seeded rng →
  unit-tested odds + pity guarantees.
- **`RitualState`** (persisted per-pasture, in `PastureData`): banked pulls + pity per ritual id.
- **Harvester tick**: snapshot composition → bank pulls for satisfied rituals → (auto-pull if enabled).
- **GUI**: a Ritual tab on the Harvester screen — active rituals, %/pull, banked pulls, pity x/N, PULL
  buttons → C2S packet → server rolls via `Gacha` → deposits on hit.
- **Sim**: extend `sim_drops.py` with a ritual mode — given a composition + rates, print expected
  pulls/hr and items/hr (with pity) so we tune odds without grinding.

## Open knobs (your calls)
- **`rarityFactor`** (default 3 — how many × rarer ritual pulls accrue vs a staple drop) + whether to also let
  Drop Rate/Yield scale the type-drops (currently rituals only).
- **Base % + hard-pity N** per ritual (table above) · whether to add **soft pity**.
- **Manual pull vs auto-pull** (gacha dopamine vs idle farm) — propose both, default manual.
- **Signature species** picks for the legendary-gated rituals.
- **Type-drop rates/qty** (Tier 1 table).
- Do rituals consume anything (pure time, or also Data/an item cost per pull)?

## Status / build order
1. ✅ Doc pinned (recipes still tunable via config — deferred for fine-tuning).
2. ✅ Pure cores — `Gacha` (pull/pity), `Requirement`/`Composition`/`RitualBook`, `TypeDropTable` + tests (148 green).
3. ✅ Composition-read adapter (`drops/CompositionReader`, Cobblemon `getTypes()`/`getShowdownId()` verified) +
   Harvester pull-banking + per-ritual pity persistence (Harvester NBT, survives relog).
4. ✅ Tier-1 type-drops (config `TypeDropTable`, rolled per-mon each Harvester tick).
5. ✅ Config — `config/greenerpastures/rituals.json` (`RitualConfig`/`RitualSystem`): master + per-tier +
   per-ritual toggles; re-map which types/species → which items, counts, odds, pity. **Auto-pull** is the
   interim default (rolls banked pulls each tick so it's playable now); set `autoPull:false` for manual.
6. ✅ Sim — `cobblemon-drops-ref/sim_rituals.py`: a composition + augment loadout → **pulls/h + items/h per
   ritual** (with pity), plus which rituals are inactive and why. Reads the built-in default roster or a
   game-written `--config rituals.json`. Tune odds/pity/rarity headlessly. (e.g. base Nether Forge ≈ 1
   `netherite_scrap`/1.6h; Drop Rate 1% + Yield 1 → 1/0.6h.)
7. ⏳ Manual-pull gacha **GUI** on the Harvester (live %/pity + PULL buttons) — when at PC.

---

# Batch 2 — Pasture Ritual Spec (Deuce design session, 2026-07-05 — ACCEPTED + SHIPPED)

14 hinted hidden rituals (hints show as 🔒 locked teaser cards; recipe reveals on discovery).
Tier grid @1 pull/min: LOW 5%/15/30 · MID 2.5%/40/80 · HIGH 1.2%/80/160 · APEX 0.4-0.5%/150-300.

| id | output | mons | tier | hint |
|---|---|---|---|---|
| nether_star | Nether Star | 1 Hydreigon, 3 Marowak, 4 Yamask | APEX 0.4% | "Three heads. Three skulls. The restless dead." |
| wither_skull | Wither Skeleton Skull | 3 Duskull, 4 Houndoom, 1 Marowak | MID | "The hounds guard what the dead wear." |
| elytra | Elytra | 1 Ninjask, 1 Shedinja, 6× any-mix Heracross/Pinsir (GroupCount gate) | HIGH | "What the swift one leaves behind, the sky claims." |
| totem_of_undying | Totem | 4 Sableye, 1 Blissey | HIGH | "The gem-eyed ones guard the egg of second chances." |
| shulker_shell | 2× Shulker Shell | 8 Shuckle | MID | "Say it out loud." |
| trident | Trident | 1 Dhelmise, 2 Jellicent, 4 Frillish | HIGH | "Where sailors drown, their weapons linger." |
| heart_of_the_sea | Heart of the Sea | 8 Luvdisc, 2 Clamperl, 1 Lapras | HIGH | "The gentle sea keeps its heart hidden among lovers and pearls." |
| echo_chorus | 2× Echo Shard | 2 Noibat, 2 Zubat, 2 Loudred, 1 Exploud, 1 Kricketune | MID | "What screams in the dark leaves pieces behind." |
| saddle | Saddle | 1 Mudsdale, 2 Zebstrika, 4 Ponyta | LOW | "The herd provides for its rider." |
| name_tag | Name Tag | 1 Smeargle, 4 Chatot | LOW | "The painter takes commissions." |
| slime_court | 4× Slime Ball | 4 Goomy, 2 Shellos, 1 Ditto, 1 Goodra | MID 3.5% | "The dragon of slime holds court." |
| ominous_bottle | Ominous Bottle | 4 Absol, 1 Honchkrow, 2 ALOLAN Meowth, 1 ALOLAN Persian, 2 GALARIAN Zigzagoon (form-gated) | MID | "Misfortune, bottled at the source." |
| pasture_band | random Music Disc (18-disc outputPool, per-hit roll) | 1 Chatot, 4 Kricketune, 4 Chingling | MID | "The pasture band takes requests." |
| fossil_communion | Sniffer Egg | all 11 base fossils (Oma/Kabu/Aero/Lileep/Anorith/Crani/Shieldon/Tirtouga/Archen/Tyrunt/Amaura), span 2 pastures | APEX 0.5% | "The ancients recognize their own." |

Engine additions this batch: `Requirement.GroupCount` (N total across any-of species) · form-qualified
composition keys (`meowth:alolan` — reader emits them alongside base species) · `Ritual.hint` (locked
teaser cards) · `Ritual.outputPool` (per-hit random output). Rejected list per spec (dragon egg parked,
ghast tear/ender pearl/etc. already covered elsewhere). Pre-batch rituals untouched (Kartana feast —
NB spec said "Tropius", shipped config says Kartana, left as shipped).
