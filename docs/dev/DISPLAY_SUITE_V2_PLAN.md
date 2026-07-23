# 🏛 Display Suite v2 — Living Exhibits (build plan)

> Drafted 2026-07-22 with Deuce, continuing `DISPLAY_SPEC.md` (v1 = Exhibit Pen + Specimen Statue, shipped
> in `1.0.0-beta.2`). v1's **Specimen Statue is signed off** ("wonderful"). v2 turns exhibits from static
> displays into **interactive, path-walking, talking NPCs** — authored entirely through the Notebook.
>
> **Ships inside Greener Pastures — NOT a separate mod.** Same `greenerpastures` id, same jar, same
> Modrinth/CurseForge listing, one review cycle. (Deuce, 2026-07-22: "i dont feel like making a second
> listing and having to go through lots of days of review.") The Display Suite classes already live in
> the beta.2 jar; v2 is additive.

---

## ⚑ STATUS (2026-07-23 night — read this first on resume)

**Phase B: CODE-COMPLETE + committed. Resume at DEPLOY + QA sign-off (not building).** Latest `51d9814`.
Two tonight-QA fixes on top of the original 4 commits:
- **Patrol movement rewritten navigation → SCRIPTED** (`PatrolDriver`). Cobblemon's wander AI fought our
  navigation (`patrol_step` log: 29s stalls, "sometimes walks it, sometimes wanders off"). Now
  `takeControl()` disables the mon's AI + gravity and steps it straight to each waypoint; `releaseControl()`
  restores for WANDER. **LIVE on the server (server-side), UNCONFIRMED.** Watch: moonwalk/slide animation
  (cosmetic, fix ready) + no pathfinding (straight-line; record waypoints on terrain).
- **Per-resident SIZE** (was hardcoded 0.75×): Display-tab `<n>× size` button, cycles the Statue ladder
  (0.25×–3×), persists, re-projects live. **BUILT + jar ready, NOT deployed** (changed client UI → needs
  MC closed + 2 client swaps + server bounce).

QA server STOPPED cleanly for the night. **Next session: deploy size (server + `Claude Development` +
`Greener Pastures Test`), then QA sign-off** (patrol feel · size · reload-persist · stationary · wander),
tune, → Phase B DONE → Phase C (§4).

---

## ⚑ STATUS (2026-07-23 earlier — code-complete)

**Phase B: CODE-COMPLETE, awaiting live QA.** 4 commits `f8a79dd..c0fe04e` on `dev`. Full build green
(tests incl. 11 new `PatrolPathTest`); `greenerpastures-1.0.0-beta.2.jar` built + zip-verified. Not yet
deployed (MC was open). What shipped this session:
- **§3 MC-free `PatrolPath` core** — immutable/sanitizing state machine (loop / ping-pong / dwell), + `RelPos`
  (block-local, leash-clamped waypoint) + `PatrolMode` (WANDER/PATROL/STATIONARY). 11 tests.
- **Per-resident config + NBT** — each pen resident carries mode + `PatrolPath` (persisted; cursor re-derived
  at `start()` on reload). `PatrolNbt` keeps the core MC-free. Edit surface: setResidentMode / addWaypoint /
  removeWaypoint / clearWaypoints / setPatrolTiming + `patrolViews()`.
- **`PatrolDriver` goal** — thin shell driven every tick from the pen's `serverTick`; owns only entity +
  arrival check, delegates sequencing to the core; moves via the mon's own navigation (re-asserts only when
  idle, so a dwell holds it still). STATIONARY collapses to a hold-point. Leash now WANDER-only.
- **Authoring GUI + net** — display-tab per-resident controls (mode / "record here" / clear / undo / loop↔
  ping-pong / dwell ± / speed); `PATROL_*` verbs owner-gated in `applyPatrolAction`, riding the existing
  `display` channel as `slot|payload` in `arg`. `patrol_set`/`patrol_step` logs on the `display` channel.

**⚠ QA WATCH #1 — Cobblemon AI vs our navigation.** The projection is a live `PokemonEntity` with its own
wander behaviour; `PatrolDriver` re-issues `startMovingTo` only when nav is idle. If the mon fights us /
stutters / won't follow the path, the fallback is to suppress its wander (setNoAi or brain edit) or move it
scripted. This is the #1 thing to watch in the QA pass — I can't test the feel headless.

**QA checklist for Phase B:** place pen → insert disk → open Notebook → set PATROL → walk to spots and
"record here" a few → confirm the mon walks the loop, dwells, reverses in ping-pong, respects speed; break +
replace the block (chunk reload) → path persists and resumes; STATIONARY = greeter stands put; WANDER
unchanged. Tail the `display` channel for `patrol_step`.

---

## ⚑ STATUS (2026-07-22 — Phase A)

**Phase 0 + A: DONE and live-QA'd on the `gp-qa-server` + 4 clients.** 14 commits `bd7a720..2a653ee` on
`dev`. All green (~438 tests). Deuce signed off: *"works perfectly! all qa done on phase a."*

Shipped + verified in-game:
- **§0 shiny statue fix** — statue renders shiny + forms (forced aspect set).
- **§2 Notebook Display tab** — right-click a display block with the Notebook opens its config tab
  (`NotebookDisplayS2C` / `NotebookDisplayActionC2S` / DsBridge `display` channel / React `DisplayTab`).
- **§2.1 naming + "My Exhibits" registry** — `ExhibitEntry`/`ExhibitRegistry` (MC-free, tested) +
  `ExhibitStore` (PersistentState) + place/break hooks + lazy-register for legacy blocks. RENAME wired.
- **§2.2 disguise — CONFIG + RENDER both done.** `DisguiseModel` (ForwardingBakedModel) draws the mimicked
  block's quads with correct **biome tint**; live re-mesh on change (BE `readNbt` → `updateListeners`);
  render data via `RenderAttachmentBlockEntity.getRenderAttachmentData()` → disguise BlockState.
- **Real block art** ported from `~/CodexHangout/staged-mon-blocks` (namespace → `greenerpastures`):
  plinth → Statue, deepslate trigger-core → Exhibit Pen (+ horizontal FACING). 11 textures + animated energy.
- **Scale tweaks** — statue presets `0.25/0.5/0.75/1/1.5/2/2.5/3`; roaming pen mons at `0.75×`
  (`CobblemonProjector.EXHIBIT_SCALE`, `Pokemon.setScaleModifier`).

**Two open follow-ups (non-blocking):** roaming-mon `0.75×` is a starting guess — Deuce may want it
smaller; plinth wants a custom collision/outline shape (14/16 tall) + the energy texture isn't emissive yet.

**⚠ Pending deploy:** the scale-tweak commit `2a653ee` is committed but NOT yet on the running server/clients
(needs a bounce + client swap; MC was open). Redeploy before Deuce re-QAs the scale.

**NEXT: Phase B — Patrol pathing (§3 below).** Start with the MC-free `PatrolPath` core + tests, then the
GUI waypoint UX + the server goal. Everything routes through the Display tab / data model already built.

---

## 0 · Bug fix (immediate, ships first)

### Statue does not render shiny
- **Symptom:** a shiny mon on a disk renders as its non-shiny form in the Specimen Statue.
- **Root cause:** server side is correct — `CobblemonProjector.renderSpec()` adds `"shiny"` to the
  synced `RenderSpec.aspects`. The bug is in `client/display/StatueRenderer.build()`: it **ignores
  `spec.aspects()` and `spec.form()`** and only re-derives shiny via `pokemon.setShiny()` on a detached
  client `Pokemon`, which does not propagate the shiny aspect into Cobblemon's render aspect set.
- **Fix:** apply the full synced aspect set to the dummy entity (Cobblemon selects model/texture by the
  ASPECT set, not the `shiny` boolean). Force `spec.aspects()` onto the built `PokemonEntity`
  (forced-aspects / aspect data-tracker), and apply `spec.form()` while we're there — this fixes shiny
  AND regional/form skins in one shot. No server or data-model change; render-only.
- **Test:** headless can't render, so this is a manual + bot QA item. Add a guard test that `RenderSpec`
  built from a shiny specimen NBT contains `"shiny"` in `aspects` (locks the server-side contract).

---

## 1 · Design pillars (carry over from v1)

- **Projection principle:** the disk in the block is the single source of truth. Roaming mons and statues
  are projections — never saved to the world, never duped, die when the disk leaves. v2 adds behavior on
  top of projections; it must NOT weaken this invariant.
- **Author through the Notebook:** no new standalone screens. Everything (pathing, dialogue, rewards) is
  configured in a Notebook browser tab, same MCEF/React style as the pasture config screen.
- **Logic-first:** every behavior gets an MC-free, unit-tested core (`DialogueTree`, `PatrolPath`, config
  (de)serialization) before any MC wiring. UI/rendering last.
- **Observability:** every feature extends the `display` GpLog channel (JSONL) so a whole zoo can be
  tailed while it runs.

---

## 2 · Phase A — Notebook GUI for display blocks  ★ foundation, build first

Everything else is authored through this, so it comes first.

- **Open:** right-click an Exhibit Pen or Specimen Statue **with the Notebook** → the Notebook opens to a
  new **block-config tab**, exactly like right-clicking a pasture opens the pasture config screen
  (`awaitPastureUntil` / `NotebookPastureActionC2S` pattern). The tab is bound to that block's position.
- **Statue tab:** transform controls (rotate / offset / scale — already exist as actions, now given a
  real UI), plus the resident's summary (species · ★shiny · gender).
- **Exhibit Pen tab:** the slot list (up to 6 residents), and per-resident sub-config: **Pathing**
  (Phase B) and **Dialogue** (Phase C). Insert / eject a disk from the tab, not just sneak-click.
- **New net surface** (mirror the existing block-action pattern):
  - `NotebookDisplayC2S` (open/config actions: pos + action id + args) and a matching S2C push carrying
    the block's config JSON. New bridge channel `display` in `DsBridge`.
  - Reuse the existing "right-click block → open Notebook to this view" plumbing so pan/zoom, curtains,
    and the tab chrome come for free.
- **Data model (server, on the Block Entity NBT):** a versioned `ExhibitConfig` / `StatueConfig` compound —
  **block `name`** (§2.1) + **`disguise` state** (§2.2), plus per resident slot: `disk payload` (existing)
  + `patrol` (Phase B) + `dialogue` (Phase C) + `rewards` (§4.1). All MC-free record types with codecs so
  they round-trip and unit-test.

### 2.1 · Block registry + naming (the "don't lose your hidden blocks" safety net)

Because blocks can be **disguised** (§2.2), a builder who hides one has no way to find it again — so the
Notebook keeps an authoritative directory (Deuce, 2026-07-22):
- **Every display block is NAMED** (set in its config tab; defaults to `"Exhibit Pen @x,y,z"` until named).
- **Per-owner registry**, server-persisted (`PersistentState`, mirrors how pastures are tracked): on place →
  register `{pos, dimension, type, name}`; on break → deregister. The registry is the **source of truth**,
  independent of whatever the block visually looks like.
- **"My Exhibits" view** in the Notebook: a searchable list of every placed block — name · type · dimension
  · **exact coords** — so a disguised or forgotten block is always locatable. Nice-to-have: click a row to
  ping/highlight it in-world, or show a compass bearing to it.
- **Log:** `display/registered` / `display/deregistered` (pos, name, owner).

### 2.2 · Disguise (aesthetic gyms + puzzles)  — Deuce owns the real block models

A per-block toggle to make a display block **render as an ordinary block** so it blends into a gym build or
hides for a puzzle (Deuce, 2026-07-22, building the block art in parallel):
- **Set in the Notebook:** pick the block to mimic — v1 UX candidate: "copy the block **I'm looking at / next
  to me**", matching a neighbor in one click (no block-picker UI to build). Stores the chosen `BlockState`.
- **Render:** the block draws its `disguise` BlockState instead of its own model (client BER / model swap);
  the projection (roaming mon or statue) still renders on top exactly as before — the **mon stays visible,
  the block hides**. Toggle off → real model returns.
- **Owner reveal:** holding the Notebook (or a "reveal my exhibits" toggle) outlines your disguised blocks
  so the builder can still see them; everyone else sees only the disguise. Pairs with the §2.1 registry.
- **Puzzle use:** a disguised interactable block = a hidden switch (right-click → dialogue / reward /
  later a battle) that doesn't look like tech.
- **Decision (open):** does disguise copy the mimic's **collision / light / redstone**, or **visual only**?
  v1 recommend **visual-only** (simplest and safe) — note as a knob.

**Done when:** right-click-with-Notebook opens the correct tab for each block type; blocks can be named and
appear in the "My Exhibits" list with exact coords; disguise renders a chosen block and toggles back; edits
persist across reload; JSONL logs `display/config_open`, `display/config_edit`, `display/registered`.

---

## 3 · Phase B — Patrol pathing (Exhibit Pen)

The roaming mon follows authored waypoints instead of pure random wander.

- **Author in the GUI:** a per-resident waypoint list. v1 UX candidate: "record" mode — stand where you
  want a waypoint, click **Add Waypoint**, it stores the position **relative to the block**; reorder /
  delete in the list. (Avoids a 3D editor; leans on the player's own position.)
- **Mode toggle per resident:** `WANDER` (current Cobblemon tether wander) ↔ `PATROL` (walk the waypoints)
  ↔ `STATIONARY` (stand at a spot, face a direction — a greeter).
- **Server behavior:** a lightweight goal drives the projection along waypoints; **loop** or **ping-pong**;
  per-waypoint dwell time; movement speed. Respect the leash so it never wanders off the exhibit.
- **MC-free core `PatrolPath`:** given waypoints + mode + a tick, returns the current target waypoint and
  advances the index (loop / ping-pong / dwell countdown). Fully unit-tested; the MC goal is a thin shell
  that asks `PatrolPath` where to go next.
- **Data model:** `List<RelPos> waypoints`, `PatrolMode`, `dwellTicks`, `speed`, `loop|pingpong`.
- **Log:** `display/patrol_set` (waypoint count), `display/patrol_step` (debug: current index) gated behind
  DEBUG so it doesn't spam.

**Done when:** a mon walks its authored loop, dwells, respects the leash, survives chunk reload (path is
config, re-projects and resumes), and the FEEL is right (Deuce's subjective pass).

---

## 4 · Phase C — Branching dialogue + item rewards

Right-click the projection mon in the world → a visual-novel dialogue plays; branches on player choices;
nodes can hand out items.

### Authoring (Notebook GUI)
- Per resident, a **branching dialogue tree** editor: nodes of `{ speaker text, [choices] }`, each choice
  points to a child node (or ends). Start simple (list-of-nodes with id links); a node-graph canvas can
  come later, reusing the Daemon graph tooling.
- A node may carry a **reward**: item(s) handed to the player, via the **shared Rewards model** (§4.1).

### In-world interaction
- **Right-clickable projection:** intercept interaction on OUR projection entities (identified by the
  `gp_exhibit_projection` command tag) and route to the dialogue handler instead of Cobblemon's default
  (which we already suppress — battles/catching are off). Guard: only fires for tagged projections.
- **The modal — visual-novel style, NOT full screen (Deuce, 2026-07-22):**
  - Same **MCEF / React** stack as the rest, but rendered as a **small bar pinned to the BOTTOM** of the
    screen — the game stays visible above it, VN-style.
  - **Mon portrait on the RIGHT side** of the bar — render the resident the **same way Cobblemon draws the
    left-side party HUD slots** (that live party-portrait widget), shown inside the dialogue box (Deuce,
    2026-07-22). Reuses Cobblemon's own portrait render, so form / shiny / gender all read correctly and it
    matches what players already recognize — no separate sprite sheet to maintain.
  - Speaker text on the left; **branching choices** as clickable options beneath it.
  - Opening it does NOT navigate the full Notebook — it's a lightweight overlay view the client can raise
    directly on right-click (new S2C "play dialogue" push carrying the resolved tree + resident portrait id).
  - **⚠ Integration unknown — portrait compositing:** Cobblemon's party portrait is a **native 3D model
    render**; the VN bar is **MCEF (HTML)** — a model can't be drawn straight into the browser. Options to
    resolve in Phase C: (a) draw the Cobblemon portrait **natively as a layer** positioned over the MCEF
    bar's right slot (composite native + browser in one screen — likely cleanest); (b) render the model to
    an offscreen framebuffer → feed as an image into MCEF each frame (heavier); (c) a native, non-MCEF VN
    bar that just borrows the notebook's CSS look. Prototype (a) first.
- **MC-free core `DialogueTree`:** node navigation (choice → next node), terminal detection, reward-gate
  evaluation (has this player hit their claim limit?). Unit-tested with a fake per-player claim ledger; the
  MC layer just renders nodes and grants a **copy** of the reward when the core says the player may claim.

### 4.1 · Rewards model (shared by dialogue AND battle rewards)

The one rule (Deuce, 2026-07-22): **rewards are TEMPLATES, copied to the player, never consumed from the
unit.** The author defines the reward item once — e.g. a custom "Bob The Diamond" — and every earner
receives a fresh copy; the unit needs no stockpile and never runs dry.

- **Template stack:** the reward is a stored `ItemStack` (full NBT / custom name / lore intact), **cloned**
  on grant. Authored in the GUI (drop the item into a reward slot).
- **Claim limit is a COUNT, not scarcity:** a per-unit author setting — **how many times each player may
  earn this reward** (default 1; any N, or unlimited). Tracked per player UUID on the block. Hitting the
  limit refuses further grants; the template is untouched either way.
- Used identically by a dialogue node's reward (Phase C) and a battle victory (Phase D). One mechanism.

### Data model
- `DialogueTree { Map<NodeId, DialogueNode> }`, `DialogueNode { text, List<Choice>, RewardRef? }`,
  `Choice { label, NodeId next }`.
- `Reward { ItemStack template, int perPlayerLimit /* 0 = unlimited */ }` — the shared §4.1 model.
- Per-player claim ledger on the block (UUID → per-reward claim counts).

### Log
- `display/dialogue_open` (pos, player, resident), `display/dialogue_choice` (node → choice → next),
  `display/dialogue_reward` (items granted / refused-already-claimed), `display/dialogue_end`.

**Done when:** you author a branching tree in the Notebook, walk up to the mon, right-click, get the
bottom VN bar with the mon's portrait, click through branches, and receive a once-per-player item reward
(refused on a second try) — all logged.

---

## 5 · Phase D — Battle + post-battle rewards  ⏸ DEFERRED

**Explicitly on hold (Deuce, 2026-07-22):** battle actions require **their own visual-scripting layer**
(a battle-action node graph, kin to the Daemon) — deciding what the trainer does, win/lose branches,
reward/onward-dialogue on victory. That's a large subsystem and gets its **own spec**, not this build.

Noted so the v2 data model leaves room for it:
- A per-resident **`battleable` toggle** that would flip the v1 unbattleable invariant to opt-in
  (display mons stay unbattleable by default; only a flagged NPC can be challenged).
- A per-unit **battle-count setting** — how many times each player may challenge it (author-set; Deuce,
  2026-07-22). Same per-player tracking as the Rewards ledger.
- **Victory grants the reward via the shared §4.1 model** — a copied template item, capped by the count.
- Dialogue nodes reserve a future `onBattleWin` / `onBattleLose` branch hook.
- Post-battle detection will hang off Cobblemon's battle-end events; the projection must faint→re-project
  cleanly without ever becoming a real, savable entity (dupe-safety still holds).

Do NOT build any of this in v2. Just don't paint the data model into a corner.

---

## 6 · MC-free cores to unit-test (logic-first rule)

| Core | Responsibility |
|---|---|
| `PatrolPath` | waypoint sequencing: current target, advance, loop / ping-pong, dwell countdown |
| `DialogueTree` | node navigation, branch resolution, terminal detection, reward claim-gating |
| `ExhibitConfig` / `StatueConfig` codecs | serialize ↔ deserialize round-trip; version migration; defaults |
| `ExhibitRegistry` | default-name generation, dedup-by-pos, add/remove, name/coord search & sort (§2.1) |
| `RenderSpec` (extend) | shiny/form aspect contract guard (the 0-bug regression test) |

UI (React tabs, VN bar) and MC wiring (goals, entity interaction, BE NBT) sit on top of these and are
covered by the QA workflow below, not unit tests.

---

## 7 · Definition of done — the QA workflow (per phase)

Each phase ships only after all three gates pass (Deuce's process, 2026-07-22):

1. **Manual QA** — Deuce exercises it in-game on the QA server (the live-tail loop we've been running:
   deploy → bounce → tail the `display` JSONL → drive it by hand).
2. **Custom bot QA** — an automated client bot connects to the QA server and exercises the feature
   headlessly (place block, insert disk, walk the mon, right-click for dialogue, claim reward), asserting
   against the `display` JSONL. Repeatable regression coverage for behavior a unit test can't reach.
   *(Bot harness TBD — mineflayer-style headless client or a scripted fake player; scope in Phase A.)*
3. **Test passing** — `./gradlew test` green: the MC-free cores above plus the RenderSpec guard.

Only after 1+2+3 does a phase fold into the next GP release and get tagged.

---

## 8 · Build order (recommended)

1. **0 · Shiny statue fix** — tiny, ships immediately, good warm-up on the render seam.
2. **A · Notebook GUI + data model** — the foundation everything authors through. Includes **naming +
   the "My Exhibits" registry** (§2.1) and the **disguise** toggle (§2.2) — both needed the moment blocks
   can hide.
3. **B · Patrol pathing** — contained; finishes the "exhibit" half.
4. **C · Dialogue + rewards** — the NPC layer; the meat of v2.
5. **D · Battle** — deferred to its own spec + the battle visual-scripting layer.

A + B genuinely finish the Display Suite. C is the headline v2 feature. D is a future project.

---

## 9 · Decisions

**Settled (Deuce, 2026-07-22):**
- **Portrait source:** render the mon the same way Cobblemon draws the **left-side party HUD slots**, inside
  the dialogue box. Reuse Cobblemon's own portrait render (not a sprite sheet).
- **Rewards:** template **copy**, never consumed from the unit; per-player **claim/battle count** is the
  limit (author-set, default 1), not item scarcity. Shared by dialogue + battle (§4.1).

**Still open (decide as we reach them):**
- **Disguise scope:** visual-only vs also copying the mimic's collision / light / redstone (§2.2).
  Recommend **visual-only** for v1.
- **Waypoint authoring UX:** stand-and-record (recommended, no 3D editor) vs typed coords.
- **Bot QA harness:** which headless-client tech; decided in Phase A.
