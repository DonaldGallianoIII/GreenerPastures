# 🏛 Display Suite — Exhibit Pen + Specimen Statue (feature spec)

> Drafted 2026-07-14 with Deuce, born from the shiny-zoo project on Tinderbeef's server.
> **The problem:** 40 PC boxes is "a fuck ton" and still not a shiny living dex. Specimen disks already
> solve *storage* (mons as data on a shelf). This suite solves *exhibition*: getting disk-stored mons
> back into the world as zoo content, without PC round-trips and without breeding plumbing.
>
> Two blocks, two exhibit genres:
> 1. **Exhibit Pen** — works EXACTLY like a Cobblemon pasture (mons roam, wander, animate), except it is
>    fed by specimen disks instead of PC-linking and has **no breeding whatsoever**. The safari paddock.
> 2. **Specimen Statue** — insert a disk into a plinth block and it renders the Pokémon as a **frozen,
>    positionable statue**: rotate / offset / scale, never moves, never ticks. The museum piece.

---

## 0 · Shared foundation (both blocks)

### The projection principle (dupe-safety cornerstone)
- **The disk in the block is the single source of truth.** Whatever appears in the world — roaming
  entity or rendered statue — is a *projection* of the disk's data, never an independent copy.
- The disk payload is the existing lossless contract: `GpComponents.SPECIMEN` holds a full
  `Pokemon.saveToNBT` compound; `GpComponents.SPECIMEN_SUMMARY` holds the display summary
  (`SpecimenSummary`: species / shiny / etc.) — see `SpecimenDiskItem` javadoc ("media survives the read").
- **Invariant: there is NO code path where the mon exists twice.** Projections are never saved to the
  world, never catchable, never releasable, and die the moment the disk leaves the block.
- Eject or block-break always returns the *written* disk exactly as inserted. (Contrast with
  right-click release in `SpecimenDiskItem`, which consumes the payload and hands back a blank —
  display blocks never do that.)

### Ownership (shared-zoo rule, v1-simple)
- Block remembers its **placer UUID** (owner) and, per slot, the **inserter UUID**.
- Anyone can look. Only the inserter or the block owner can eject a disk. No locked-boolean claim
  machinery needed yet (the pasture operator-claim pattern stays where tether *costs* exist; display
  blocks cost nothing to run in v1).

### Registration + packaging
- Follow the existing block/BE registration shape (see `DarkEconomy` — `FabricBlockEntityTypeBuilder`,
  `Registry.register(Registries.BLOCK/ITEM, ...)`, item-group hook).
- New package: `com.greenerpastures.display` (server logic) + `com.greenerpastures.client.display`
  (BER + client state). Keep the MC-free rules in their own classes for headless JUnit
  (`ExhibitRules`, `StatueTransform`) per the logic-first testing rule.

### Observability (standing rule — every feature ships a live JSONL log)
GpLog channel `display`, events:
- `exhibit_insert` / `exhibit_eject` — block pos, slot, inserter, species, shiny
- `exhibit_project` / `exhibit_discard` — entity spawned/discarded (chunk load cycles), entity UUID
- `exhibit_refused` — refusal reason (blank disk, slots full, not owner, glitch mon)
- `statue_insert` / `statue_eject` / `statue_adjust` — pos, transform after adjust
- All carry `blockPos` + dimension so we can tail a whole zoo going up.

---

## 1 · Exhibit Pen (roaming display pasture)

### Player-facing behavior
- Place the block. Right-click with a **written** specimen disk → mon appears and starts roaming the
  area exactly like a pastured mon (same wander feel; reuse Cobblemon's tether wander parameters —
  config `pastureMaxWanderDistance` is the reference behavior).
- Up to **6 disk slots** (a pasture-sized herd per block; config `exhibitSlots`, default 6).
- Sneak-right-click with empty hand → eject last-inserted disk you're allowed to take (v1; a proper
  slot GUI can come later under the viewport-UI rules — no new screens needed for v1).
- Break the block → all projections despawn instantly, all disks pop as items.
- Blank disks are refused with the same message style as `SpecimenDiskItem`.
- Tooltip/plaque: hovering the block (WAILA-less v1: sneak-look chat line, or just the GUI later)
  lists residents from `SPECIMEN_SUMMARY` — species, ★ if shiny. "Donated by <inserter>" is free data.

### Entity lifecycle (the careful part)
- **Spawn:** on disk insert AND on chunk load, deserialize `Pokemon().loadFromNBT(SPECIMEN)`, create a
  `PokemonEntity` projection, position near block, apply flag set (below), `world.spawnEntity`.
- **Discard:** on chunk unload, disk eject, block break, or any tick where the backing slot no longer
  matches (belt-and-suspenders sweep) → `entity.discard()`. Projections are **never written to the
  world save** — if the server crashes mid-projection, the load path just re-projects from the disk.
  (Implementation: mark the entity with a GP data-attachment/tag; refuse vanilla save via the same
  trick Cobblemon uses for non-persistent spawns, or discard-on-save mixin if needed. VERIFY during
  build which seam is cleanest — candidate: spawn the entity with `setPersistent` semantics inverted,
  or intercept in `PokemonEntity.saveNbt`.)
- **Flag set on every projection:**
  - invulnerable (`Entity.setInvulnerable(true)`) — no player/mob/environment damage
  - uncatchable — Cobblemon-native uncatchable marking so thrown balls bounce/refuse
  - unbattleable — battles can't be initiated against it (Cobblemon busy-state or property; verify
    exact seam: `PokemonEntity` battle challenge goes through interaction — block it at our tag check)
  - no item pickup / no held-item drops / drops table never rolls (it never dies anyway)
  - not rideable, not shoulder-mountable, ignores player interaction other than our own handlers
  - **`countsTowardsSpawnCap` stays TRUE (default)** — deliberate: Cobblemon's spawner counts
    Pokémon entities per 3×3-chunk area ÷ 9 against config `pokemonPerChunk` (default **1.0**) and
    spawns NOTHING at/above cap (verified in 1.7.3 `Spawner.calculateSpawnActionsForArea` +
    `PokemonEntity.countsTowardsSpawnCap`, 2026-07-14 decompile). A stocked zoo therefore suppresses
    wild spawns automatically — the exhibits are the fence. Document this as a feature.
- **PastureKeeper interplay:** the ghost-pasture suppression toggle redirects Cobblemon's
  `tether()` → `World.spawnEntity` call (see `PokemonPastureBlockEntityMixin`). Exhibit Pens do NOT
  route through Cobblemon's tether at all (own block, own spawn call), so keeper suppression does not
  and should not apply — an Exhibit Pen whose whole job is showing mons is never "suppressed". If a
  server wants fewer entities, they stock fewer pens.

### What it does NOT do (scope fence)
- No breeding: no Cobbreeding contact, no egg production, no `CobbreedingBridge` involvement.
- No XP, no friendship ticks, no fullness, no berry feeding on projections (interaction blocked).
- No hopper/automation inventory exposure in v1 — disks move by hand only (kills a whole class of
  disk-eating and dupe bugs; revisit if a real need appears).
- No pasture GUI reuse — Cobblemon's pasture screen is PC-linked; ours is slot-simple.

### Numbers
- `exhibitSlots` = 6, `exhibitWanderDistance` = Cobblemon's pasture wander reference, projection
  respawn position = block-adjacent with the same "find open spot" approach pastures use.
- Per-chunk entity budget note for docs: 6 mons/pen × pens is real server load — same math as
  pastures. The suppression side-effect partially pays it back (fewer wild spawns ticking).

---

## 2 · Specimen Statue (frozen, positionable)

### Player-facing behavior
- Place the **plinth block**. Right-click with a written specimen disk → the Pokémon renders above
  the plinth, frozen mid-idle. It is scenery: it never moves, ticks, blinks at you, or pathfinds.
- **Adjustable in space** (the "move around in space" requirement):
  - **Rotate:** right-click empty hand cycles yaw in 22.5° steps (16 facings, armor-stand cadence).
  - **Offset:** sneak-right-click with stick nudges XZ offset in 1/16-block steps, cycling axes
    (v1-crude but workable); vertical offset ±1.0 for pedestal centering.
  - **Scale:** right-click with GPU-tier reagent? NO — keep v1 free: sneak-right-click empty hand
    cycles scale presets `0.5× / 1× / 1.5× / 2× / 3×` (config clamps, `statueMaxScale` default 3.0).
    A 3× shiny Absol at the park gates is the intended flex.
  - All adjustments replicate via BE sync; later a small owo-ui panel per the viewport-UI principle
    (never overload left-click; all current controls are right-click variants on purpose).
- Sneak-right-click with empty hand while holding... conflict with scale-cycle → **v1 control map to
  finalize at build time with Deuce in-game** (candidates: axe=rotate, stick=offset, shears=eject;
  spec intentionally leaves the exact tool bindings as an open question, see §5).
- Eject/break → statue vanishes, written disk returns.

### Rendering architecture (why this is NOT an entity)
- `StatueBlockEntity` + client `BlockEntityRenderer`. The BER holds a **client-side dummy
  `PokemonEntity`** (created from a slim render-spec, never added to any world), posed once and
  handed to Cobblemon's entity renderer inside the BER's render call with the BE's transform
  (yaw/offset/scale) applied to the matrix stack. Frozen = age pinned, animation time constant.
- **Slim render-spec, not full NBT, on the client:** at insert time the server computes
  `{species, form, aspects (incl. shiny), gender, scaleHint}` from the Pokémon and writes THAT to the
  BE's client-sync NBT. The full `SPECIMEN` compound (IVs, OT, moves — player data) stays server-side
  in the stored disk stack. Client needs only cosmetics; don't broadcast a player's whole mon.
- Zero server entities → zero AI, zero tick cost, zero catch/battle/dupe attack surface, and a
  museum hall of 50 statues costs the server almost nothing. Statues are the scalable exhibit.
- No collision beyond the plinth block itself (v1). Shiny source mons: soft gold particle whiff,
  client-side, toggleable (`statueShinyParticles`, default on) — it's the whole point of the museum.

### Persistence
- Trivial: BE NBT = disk ItemStack + transform + render-spec. No lifecycle management at all —
  chunk unloads and the BER just stops being called; loads and it renders again.

---

## 3 · Crafting (proposals — Deuce picks/replaces)
- **Exhibit Pen:** frame like a pasture's cheaper cousin with a data heart — e.g. planks/fence ring +
  glass + 1 blank data disk. Should be cheap enough to build a whole zoo (dozens).
- **Specimen Statue:** stone-pedestal flavor — smooth stone / quartz + 1 blank data disk. Also cheap;
  museums have many plinths.
- Neither consumes GPU/Data to *run* in v1 (display is free; the dark economy taxes creation flows,
  not exhibition). Revisit only if display somehow becomes an exploit surface.

## 4 · Testing plan
**Headless JUnit (MC-free cores, `./gradlew test`):**
- `ExhibitRulesTest` — insert refusals (blank disk, slots full, non-owner eject, glitch-mon refusal
  mirroring the MissingNo pasture rule), slot bookkeeping, eject ordering.
- `StatueTransformTest` — yaw step cycling wraps at 360, offset clamps (±1.0 vert, ±0.5→? horiz —
  finalize), scale preset cycle + config clamp, NBT round-trip of transform.
- `RenderSpecTest` — spec extraction from a Pokémon NBT fixture: shiny aspect present, form carried,
  no player-private fields (IVs/OT/moves) leak into the client compound.

**In-game QA (next batch session, standard checklist style):**
- Dupe hunt: break pen while mons projected; eject during projection; log out/in mid-projection;
  chunk unload/reload cycles; server stop/start with stocked pen (projection must not persist twice).
- Attack surface: throw every ball type at projection + statue; initiate battle; ride attempt;
  shoulder attempt; berry feed; Daemon/other GP item interactions — all must refuse cleanly.
- Spawn suppression: stock a pen with 9+ across a 3×3-chunk test plot, confirm ambient spawns stop
  (and snack-tower spawns continue — separate `pokeSnackPokemonPerChunk=2.0` budget).
- Statue: all adjust controls, scale extremes, unload/reload, MC restart, F3+A reload, shader pack on.
- Connector run on the NeoForge QA instance (see §6).

## 5 · Open questions (Deuce calls these during build)
1. Block names: "Exhibit Pen" + "Specimen Statue"? (alt: Habitat / Display Paddock; Plinth / Pedestal)
2. Statue v1 tool bindings for rotate/offset/scale/eject (finalize live in-game, feel-first).
3. Statue pose: frozen idle frame only (v1) vs. selectable poses from the mon's animation set (later).
4. Exhibit Pen plaque: chat-line on sneak-look (v1) vs. wait for Notebook zoo tab integration.
5. Does the zoo want a "census" view in the Notebook later (all display blocks + residents on one
   tab)? Cheap to add on top of GpLog events; not v1.
6. Statue offset range — is ±0.5 horizontal enough on a 1-block plinth, or allow reaching over edges?

## 6 · NeoForge/Connector parity notes
- Everything server-side here is plain Fabric API + Cobblemon API — same surface the Connector build
  already handles. The lazy-probe lesson applies: **never class-load Cobblemon internals during mod
  init**; both blocks touch Cobblemon only at interaction/render time, which is inherently late. Keep
  it that way.
- BER registration is client-init; mirror it in the `greener-pastures-neoforge/` tree the same way
  the existing client hooks were mirrored. (Fold-back of the CobbreedingBridge fix into main is still
  pending and should ride the same batch — see memory `neoforge-connector-support`.)

## 7 · Implementation order (suggested)
1. `display` package skeleton + block/BE/item registration for BOTH blocks (statue first is
   tempting, but pen exercises the disk contract harder — do **pen core first**).
2. `ExhibitRules` + tests green before any MC wiring (logic-first).
3. Pen: insert/eject/break + projection lifecycle + flag set + GpLog. In-game smoke on the QA instance.
4. Statue: BE + transform + slim render-spec + BER with dummy-entity render. Smoke with a shiny.
5. Adjustment controls pass (feel session with Deuce), shiny particles, plaque line.
6. QA checklist sweep + Connector instance run + fold into the batch for next deploy window.

*— spec by Claude + Deuce, 2026-07-14. The zoo is Los Pollos Hermanos and these are the menus.*
