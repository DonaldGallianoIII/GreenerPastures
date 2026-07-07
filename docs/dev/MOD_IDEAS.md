# Mod Suite — Catalog & Improvement List
_Built 2026-06-23. A talking list: every mod/tool, what it does **today** (verified against source, not status docs), and proposed plans under each. Tick boxes as you decide._

**Tags:** `[improve]` refine existing · `[add]` new feature on existing mod · `[new]` whole new mod · `[decision]` a fork to choose · `[blocked:…]` waiting on something · `[release]` ship chore · `[CB]` colorblind-accessibility · effort `(S/M/L)`.

**The one load-bearing insight:** a chunk of the wishlist is **one engine** — a walk-by `interactBlock` loop — with different targets. Build `WalkByPrinter` once (§D0); FarmHand's **Till+Plant** & **Reap**, plus EggHarvester / SnackStacker / PastureSmith / ApricornArborist, are then thin configs. I have the exact Litematica Easy-Place flow decompiled to crib from.

> **⭐ Decided 2026-06-23:** the water mod (HydroGrid) becomes **FarmHand** — the all-in-one farming suite (Water + Till/Plant + Reap + Plan) on the shared `Field` + WalkByPrinter. CropPlacer & AutoReaper fold in as FarmHand capabilities; apricorns stay a separate mod. See §A-FarmHand and §D.

---

## A. Shipped client mods

### 🔭 ShedScope · `shedscope` v0.1.0 — *shipped-working*
**Does today:** Client ore/loot ESP. In-world boxes + tracer to nearest target; top-left HUD lists 12 nearest with live distance; scans loaded chunks in an 80-block radius every 8 ticks. 11 ore types + 5 loot/spawner types + 3 deep-dark markers, color-coded. Keys: `\` whole mod · `[` ores · `]` loot. No config, no persistence (runtime toggles only).
**Proposed:**
- [ ] `[add]` **Cobblemon entity ESP** — box/track a target *species* or any *shiny* mon in-world. The single highest-value add for a shiny hunter. (M)
- [ ] `[add]` Apricorn-tree / berry / specific-block finder mode — feeds the survival ball+snack grind. (S)
- [ ] `[add]` Per-box depth/Y callout ("diamond · y-58 · 23m") and vein-grouping ("vein ×7"). (S)
- [ ] `[add]` Sound ping when a high-value target (diamond / shiny) enters range. (S)
- [ ] `[improve]` Config screen + persistence — toggle individual targets, scan radius, scan rate, HUD length (all hardcoded now). (M)
- [ ] `[CB]` Shape/letter glyph on boxes, not just hue — you can't lean on color. (S)

### ⭐ Shiny Egg Highlighter · `shinyegghighlighter` v1.0.0 — *shipped-working*
**Does today:** Overlays eggs in any open container (vanilla / Sophisticated via mixin) — gold fill+border for shiny, dim for non-shiny. Detection = Cobbreeding tooltip ★ with ID/NBT fallbacks, cached per stack. In-GUI `G` counts eggs into a lifetime tally + prints empirical shiny rate; `Shift+G` resets; `\` dumps held-egg debug. Stats persist to `config/shinyegghighlighter_stats.json`.
**Proposed:**
- [ ] `[add]` **ShinyKlaxon** — hatch-moment / shiny-into-inventory alert: flash + chime + auto-log. (M) `[CB]` aura/badge, not hue.
- [ ] `[decision]` **Overlap with EggOracle's Egg Culler** — both overlay eggs in containers (this = shiny gold; culler = shiny/keeper/cull by IV). Merge into one overlay, or keep highlighter as the lightweight shiny-only version? (decision)
- [ ] `[add]` Export the `G`-counter shiny-rate dataset → feed the Python model's ML-calibration layer (§B-Py). Closes a real loop. (S)
- [ ] `[add]` Chest-title shiny counter · "jump to next shiny" auto-scroll in big banks (old backlog). (S)

### 🥚 EggOracle · `eggoracle` v0.1.0 — *shipped-working* — **the flagship (6 tools)**
**Does today:**
1. **Odds Calculator** (`=`) — shiny odds from server config + setup: per-egg 1/X, eggs/hr, shinies/day, hours-to-shiny, P(shiny by window), progress bar.
2. **Preset cycling** — Cobbreeding vs Custom shiny-rate presets.
3. **Egg Culler** (`C`, in container) — color boxes per egg: gold=shiny, light-blue=keeper, red=cull (keeper = IV-total ≥120 **or** ≥3 perfect stats), IV totals in slot corners + summary.
4. **Farm Dashboard** (`+`) — editable pasture roster → per-species shiny %, shinies/day; farmwide totals + time-to-shiny.
5. **Pasture Finder** (`K`) — click a species → box every matching mon in-world (count + nearest).
6. **Pasture Fill Check** (`J`) — boxes pastures with <2 tethered mons.

Persists roster to `config/eggoracle_farm.txt`. **Not** persisted: calculator inputs, cull toggles, IV thresholds (all in-session). `EggReader` already does reflection-based Cobbreeding property extraction.
**Proposed:**
- [ ] `[blocked:J-test]` **Resolve the `J` pasture-sync test** — does tethering sync to the client? This is the **gate for the entire "PasturePilot / WMS" direction**. Unblock or kill it before speccing anything downstream. **← do this first.** (S to test)
- [ ] `[new→ if J passes]` **PasturePilot** — live pasture map, "where's my Ditto" search, sort by what's cooking, rename, roster-vs-world census, Ditto-needed column, collection routes, slotting/Masuda-OT audit. (L)
- [ ] `[add]` **DittoMatch** — extend `EggReader` to read pasture *parents* → recommend which Ditto to slot for best IV/nature/Masuda egg. The brain over the farm. (M)
- [ ] `[add]` **VarianceOracle** tab — "you are X σ unlucky," pity counter, EV-countdown to next expected shiny; fed by the highlighter's live shiny-rate stats. (M)
- [ ] `[improve]` Persist calculator inputs + cull toggles + **configurable / per-stat IV thresholds** (hardcoded 120 / 3 now) — nature/ability/gender filters too. (M)
- [ ] `[add]` `[decision]` Auto-cull **action** (move/toss flagged eggs) — now unblocked by the dev greenlight; pairs with the WalkByPrinter container path. (M)

### 🚜 FarmHand · `farmhand` (was `hydrogrid` v0.1.0) — *water base shipped; expanding into the farming suite* ⭐ DECIDED 2026-06-23
The all-in-one farming mod — **HydroGrid grown up.** One shared `Field` model (stamp/type two corners → solved cell grid) drives every capability: water, planting, and harvesting all iterate the *same* grid — same grid, three verbs. Automation runs on the `WalkByPrinter` engine (§D0). Apricorns are explicitly **out** (separate mod — see ApricornArborist, §D1).
**Does today (the Water base, shipped):** `H` master toggle. Held water bucket → 9×9 footprint + next-8 seamless ghosts. `V` stamps two field corners, `N` types XYZ. Field-fill solver = minimum evenly-spaced water grid (SPACING=9, zero gap/overlap), amber pillars → green as filled. Nearby-water audit (~14×±4, cap 48). No persistence; flat-plane only.
**The four capabilities (build target):**
- [ ] 💧 **Water** *(shipped)* — refine: persist + name multiple field plans (lost on reload now) (S); terraced/multi-level fill (flat-plane only today) (L).
- [ ] 🌱 **Till + Plant** — GUI (crop · rows×length · till on/off · printer/place-now); walk-by auto-till then seed the solved grid. Runs on `WalkByPrinter`. (M, needs D0)
- [ ] 🌾 **Reap** — walk-by harvest + replant mature cells in the grid; "treadmill" mode. Runs on `WalkByPrinter`. (M, needs D0)
- [ ] 🗺️ **Plan overlay** — dry/wet + planted/empty heatmap with `[CB]` ✗/✓ symbols + counter (folds the old HydroHeatmap idea in). (M)
**Suite chores:**
- [ ] `[improve]` Rename folder/id `hydrogrid` → `farmhand`; keep the shipped water features intact through the rename. (S)
- [ ] `[decision]` Mode UX — one keybind cycle vs a unified GUI to pick Water / Till+Plant / Reap. (S)

### 🏗️ PokeSnack Planner · `pokesnackplanner` v0.1.0 — *shipped-working*
**Does today:** `P` locks a snack-column anchor on the crosshair block; `L` sets the platform plane Y. Renders 4 overlays: snack column (cyan), mine-to-bedrock home/activation block at (anchor x-8, z-8) (orange), 17×17 spawn platform (green), and the ±64 vertical coverage band. Matches Cobblemon's `FixedAreaSpawner` math exactly. In-memory only; tower height hardcoded 128; no config.
**Proposed:**
- [ ] `[add]` **SnackStacker** — turn the overlay into a **placer**: auto-build the snack column + platform via WalkByPrinter. Planner shows *where*; placer builds it. (M, after D0)
- [ ] `[add]` Export layout → `.litematic` (round-trips with `litematica_inspect.py` + the tower schematics). (M)
- [ ] `[add]` Live spawn-candidate readout — marry to the Python engine: "with X snack here, expect Y Ditto/hr." (M)
- [ ] `[improve]` Persist anchor/platform per world · configurable tower height · multi-platform stacking guide. (S)
- [ ] `[improve]` Home-column validation — warn if not mined to bedrock / activation block missing (it already scans solid Ys). (S)

---

## B. Server mod · Python · tools · resource pack

### 📦 Shiny Egg Collector · `shinyeggcollector` v1.0.0 — *shipped-working, but server-side*
**Does today:** Server-side block entity (27 slots). Every 1s scans a 6-block cube for any container (vanilla + Sophisticated via Fabric Transfer API) and pulls shiny eggs in via `StorageUtil.move`. Click to open; crafted gold-frame + chest + hopper. Drops contents if broken.
**Proposed:**
- [ ] `[decision]` **It can't run on Shedmon (server-side).** Pick its fate: (a) keep as a singleplayer/admin-server tool and publish it for that audience, **or** (b) supersede with **EggHarvester** (client walk-by, §D) for your actual server. (decision)
- [ ] `[add]` IV/shiny filter (shiny-only now) via the `extractProperties` API — pull keepers too. (S)
- [ ] `[improve]` Custom texture (gold-block placeholder) · configurable radius/filter · hopper-output for sorting plumbing. (S)

### 🐍 PokeSnack Engine · `pokesnack/` (Python) — *validated, production-ready*
**Does today:** Pure-Python, no deps. Models Cobblemon's two-stage spawn algo + Bait-Seasoning recipes. CLI (`biomes`, `outlook`, `run`, `versus`, `optimize`) + library. Validated against hand-computed + real-catch numbers (±3–5%). Modules: gamedata/pool/engine/analytics/optimize/model/cli.
**Proposed:**
- [ ] `[improve]` **Fill the common bucket** — only 7/40 species captured, so common-Normal shiny math is a *lower bound*. Capture in-game → make it exact. (M)
- [ ] `[add]` **Quantitative cost model** (gold↔time rate) → unlocks the optimizer's "is it worth it?" objective (DESIGN.md open item). (M)
- [ ] `[add]` **EV-yield species tags** → activate the already-wired EV-filter seasonings. (M)
- [ ] `[add]` **ML-calibration layer** (DESIGN.md phase 2) — fit effective λ + caps from logged spawns. Direct Kaggle/portfolio prep. (L)
- [ ] `[add]` **In-game bridge → SpawnScope** — emit results as JSON a HUD mod reads, or reimplement the core in Java. (L)
- [ ] `[add]` **Capture pipeline** — a mod that logs real spawns → feeds `spawn_pool` data, killing manual capture. (M)

### 🧰 tools/
**Does today:** `litematica_inspect.py` (read-only `.litematic` NBT decoder — metadata, block tallies, coords); `make_colorblind_ore_pack.py` (builds the ore pack, recolors only mineral speckles); `shiny_pasture_calculator.html` + `shiny_service_planner.html` (standalone breeding/order-ETA calculators).
**Proposed:**
- [ ] `[add]` Grow `litematica_inspect.py` into a **writer** (spec → `.litematic`) — enables SnackStacker + layout export. (M)
- [ ] `[add]` Port the two HTML calculators **in-game** into EggOracle (VarianceOracle + a **ServiceBoard** order-queue HUD), so they're not separate browser tabs. (M)

### 🎨 colorblind-ore-pack — *complete, MC 1.21.1, zip staged*
**Does today:** Recolors iron→blue, copper→orange (surface + deepslate + optifine emissive). `ColorblindOres.zip` ready at repo root.
**Proposed:**
- [ ] `[release]` **Ship it** — it's done. Lowest-effort win on the board (Modrinth/CF). (S)
- [ ] `[add]` Extend to other confusables: redstone, gold, emerald, lapis, ancient debris, nether gold/quartz, raw-ore blocks. (M)
- [ ] `[CB]` Promote the "symbol not hue" rule into a shared **`colorblind-ui` lib** used across ShedScope/HydroGrid/EggOracle (§E). (M)

---

## C. Research projects (not yet mods)

### 🗼 pokesnack-tower/ — *research done, awaiting in-game validation*
Decompiled Cobblemon spawn mechanics + 5 Litematica schematics + `analyze.py` (column/apparatus/home-offset validator). **Next:** deploy a tower in-game and verify it actually lifts catch rates; optionally a spawn-cap sim. **Build target:** this is what **SnackStacker** (§A-Planner / §D) places.

### 🚰 egg-plumbing/ — *research done; client culler already shipped in EggOracle*
Confirmed: egg IV/shiny/nature/gender/ability/form/species are baked at breed time and readable via `EggUtilities.extractProperties` (Cobbreeding 2.2.1) — no NBT/decryption. Option A (client culler) shipped in EggOracle. **Open:** server-side filter block (deferred); per-stat IV filters + config UI (→ EggOracle improve); auto-disposal policy (now greenlit). This research backs **DittoMatch** + the EggOracle IV-filter work.

---

## D. Net-new mods — the WalkByPrinter family + readers

### D0. 🖨️ WalkByPrinter (engine) — `[new]` **build first; powers FarmHand + every placer** (L)
Reusable core cribbed from the decompiled Litematica Easy-Place flow: tick loop → resolve in-reach target cells → rate-limit gate → one legit `interactionManager.interactBlock()` per cell → position-cache to avoid double-fire → **row-batched two-pass item-swap** (do the whole row with item A, swap once, whole row with item B). Pluggable `TargetResolver` + shape enum (rect now, circle later). Greenlit for Shedmon (no anticheat).
- [x] Spec the engine contract + the gotchas → **`FARMHAND_SPEC.md`** ✓ 2026-06-23 (tool-batching, idempotency, out-of-seeds, trample-retill all covered).

> **Folded into FarmHand (§A):** the crop placer (→ **Till + Plant**) and AutoReaper (→ **Reap**) are no longer standalone mods — they're FarmHand capabilities on this engine + the shared `Field`. They live in §A now; D0 just powers them.

### D1. 🍎 ApricornArborist — `[new]` *(separate from FarmHand — "handled differently")* (S–M, needs D0)
Walk-by harvest + replant apricorn trees. Different target shape (single trees on a timer, not a flat field grid) and tied to the ball economy, so it's its **own** mod, not a FarmHand mode. **Best economic fit** — balls = apricorns in your survival grind.

### D2. 🥚 EggHarvester — `[new]` client walk-by (M, needs D0)
Walk a pasture row → pull eggs into boxes. The **Shedmon-compatible** answer to the server-side Collector (§B).

### D3. 🏗️ SnackStacker / 🐴 PastureSmith — `[new]` (M each, needs D0)
Place the snack tower (from a `.litematic`) / lay pasture-block + fence grids. Building 184 pastures by hand is the real crime.

### D4. Readers (no engine, anticheat-trivial)
- [ ] **DittoMatch** — see §A-EggOracle. (M)
- [ ] **EVScope** — in-game EV-yield logger → exports to the Python dataset (§B-Py). (M)
- [ ] **SpawnScope** — overlay the planner's spawn band + live "best snack / expected Ditto-per-hr" from the engine. (M)

---

## E. Cross-cutting / suite-level decisions
- [x] `[decision]` **Consolidation → FarmHand:** HydroGrid + CropPlacer + AutoReaper merged into **FarmHand** (§A) on the shared `Field`; apricorns stay separate. ✓ 2026-06-23
- [ ] `[decision]` **Build order:** WalkByPrinter (D0) → FarmHand **Till+Plant** → **Reap** → EggHarvester / SnackStacker nearly free. Agree the spine before writing code.
- [ ] `[decision]` **Other consolidations:** Highlighter ↔ EggOracle Culler overlap (§A); Collector ↔ EggHarvester (§B/§D). Decide merge-vs-keep-both for each.
- [ ] `[decision]` **Shared infra:** a `colorblind-ui` lib (symbol/shape/aura primitives) + a `ReachPulse` "cells-in-range now" overlay, imported by every relevant mod. Worth factoring out, or keep copy-paste? Every mod is hand-rolled 0.1.0 today.
- [ ] `[add]` **The data loop:** Highlighter stats + EVScope + a spawn-capture mod all feed `pokesnack/`'s ML layer. The mods instrument the game; the engine learns. (This is the portfolio story.)
- [ ] `[release]` **Ship pipeline:** ready-now = colorblind-ore-pack + Shiny Egg Highlighter; staged = HydroGrid, EggOracle, PokeSnack Planner (pending relaunch test). Modrinth/CF descriptions + icons + the `J`-test verdict gate the EggOracle listing.

---

### Suggested talking spine (if you want one)
1. **Unblock-or-kill:** run the EggOracle `J` pasture-sync test → decides PasturePilot's whole future.
2. **Free win:** release colorblind-ore-pack (it's done).
3. **The engine + FarmHand:** spec + build WalkByPrinter (D0) → FarmHand's **Till+Plant** & **Reap** → EggHarvester / SnackStacker.
4. **The brain:** DittoMatch + the EggOracle IV-filter/persist work (research already done).
5. **The loop:** EVScope + capture pipeline → Python ML layer (the Kaggle-prep throughline).
6. **Consolidate + ship** the overlapping overlays and the staged trio.
