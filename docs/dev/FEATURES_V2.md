# 🧭 Greener Pastures — v2 Feature Spec (the "you'll get asked for these" wave)

_Companion to `PICKUP_HERE.md`. Source: a predicted-feature-request list Deuce surfaced (2026-06-29). This is the
**implement → test → QA** plan for each, ordered by value-per-risk **given what we've already built**. House rules
apply throughout: logic-first (MC-free cores, unit-tested headless via `./gradlew test`), config-driven +
admin-toggleable, observability via `GpLog`, **commit-not-deploy** (batch QA), every MC/adapter change gets a
`QA_PENDING.md` row. See [[testing-and-logic-first]], [[batch-qa-workflow]], [[observability-first-logging]]._

## At a glance — priority & cost

| # | Feature | Tier | Cost | Why this rank |
|---|---|---|---|---|
| F1 | **Breeding-meta augments** (Nature → Hidden Ability → Ball → Egg Moves) | 🥇 do now | **S** (Nature), M (rest) | #1 reply-bait; reuses the Augment+egg-shaping systems we OWN; **verified** = plain string setters on the egg spec |
| F2 | **Notifications** (shiny / Data threshold / pulls ready) | 🥇 do now | **S** | idle mod → people walk away; we already emit the events |
| F3 | **Breeding Goal / Project tracker** | 🥈 next | M | uniquely ours; wires odds engine + hatch events + auto-cull; better once F1 exists |
| F4 | **Hopper / AE2 interop** | 🥉 wave 2 | M | "does it work with hoppers?" is inevitable; mechanical per-block `Storage` faces |
| F5 | **Richer dashboards / shiny-hunt stats** | 🥉 wave 2 | M | already task #6; pure data + HTML export, no MC render |
| F6 | **Multiplayer economy** (Data trade, leaderboard) | 🥉 wave 2 | L | server-owner demand; real scope (networking + anti-abuse) |
| F7 | **Admin config + in-game guide** | 🥉 launch | S/M | config is cheap (house style); guide (Patchouli/EMI) is launch-gating content |

Build order I'll execute: **F1 (Nature first) → F2 → F3**, then re-sync with Deuce before the wave-2 / launch items.

---

## ✅ Feasibility already verified (the make-or-break checks)

- **Cobblemon `PokemonProperties` exposes the 4 breeding-meta traits as plain string setters** (verified via javap
  on `Cobblemon-fabric-1.7.3+1.21.1`): `setNature(String)`, `setAbility(String)`, `setPokeball(String)`,
  `setMoves(List<String>)`. The egg is built as a `PokemonProperties` spec (`CobbreedingBridge.buildEggForPair`),
  and Cobbreeding applies it at hatch via `PokemonProperties.apply` — **the exact path IV Floor / EV already ride**.
  So each new trait is a ~1-line `applyX(eggData, value)` mirroring `applyIvFloor`, wrapped in the same
  "egg-shaping must never abort egg-gen" try/catch.
- **Augment plumbing is one constant**: `AugmentFunction` is a flat enum whose own javadoc says
  _"Nature/Egg-Move/Ability/… join later as new constants."_ The Compiler/Tether machinery is generic over function.

---

## F1 — Breeding-meta augments  🥇  (the spearhead)

> Force the competitive-breeding traits onto the egg: **Nature** (the #1 ask), **Hidden Ability**, **Ball**,
> **Egg Moves**. Each is an Augment function installed on a Kernel via the Compiler, resolved per-pasture, and
> written into the egg spec before encryption.

### The one design wrinkle: a *selector* augment
Existing augments use `Augments = {function → level}` where level is a **magnitude** (more shiny, higher IV floor).
Nature/Ability/Ball are **selectors** — "which one", not "how much". Lowest-risk fit: **reuse the int level as a
1-based index** into a fixed, ordered, MC-free catalog. `level 0` = off; `level N` = the Nth entry.
- A pure `NatureCatalog` (the 25 vanilla nature ids, ordered, immutable) maps `index ↔ nature string`. Testable,
  no Cobblemon dependency (Cobblemon validates the string at hatch; an unknown string just no-ops).
- The future Compiler UI shows the **name** for the selected index; until UI exists, QA sets it via config / sneak-cycle.
- _Alt considered:_ add a `{function → String}` param map to the `Augments` component. More flexible (needed for
  **Egg Moves**, which is a *list*), but a component-schema change. Deferred to Egg Moves only.

### Implementation (Nature, then repeat the shape)
1. **`AugmentFunction.NATURE`** (`economy/AugmentFunction.java`) — one constant, `TetherClass.QUALITY` (like Shiny/IV).
   _(then `ABILITY`, `BALL`, `EGG_MOVE` as the batch continues.)_
2. **Pure `NatureCatalog`** (new, `pasture/breeding/`) — ordered `List<String>` of the 25 nature ids + `byIndex(int)` /
   `indexOf(String)` / `size()`. MC-free → unit-tested.
3. **Resolve** in `EffectiveAugments` / the breeder: turn the pasture's `NATURE` level into a nature string
   (`NatureCatalog.byIndex(level)`), thread it into `CobbreedingBridge.buildEggForPair(...)` alongside ivFloor/evFloor.
   (Tethers don't amplify a selector — a Nature Tether is meaningless — so NATURE is resolved as a flat base value,
   NOT run through the throughput drain split. Note in `TetherRuntime` that selector functions skip amplification.)
4. **`applyNature(eggData, natureId)`** in `CobbreedingBridge` — mirrors `applyIvFloor`:
   ```java
   private static void applyNature(PokemonProperties eggData, String natureId) {
       if (natureId == null || natureId.isBlank()) return;
       try { eggData.setNature(natureId); } catch (Throwable t) { /* never abort egg-gen */ }
   }
   ```
   Call it in `buildEggForPair` right after `applyEvFloor`.
5. **`GpLog`** line `breeding nature_lock species:… nature:…` on apply.

### Tests to write (headless, pure)
- `NatureCatalogTest`: exactly 25 entries; stable order; `byIndex` 1-based + clamps/`null` out of range; `indexOf`
  round-trips; ids are lowercase + de-duped; `byIndex(0)` = off/null.
- `EffectiveAugmentsTest` (extend): a `NATURE` level resolves to the right nature string; level 0 → no nature; a
  Nature Tether does NOT amplify (selector stays flat); disabled/absent → null.
- _(The `applyNature` mutation itself is a thin Cobblemon adapter → QA'd in-game, like `applyIvFloor`. No unit test —
  trusted by the pure resolver + the in-game row, same precedent as IV/EV.)_

### QA rows
- **Qxx Nature lock**: install a Nature augment (index = Adamant) on a pasture's Kernel → every egg from it hatches
  **Adamant**, regardless of parents/everstone; `~/gp-logs/latest.log` shows `breeding nature_lock nature:adamant`.
  Off (level 0) / no augment → vanilla nature inheritance. Shiny/IV/EV from the same pasture still apply (composes).
- Repeat-shape rows for **Hidden Ability** (`setAbility("hidden")` — verify Cobblemon's HA spec token),
  **Ball** (`setPokeball(id)` — selector or "inherit mother's ball"), **Egg Moves** (`setMoves(List)` — needs the
  param-map; spec its UX when we get there: likely "preserve all eligible parent egg moves" as the v1 binary).

### Batch sequencing & status — ✅ COMPLETE (all four shipped)
- ✅ **Nature** (`6a7df50`) — `NATURE` selector + `NatureCatalog` (25) + `applyNature`. QA Q31.
- ✅ **Ball** (`5425318`) — `BALL` selector + `BallCatalog` (32 namespaced ids) + `applyBall`. QA Q32.
  ⚠ verify the egg-spec ball-id format (`cobblemon:poke_ball` vs bare path) — fails *safe* either way.
- ✅ **Hidden Ability** (`950c8ad`) — `ABILITY` binary toggle; resolves the species' hidden ability by scanning the
  `AbilityPool` for the entry whose `getType() != CommonAbilityType.INSTANCE`. ⚠ heuristic to confirm in QA.
- ✅ **Egg Moves** (`4a06bac`) — `EGG_MOVE` binary toggle; writes `Learnset.getEggMoves()` (first ≤4) via `setMoves`.
- ✅ **`EggShape` refactor done** (`950c8ad`) — `buildEggForPair(pairSlots, EggShape shape)`; the record carries all
  7 shaping inputs. No more positional-param creep.

All four write the trait onto the egg `PokemonProperties` pre-encrypt (same seam as IV/EV), fail-safe, non-destructive.
**Remaining for the batch = the install UX** (pick the nature/ball; toggle ability/egg-moves) — part of the deferred
**Compiler UI**; for QA, set augment levels via creative/command. **Next: F2 Notifications.**

---

## F2 — Notifications  🥇  ✅ v1 SHIPPED (shiny ping)

> It's an idle mod; people walk away. Ping on: **shiny hatched**, **Data crossed a threshold**, **ritual pulls ready**.
> Toast + sound + optional chat, all config-gated.

**✅ Shipped (this session) — TWO triggers:** the `notify/` package (`NotifyRules` pure + tested, `NotifyConfig`
lazy-Gson, `NotifySystem` holder, `Notifier` MC adapter), hooked into `Analytics.record` as a general event-stream
observer. (1) **shiny-egg-laid** → broadcast (all/ops), chat/actionbar/both + chime (QA Q35). (2) **Data milestone**
→ owner-targeted ping each time their Data crosses another `dataMilestoneStep` (default 1000), stateless
block-crossing off `egg_rendered` + a `DataStore` read (QA Q36). All in `notifications.json`. **Follow-on:**
ritual-pulls-ready (needs the pull state) — slots into `NotifyRules` as a third trigger. Toast deferred to the UI
wave (needs a client component).

### Implementation
1. We already have the analytics event stream (`analytics/` — `Event`, `EggEvent`, `EventLog`, `EventReader`) and
   per-egg events flow through `CobbreedingBridge`. **Notifications are an observer on that stream**, not new plumbing.
2. **Pure `NotifyRules`** (new) — given an event + the player's config + last-fired state, decide `should-notify` +
   the message. Handles **threshold-crossing** (fire once when Data crosses N, not every tick above it) and
   **debounce**. MC-free → unit-tested.
3. **MC adapter `Notifier`** — on a qualifying event: `ServerPlayerEntity.sendMessage`(actionbar/chat) +
   `playSound` (a soft chime) + optional toast (client packet, or just actionbar+sound server-side to stay UI-free
   for now — **toast needs a client component, so v1 = actionbar + sound + chat**, toast deferred with the GUI wave).
4. **Config** `config/greenerpastures/notifications.json` — master + per-trigger toggles + the Data threshold value +
   channel (chat/actionbar/sound). Lazy-Gson holder, fail-safe load (house style).
5. **`GpLog`** `notify fired trigger:shiny player:…`.

### Tests to write
- `NotifyRulesTest`: shiny event → fires once; Data threshold fires **only on upward crossing** (N-1→N yes; N→N+1 no;
  N→N-1→N re-arms); debounce window suppresses duplicates; disabled trigger / master-off → nothing; ritual-pulls-ready
  fires when pulls ≥ cost.

### QA row
- **Qxx Notifications**: hatch a shiny → chime + message; grind Data past the configured threshold → one ping (not
  spam); ritual pulls reach the bar → ping. Toggle each off in config → silent. (Toast deferred to UI wave.)

---

## F3 — Breeding Goal / Project tracker  🥈  (the uniquely-ours one) — ✅ v1 SHIPPED (track-only)

> "I want a 6IV shiny Adamant Garchomp." Define the target; the mod shows live progress + expected eggs-to-go and
> **auto-culls everything off-target into Data**. This is the data-science pitch made playable — and mostly *wiring*.

**Deuce's call: TRACK-ONLY v1** (non-destructive — watch + report, NO auto-cull; auto-cull is the follow-on).

**✅ Shipped (`c3a385a`):** the pure cores — `goal/BreedingGoal` (species/shiny/min-perfect-IVs/min-IV-total/count,
all optional; `matches(EggSummary)`) + `goal/GoalProgress` (immutable fold: checked/matched/best-IV-total, `reached`).
Reuse the existing `EggSummary`. 11 tests.

**⏳ MC wiring next (integration points all verified) — a focused multi-file increment:**
1. **`/gp goal` command** — the mod's FIRST command (no command subsystem exists yet → set up
   `CommandRegistrationCallback`). Sub-commands: `set <species?> <shiny?> <ivs?> <count?>`, `show`, `clear`. Build a
   `BreedingGoal` from the args; print `goal.describe()` + progress on `show`.
2. **Per-player store** — `GoalStore`: `Map<UUID, (BreedingGoal, GoalProgress)>`. In-memory for v1 (note: persist
   later via a `PersistentState` like `DataStore`).
3. **Egg observation** — attribute each laid egg to the pasture owner (`PastureData.owner`, a UUID — verified) and
   fold it into that player's progress. Two clean options: (a) add `species/ivTotal/perfectIvs` to the `BredEgg`
   record (computed in `buildEggForPair` from `eggData`) and enrich the `egg_laid` event, then a `GoalTracker`
   observes the event stream like `Notifier`; or (b) the breeder reads the stack via `EggReader.read(stack)` +
   `EggReader.species(stack)` → `EggSummary`. Prefer (a) — no re-decrypt, reuses the observer pattern.
4. **"Goal reached" ping** — reuse the `notify/` infra (owner-targeted), fire on `progress.reached(goal)`.
5. QA row. Auto-cull stays OUT (the deferred destructive follow-on; when built: off-target non-shiny → Renderer→Data,
   shiny 4-guard intact, opt-in).

### Implementation
1. **Pure `BreedingGoal`** (record) — target: species + optional nature + per-stat IV minimums + shiny? + ability/ball.
   `matches(EggSummary)` predicate (we already have `EggSummary` from the BioBank/cull work). MC-free → tested.
2. **Pure `GoalProgress`** — fold the hatch-event stream into: hatched-toward-goal count, best-so-far, and
   **eggs-to-go** = `ceil(1 / P(match))` using the existing odds engine (`ShinyOdds` + IV/nature independent probs).
   Honest "expected" math, unit-tested.
3. **Storage**: the active goal lives on the Daemon (or a "Project" item) as a component; progress is derived from the
   `EventLog`, not stored (recomputable = no dup state).
4. **Auto-cull hook**: when a hatched/​collected egg **fails** `goal.matches`, route it through the existing
   Renderer cull → Data (the SACRED shiny 4-guard still protects shinies). Config toggle (auto-cull on/off; never
   cull shinies even if off-goal unless explicitly allowed).
5. Surfaces through **F2 notifications** ("goal reached!") and **F5 dashboards** (progress view). UI is the last step
   (deferred); v1 reports via command/actionbar + the JSONL.

### Tests to write
- `BreedingGoalTest`: `matches` honors each clause (species/nature/IV-floor/shiny/ability); partial targets;
  empty goal matches all.
- `GoalProgressTest`: eggs-to-go = inverse of combined independent probabilities; a perfect hatch zeroes remaining;
  off-goal hatches don't advance; matches the odds engine on a known fixture.

### QA row
- **Qxx Goal tracker**: set a goal (shiny Adamant, 4×31) → hatch eggs; on-target ones register progress + best-so-far,
  off-target ones auto-cull to Data (shinies spared); a matching hatch fires the "goal reached" ping. Eggs-to-go
  tracks down sensibly. Clearing the goal stops auto-cull.

---

## F4 — Hopper / AE2 interop  🥉  (wave 2)

> "Does it work with hoppers?" Expose inventory faces so eggs flow pasture → BioBank → Renderer → Data hands-free,
> and storage mods (AE2/pipes) can push/pull.

### Implementation (roadmap depth)
- Make `RendererBlockEntity` / `HarvesterBlockEntity` / BioBank implement `Inventory` + `SidedInventory` (vanilla
  hopper interop) and expose a **Fabric Transfer API `Storage<ItemVariant>`** per face (mod-agnostic push/pull).
- Define face semantics: top = insert (eggs in), bottom = extract (Data-rendered output / culled remainder), sides per
  block. Renderer's "insert egg → cull → Data" becomes a fully automatable pipeline; **shiny 4-guard still applies on
  the insert path** so automation can't feed a shiny to the grinder.
- Per-block QA rows (hopper feeds in, hopper pulls out, AE2 export bus if available).
- _No pure core_ — it's adapter surface; QA-driven. Tests limited to any slot-mapping helper.

---

## F5 — Richer dashboards / shiny-hunt stats  🥉  (wave 2; already task #6)

> Data is the brand. Streaks, luckiest/unluckiest species, **odds-vs-actual** ("running 1.4× expected"), session
> compare, living-dex completion. All off the `EventLog`; rendered to the existing **HTML export** (no MC rendering).

### Implementation (roadmap depth)
- Extend `analytics/DashboardStats` with pure aggregators: streak detector, per-species luck (actual/expected ratio
  from the odds engine), session windows, dex coverage. All pure → heavily unit-testable (this is the most
  test-friendly feature we have).
- `DashboardExport` grows the new sections (charts via inline SVG / a small JS lib in the HTML; no MC dep).
- Pure tests per aggregator (fixtures → expected numbers). QA = generate an export, eyeball it.

---

## F6 — Multiplayer economy  🥉  (wave 2; biggest scope)

> Server-authoritative already. Data trading between players, shiny leaderboard, "pasture of the week."

### Implementation (roadmap depth, flagged as L)
- **Data trade**: a C2S "transfer N Data to player X" with server validation (atomic debit/credit on `DataStore`,
  which is already per-player persistent); needs networking + an anti-abuse pass (rate-limit, confirm step, log).
- **Leaderboard**: derive from `DataStore` + the `EventLog` (shiny counts); a `/gp top` command first, GUI later.
- This is the one with real new surface (networking, trust boundaries) → its own design doc before building.

---

## F7 — Admin config + in-game guide  🥉  (launch-gating)

> Admins want to tune/disable the economy; newcomers need to know what a Daemon/Tether even is (the cryptic naming
> is great vibe, bad onboarding).

### Implementation (roadmap depth)
- **Config**: we already ship JSON config per system (rituals, buffs). Add an **economy** config (Data-per-egg burn,
  Data values, tether drain rates, master enable/disable) in the same lazy-Gson fail-safe pattern. Cheap.
- **Guide**: a **Patchouli** (or EMI) book explaining Daemon / Tethers / Renderer / Data / Augments / Rituals.
  Content work, not code-risk; gate it on the publish phase. Ship-with "Data Science" awareness book (task #18) is
  the seed.

---

## Cross-cutting reminders
- **Logic-first**: every feature lands a MC-free core first (`NatureCatalog`, `NotifyRules`, `BreedingGoal`,
  `GoalProgress`, the dashboard aggregators) with headless tests, then a thin MC adapter, then UI **last**.
- **Composability**: F1 augments compose with the existing shiny/IV/EV shaping (same `buildEggForPair`), F2 observes
  the F3 tracker, F5 visualizes F3 — they reinforce, build in this order.
- **No dupe surface**: F1 mutates the egg spec pre-encrypt (no item rewrite); F3 derives progress from the log (no
  dup state); F4 keeps the shiny 4-guard on the automated insert path.
- **Commit-not-deploy**: each increment → tests green → commit to `main` → a `QA_PENDING.md` row. Deploy only when
  Deuce calls a QA pass.
