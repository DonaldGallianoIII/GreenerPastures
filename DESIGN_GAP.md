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
| **Augment-on-Kernel** (base mods as data) | ✅ catalog | `augments` component now holds the full `{function→level}` catalog (back-compat). **Shiny, Speed, Enrichment, Drop Rate, Drop Yield** have live effects (base + tether-amplified, each drained by its own consumer); **IV Floor / EV** are data-modeled, effects pending. |
| **Compiler** (writes mods) | ✅ / 🔴 | Writes the Shiny augment. Design also wants it to **inscribe Soul Tethers** — not built. |
| **Multi-pair breeding** | ✅ | `MultiPairBreeder`. |
| **Shiny proc** (bounded reroll) | ✅ tested | `ShinyOdds`. |
| **24-egg FIFO** | 🟢 | `EggQueue` + breeder adapter (not deployed → QA Q1). |
| **Offline catch-up** | ❌ cut | Was `CatchUp` math (never wired); **removed 2026-06-28** (Deuce's call) — pastures only breed in loaded chunks. |
| **Notebook** (editor: node-graph + plots + local withdraw) | 🟡 misnamed | Built as the **"Daemon" screen** + wand + Arrange board. Needs the rename + Dashboard fold-in + local withdraw. |
| **Dashboard** (dark telemetry plots) | 🟢 | `DashboardStats/Export/Reader`. Design wants it as a **Notebook tab**: sources-vs-sinks, net Data/hr, body count, shiny curve. |
| **Renderer** (cull eggs in-step → Data, never materialized) | ✅ built | `RendererBlock`/`RendererBlockEntity`: culls non-keeper eggs in-step → Data via `RenderValuation`, **SACRED shiny guarded 4 ways**, base **+ tether-amplified Enrichment** multiplier (drains the operator per render). Keep-filter brain: `RenderSelection`/`ValueRule`/`IvFilter`/`RenderLedger`. The centerpiece. |
| **Data** (currency: player account + counter) | ✅ | `DataStore` + `DataAccount` — per-player balance, persisted. Credited by the Renderer; drained by tether burn across all three consumers (breeder/Harvester/Renderer). |
| **Daemon** (NEW runtime item: Data account + buffs) | ✅ / 🟡 | `DaemonItem` shows live balance (right-click). Global "root" buffs still 🔴. Distinct from our "Daemon" screen (= the Notebook). |
| **Soul Tether** (amplifier; slots; burns Data) | 🟢 wired | Logic fully tested (per-function amplify · burn-by-class · fed/starved · inscribe/wipe); item + `[function,tier]` component built. **Per-consumer drain split — COMPLETE** (`TetherRuntime.resolveFor` + disjoint function sets — each consumer bills its OWN tethers on its OWN clock, so a tether is charged exactly once): **breeder** drains `shiny/speed` (breeding cycle), **Harvester** drains `drop_rate/drop_yield` (1-min clock), **Renderer** drains `enrichment` (1-sec cull clock, billed to the operator — the render reward still credits the Renderer's own owner). All three consumers amplify + drain their own functions; `IV/EV` join the breeder set when their effect ships. **Operator = an explicit locked-boolean claim** (`PastureClaim` + `ClaimOperatorPayload`, tested — who pays the cost; shared group pastures): click to claim/lock, only you release. **Remaining = the claim box UI (sends the packet) + Compiler inscription GUI + more base-augment items.** Magnitudes still calibration (`BASE_DATA_PER_EGG=2`). |
| **Global "root" buffs** (enchant +1/+2/+3, auto-smelt, vein-mine, magnet, haste, XP, potion-dur, saturation) | 🔴 | Enchant include/exclude list is SETTLED in design; nothing built. Worker-not-fighter, config-gated. |
| **Harvester** (passive drops → its own chest, never on the ground) | 🟢 built | The **Harvester block** (`drops/`): rolls each tethered mon's Cobblemon drop table (`DropsBridge`) via our tested `DropTable`/`DropEntry`, deposits into a vanilla 9×3 container, pause-when-full, scatter-on-break. Roll model: **3%/mon/IRL-min proc (LEVER 1) → Cobblemon's faithful `getDrops` (LEVER 2)**. Every **Kernel ships a base +0.25% drop rate** (LEVER 1, visible on the tooltip, read by the Harvester, tether-amplifiable). **Drop Yield** (LEVER 2) is built too: `EffectiveAugments.dropYieldBonus()` widens Cobblemon's `amount` budget ceiling → richer events (fattens the rarer *secondary* drops, lead drop stays flat; base × tether). **Tether-amplified** drop rate/yield is wired too: the Harvester reads the pasture's slotted Drop Rate/Yield tethers on its own 1-min clock and drains the operator's Data per harvest event (`TetherRuntime.resolveFor` with `{DROP_RATE,DROP_YIELD}`; starved → free base, never a pause). Tune headlessly with **`cobblemon-drops-ref/sim_drops.py`** (`--droprate` / `--yield`). Replaces the cut loot-sweep. **Remaining = species-combo easter eggs + craftable yield/rate augment items.** Materials only — drops never feed Data (they only ever cost it, via the tether burn). |
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
