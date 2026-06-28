# 🧬 BioBank — AE2-style egg storage (design)

_Greener Pastures · **2026-06-28**. Deuce's ask: stop needing "90 million chests" for thousands of eggs. Refined into an **Applied-Energistics-style egg storage network** — store eggs as data, browse/sort them in a terminal. Pairs with `EGG_STORAGE_DESIGN.md` (the per-pasture FIFO) + the dark-economy Renderer. `glow EGG_DATABASE_DESIGN.md`._

## Name — BioBank (LOCKED 2026-06-28)
**BioBank** (`greenerpastures:biobank`) — a real facility that stores frozen eggs / embryos / specimens. One word (matches the lexicon cadence: Daemon · Renderer · Compiler), and it leans into the locked dark-economy soul: you **bank life en masse** to render most of it down. The browse/sort GUI = the **BioBank terminal**. _(This design doc keeps its filename `EGG_DATABASE_DESIGN.md`.)_

## The vision (Deuce, 2026-06-28)
An **AE2 ME-system for eggs**: a block (next to the Renderer) where eggs live as **data, not stacks**. Open its terminal → a tile per **species** (Charmander, etc.) with a count; click a species → all its eggs; **sort/filter by shiny, IVs, nature, …**. One block replaces stacks of chests.

> Deuce: _"like how the AE2 ME system works, where each SLOT is an egg type… slot in a charmander stack, open the UI, click charmander, all the eggs are inside that area, and sort by various things — is it shiny, IVs, etc."_

## Why it fits the theme (it's a queryable DataFrame)
Eggs = **rows**, properties (shiny / IV / nature) = **columns**. "Sort by column" = a **query**. The store is literally a **dataset / dataframe of eggs** — the most on-brand storage a "Data Science Mod" could have. Lexicon name candidates: **Data Lake** · **Warehouse** · **Database** · **Dataset Drive**. (Distinct from the analytics **Dashboard**.)

## Key principle — store as DATA, materialize on demand
Each egg's full genome is baked into its ItemStack (read today via `EggReader` → `EggUtilities.extractProperties`). We store a **compact record per egg** (the egg's data), keyed by species. The real ItemStack is rebuilt **only when withdrawn**. This is the exact principle already locked in `~/pokemonthink/DAEMON_AND_TETHERS.md:94` ("materialized up to the cap → no thousands-of-stacks reload spike") — no lag, no chest walls.

## Storage backing (scale-safe)
- Egg records live in a **per-world `PersistentState` / save file** (like `PastureRegistry`), **NOT** crammed into block-entity chunk NBT — so it scales to many thousands without chunk-save bloat.
- The block is the **access point**; the data is world/account-bound.
- **Paged UI:** the terminal sends **species tiles + counts** (cheap) and only the **current species' page** of eggs on drill-in — never 10k stacks to the client at once.

## Relationship to the Renderer — CONFIRMED: separate, placed in-line (Deuce 2026-06-28)
Keep them as **distinct blocks**; the Database is the **hub between the pasture and the Renderer**:

```
 Pasture  ───►  BioBank (this block)  ──[render filter]──►  Renderer  ───►  Data
 (breeds)       stores eggs as data, by species   you pick the cull            (currency)
```
- **Renderer = the SINK** — culls eggs → **Data**, never materialized (dark-economy centerpiece, per `ITEM_TAXONOMY.md`).
- **BioBank = the KEEP + hub** — auto-ingests the pasture's eggs, stores them losslessly by species; **you** decide what flows onward.
- **The filter lives on the Database → Renderer step**, so *you never render eggs you wanted* — keepers stay, only the filtered cull is sent. (The manual/interactive twin of the design's **Data Cleaning** augment, which automates the same threshold.)

They're **opposite operations** (keep vs. destroy), so they stay separate blocks — but physically adjacent and wired `pasture → database → renderer`.

## The Send ledger — a dry-run preview before the destructive render (Deuce 2026-06-28)
Rendering is **destructive** (eggs → Data, gone), so the terminal shows exactly what a **Send** will consume **before** you commit. A guardrail, and on-theme: *preview the query result, then COMMIT.*

Next to the **▸ Send to Renderer** button, a live **ledger** of the pending batch (the current filter's matches), grouped by species as negatives:
```
  Render preview              ▸ Send to Renderer
  ─────────────────────
  −500  Froakie
  −90   Charmander    ✦ shiny inside ✦
  −12   Bulbasaur     ⚠ 31-IV inside
  ─────────────────────
  602 eggs  →  ~X Data
```
- **Per-species counts**, negative (they're leaving).
- **Independent safety scan** of the actual batch — **NOT** derived from the filter (defense-in-depth against a mis-set filter): any **shiny** or **perfect-IV** egg flags its species line loudly (`✦ shiny inside ✦` / `⚠ 31-IV inside`). 'Valuable' = shiny **OR** ≥1 perfect (31) IV by default; configurable.
- **Optional confirm-click when any flag is showing**, so a shiny is never one misclick from the furnace.
- Total egg count + projected Data at the bottom.

`GpLog` traces it: `db.render.preview` (per-species counts + flags) and `db.render.commit` (count, data, anyFlagged).

## Scope — the AE2 *experience*, not AE2's *complexity*
- ✅ Digital storage (data, not stacks), type-organized, sortable terminal.
- ❌ No channels / cables / controllers / P2P for v1. One block = storage + terminal. (A multi-block network is a possible later luxury, not v1.)

## Sort / filter axes (from `PokemonProperties`)
shiny ✓ · IV total ✓ · # perfect IVs ✓ · per-stat IVs · nature · ability · gender · egg moves.
**EVs: not meaningful** — EVs are battle-earned, eggs are all 0; sort by the above instead. (`EggReader` already reads shiny + IVs; extend it for nature/ability via the same reflection seam.)

## UI sketch (two-level drill-down)
1. **Top level** — grid of species tiles: sprite + species name + count badge (`Charmander ×412`). Search box + global sort.
2. **Species level** — that species' eggs as rows: ★ shiny flag, IV total / 6×perfect, nature; sortable column headers; filter chips (shiny-only, ≥X IVs). Actions per egg/selection: **Withdraw** (→ ItemStack) · **Send to Renderer** (→ Data).

## Build phases
- **Phase 0 — `GpLog`** (the observability seam; per `OBSERVABILITY.md`). Everything below logs through it.
- **Phase 1 — the core win:** Database block + BE + `PersistentState` backing; insert eggs (hopper / shift-click / GUI) → stored as data, auto-keyed by species; terminal top level (species tiles + counts) + **withdraw**. _This alone ends the chest walls._
- **Phase 2 — query:** species drill-down + sort/filter (shiny, IV total, # perfect, nature). The "DataFrame" experience.
- **Phase 3 — economy wiring:** "Send to Renderer" (→ Data) from the terminal; capacity tiers / insertable "cells" (bounded, AE2-style).

## Observability (per the locked standard)
Every step logs through `GpLog`: `db.insert` (species, shiny, ivTotal), `db.withdraw`, `db.render` (count → Data), `db.query` (filter + result count), capacity warnings. So we can watch the store fill/drain live while you play.

## Open decisions (Deuce)
- ✅ **Renderer relationship** — RESOLVED: separate block, in-line `pasture → database → renderer`, filter on the database→renderer hop, Send ledger w/ shiny/perfect-IV flags.
- ✅ **Name** — RESOLVED: **BioBank** (`greenerpastures:biobank`) — clinical specimen bank; fits the dark Renderer/Daemon lexicon.
- [ ] **Capacity** — single bounded block (config cap) · tiered blocks · insertable "cells" (AE2-style)?
- [ ] **Insertion** — hopper-fed · shift-click in GUI · a vacuum upgrade · all of the above? (auto-ingest from the adjacent pasture is now assumed)
- [ ] **Withdraw granularity** — single egg · whole species · filtered selection (e.g. "all non-shiny ≤90 IV → Renderer").
- [ ] **'Valuable' flag threshold** — shiny OR any 31-IV (default) · or also an IV-total cutoff · player-configurable?
