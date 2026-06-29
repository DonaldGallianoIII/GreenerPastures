# 🔭 Design vs. Build — Gap Analysis (2026-06-28)

_Maps `~/pokemonthink` (design) against what's actually in `~/pokemon-prediction` (code). Read alongside `PICKUP_HERE.md`. Legend: ✅ built · 🟢 logic built, needs MC wiring · 🟡 partial/misnamed · 🔴 not started · ⏸️ gated on a decision._

## ⚠️ Three things that need YOUR call before building
1. **Lexicon shift.** The design **renamed things out from under the code.** What we built in-game as the **"Daemon" node-graph** is now the design's **"Notebook"** (the *editor*: graph + plots + local withdraw; it also absorbs the Dashboard). The design's **"Daemon"** is a *brand-new runtime item* — a player-bound **Data account** + global buffs. → a rename + a new item.
2. **The BioBank isn't in the design.** The design stores keepers via a **24-egg cap + local withdraw at the pasture (Notebook)** and **renders the rest to Data** — i.e. "don't hoard thousands of eggs." Our BioBank (AE2 store-thousands-by-species) is a code-side invention. It can live on as a *central keeper warehouse*, but its role needs deciding.
3. **Soul Tether is the one true blocker.** The design has it only **PROPOSED** (per-function · tiers I/II/III · Compiler-inscribed) with an **open knob you parked: re-inscribable vs one-shot** + exact magnitudes. Everything else can proceed; this can't until you call it.

## 🗣️ Deuce's calls — LOCKED 2026-06-28
- **Data = the one currency.** Comes only from rendered eggs. **"Egg sacrifice" = voiding/rendering eggs → Data.** Soul Tethers cost **Data** (the same pool the Daemon drains) — no literal-egg crafting cost.
- **Renderer** = the egg-eater block next to the pasture (culls/"void" → Data). **"Void" is removed from the visual scripting** — a culled egg must route to the Renderer (no free destruction). Name stays **Renderer**.
- **BioBank stays** — but it's "a REALLY fancy chest": a **storage terminal** you feed via **hoppers** and open to browse/withdraw your keepers (+ items). It's the *keep* side, NOT part of the Data loop. (Matches BioBank Batch 1 + the planned hopper auto-ingest.)
- **Daemon item** = held runtime: boosts vanilla (global buffs), holds the Data account, and is the tool that **crafts Soul Tethers** (Data cost).
- **Visual scripting** = add/remove nodes that shape the egg (augments) + filter it (keep vs render).
- **Still open:** Soul Tether magnitudes + re-inscribable-vs-one-shot; the global-buff v1 list.

## By system
| Design element | Status | Notes |
|---|---|---|
| **Kernel** (pasture upgrade: pairs + slots + base mods) | ✅ | `breeding_upgrade_*` + `BreedingTier` (8-pair cap). Slots are dormant until Soul Tethers exist. |
| **Augment-on-Kernel** (base mods as data) | ✅ catalog | `augments` component now holds the full `{function→level}` catalog (back-compat). **Shiny + Speed** have live effects; Enrichment/IV/EV/Drops are data-modeled, effects pending. |
| **Compiler** (writes mods) | ✅ / 🔴 | Writes the Shiny augment. Design also wants it to **inscribe Soul Tethers** — not built. |
| **Multi-pair breeding** | ✅ | `MultiPairBreeder`. |
| **Shiny proc** (bounded reroll) | ✅ tested | `ShinyOdds`. |
| **24-egg FIFO** | 🟢 | `EggQueue` + breeder adapter (not deployed → QA Q1). |
| **Offline catch-up** | ❌ cut | Was `CatchUp` math (never wired); **removed 2026-06-28** (Deuce's call) — pastures only breed in loaded chunks. |
| **Notebook** (editor: node-graph + plots + local withdraw) | 🟡 misnamed | Built as the **"Daemon" screen** + wand + Arrange board. Needs the rename + Dashboard fold-in + local withdraw. |
| **Dashboard** (dark telemetry plots) | 🟢 | `DashboardStats/Export/Reader`. Design wants it as a **Notebook tab**: sources-vs-sinks, net Data/hr, body count, shiny curve. |
| **Renderer** (cull eggs in-step → Data, never materialized) | 🔴 block / 🟢 brain | **Block not built.** Its keep-filter LOGIC is built (`RenderSelection`/`ValueRule`/`IvFilter`/`RenderLedger`). The centerpiece. |
| **Data** (currency: player account + counter) | 🔴 | No Data accounting yet. |
| **Daemon** (NEW runtime item: Data account + buffs) | 🔴 | Distinct from our "Daemon" screen (= the Notebook). |
| **Soul Tether** (amplifier; slots; burns Data) | 🟢 wired | Logic fully tested (per-function amplify · burn-by-class · fed/starved · inscribe/wipe); item + `[function,tier]` component built; breeder applies effective shiny/speed + drains Data. **Operator = an explicit locked-boolean claim** (`PastureClaim` + `ClaimOperatorPayload`, tested — who pays the cost; shared group pastures): click to claim/lock, only you release. **Remaining = the claim box UI (sends the packet) + Compiler inscription GUI + more base-augment items + tether-amplified Enrichment** (base Enrichment is wired into the Renderer; the tether-amp version with its own drain is pending — different clocks). Magnitudes still calibration (`BASE_DATA_PER_EGG=2`). |
| **Global "root" buffs** (enchant +1/+2/+3, auto-smelt, vein-mine, magnet, haste, XP, potion-dur, saturation) | 🔴 | Enchant include/exclude list is SETTLED in design; nothing built. Worker-not-fighter, config-gated. |
| **Loot block** (%-chance of what WOULD have dropped → straight into a container) | 🔴 | NEW design (Deuce, 2026-06-28) — **replaces the cut pasture loot-sweep**. Our own drop table + roll, yields modulated by augments/tethers, species-combo easter eggs (e.g. Groudon+Darkrai+Ditto → a joke drop). Full control = the drops arm of the dark economy. Materials only, never Data. |
| **Data grid** (fuel/trophy/material economy) | 🔴 | Emerges from Renderer keep-filters + tethers + Data. |
| **BioBank** (AE2 egg storage) | ✅ / ❓ | Built (Batch 1), **off-spec** — reconcile role. |
| **Music player** (discs + Notebook "Player" tab + ambient) | 🔴 | Deuce supplies tracks. |
| **Easter eggs** (🌲 RCF anomaly, daemon stderr, body-count milestones, ⚠ overfitting, name-triggers) | 🔴 hooks | Cheap; publish-phase. |
| **Awareness book** (#18) | 🔴 | |
| **Prestige** (Greener currency) | 🔴 later | |
| **More augments** (IV Floor, Fine-Tune/EV, Enrichment, Drop Rate, Drop Yield = v1; Nature/Egg-Move/Ability/Gender/Hatch-Speed/Clutch/Anomaly = backlog) | 🔴 | Only Shiny exists. |

## What CAN be built next — and how
**Logic-first, now, test-backed (no game, no decisions needed):**
- **Data accounting** core — balance, income/drain, the one balance constant (egg→Data value vs tether burn). Pure + testable.
- **Renderer valuation** — eggs + keep-filter → kept set + cull count → Data amount (Enrichment-aware). Mostly `RenderSelection` already.
- **Grid telemetry** — net Data/hr, sources vs sinks, time-to-empty, body count. Extends `DashboardStats`.

**Gated on your decision:** Soul Tether (knob + magnitudes).

**MC adapters / blocks (need the game → QA rows):** Renderer block · Daemon item · drops · enchant buffs · music · the Notebook rename/unification.

## The design's own proposed build order (STATE_AND_PLAN)
1. Renderer block → 2. Data account + Daemon item → 3. Soul Tether (start w/ Shiny) → 4. **min-slice test** (Renderer keep-shinies + 1 Shiny tether + Daemon counter = prove *feed-to-amplify*) → 5. then augments, drops, grid, Dashboard plots, enchant buff, music, easter eggs, prestige. _(offline catch-up was step 4 in the original spec — **cut** 2026-06-28.)_
