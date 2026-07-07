# FarmHand — WalkByPrinter Engine Spec
_v1 build spec, 2026-06-23. The shared walk-by automation engine under FarmHand's **Till + Plant** and **Reap** capabilities. Grounded in the decompiled Litematica Easy-Place flow (`WorldUtils.doEasyPlaceAction` / `InventoryUtils`). Target: **MC 1.21.1 · Fabric · Yarn mappings** (match the existing mods)._

> Build the engine **once**; Till+Plant and Reap are each just a `TargetResolver` + an action set over it. SnackStacker / PastureSmith plug in later the same way.

---

## 0. v1 scope — assumptions (flip any of these before building)
- **Crops:** seed-crops only — wheat, carrots, potatoes, beetroot, nether wart. (Melon/pumpkin *stems* and bonemeal pass → v2.)
- **Place mode:** `PRINTER` (acts on cells as they enter reach while you walk) is default; `PLACE_NOW` (act on everything in reach once, then stop) also shipped.
- **Inventory:** hoe + the chosen seed are assumed **already in the hotbar**. No inventory reshuffling in v1 (that needs nastier packet work — v2).
- **Rotation:** **off** — we synthesize the hit result without turning the player (greenlit: Shedmon has no anticheat). `rotateBeforeAct` config exists, default false.
- **Reach:** vanilla (`player.getBlockInteractionRange()`); optional `+1` toggle like Litematica.
- **Field:** reuses FarmHand's existing `Field` (two corners → solved cell grid). Water auto-placement stays **out** of the printer (separate interaction; v2).

---

## 1. Architecture
```
Field (geometry)  →  TargetResolver (per-mode work queue)  →  WalkByPrinter (engine)  →  InteractAdapter (MC calls)
   already exists       Till+Plant | Reap                      tick loop / reach / rate-limit     till/plant/break
```
- **Field** — owns the cell grid. Must expose: `allCells()`, `waterSpots()` (from the field-fill solver), and `cropCells() = allCells − waterSpots` (so crops never target a water source).
- **TargetResolver** — turns Field + player state into an ordered `WorkItem` queue.
- **WalkByPrinter** — the brain: ticks, gates by reach + rate-limit, batches tool swaps, dispatches actions, stays idempotent.
- **InteractAdapter** — thin wrapper over `ClientPlayerInteractionManager`, cribbed from Litematica.

---

## 2. Core types (the contracts)
```java
enum Action { TILL, PLANT, BREAK }          // BONEMEAL → v2

record WorkItem(BlockPos pos, Action action) {}   // resolver decides side/hitVec is always top-face

interface TargetResolver {
    // Row-major (z-then-x) so tool batches naturally. Re-evaluated each tick from live world state.
    List<WorkItem> resolve(Field field, ClientPlayerEntity player);
}

interface InteractAdapter {
    boolean tillAt(BlockPos pos);              // hoe in hand → farmland
    boolean plantAt(BlockPos pos, Item seed);  // seed in hand → crop on farmland above
    boolean breakAt(BlockPos pos);             // left-click; crops are instabreak
    // each returns "did the block actually change to the expected state?"
}
```

### The two resolvers
- **Till+Plant** — for each `cropCell`: emit `TILL` if the ground is tillable (dirt/grass/path/coarse... → farmland) **and** `tillEnabled`; emit `PLANT` if the block above farmland is air. (If `tillEnabled=false`, it degenerates to PLANT-only — no tool swap, just hold seeds.)
- **Reap** — for each `cropCell` whose crop is mature (`CropBlock.isMature(state)`, or age==max for nether wart/beetroot): emit `BREAK` then `PLANT`. **Reap needs no tool swap** — hold seeds the whole time: left-click *breaks* regardless of held item, then `interactBlock` with the same seeds *replants*. Call this out; it's free.

---

## 3. The engine loop (state machine) — **the 70%**
Tick-driven (`ClientModInitializer` → `ClientTickEvents.END_CLIENT_TICK` → `WalkByPrinter.tick()`).

```
tick():
  if (!active || field == null) return
  queue = resolver.resolve(field, player)          // live, idempotent — re-checks world state
  inReach = queue.filter(w -> dist(player, w.pos) <= reach && faceReachable(w.pos))
  if (inReach.isEmpty()) { currentTool stays; return }

  if (now - lastActionTime < intervalMs) return     // rate-limit gate
  if (posCooldown.contains(w.pos)) skip              // per-pos 2s cache → no double-fire

  // --- tool batching (NO per-cell swap) ---
  wantedTool(action): TILL→hoe, PLANT/BREAK→seed
  prefer items whose wantedTool == currentTool.
  pick = first inReach item matching currentTool
  if (pick == null) {                               // nothing for current tool in reach...
      if (other-tool work exists in reach) { swapTool(); pick = first of that }
      else return
  }

  if (!haveItem(wantedTool(pick))) { warnOnce("out of " + item); dropAction(pick.action); return }
  ensureHeld(wantedTool(pick))                      // hotbar select; no swap if already held
  ok = dispatch(pick)                               // adapter call
  if (ok) { posCooldown.put(pick.pos, now+2s); lastActionTime = now }
```

**Why this batches:** holding the hoe, it tills every reachable untilled cell first; it only swaps to seeds when **no untilled cell is in reach** but a plantable one is. As you walk a row that's ≤1 swap per reach-window, not per cell. Hysteresis (swap only when current-tool reach work == 0) kills thrash when you straddle two rows.

---

## 4. InteractAdapter — concrete 1.21.1 calls (cribbed from Litematica)
```java
ClientPlayerInteractionManager im = mc.interactionManager;
Vec3d top = Vec3d.ofCenter(pos).add(0, 0.5, 0);                       // top-face center
BlockHitResult hit = new BlockHitResult(top, Direction.UP, pos, false);

// TILL  (hoe in main hand) → dirt becomes farmland
im.interactBlock(mc.player, Hand.MAIN_HAND, hit);  mc.player.swingHand(Hand.MAIN_HAND);

// PLANT (seed in main hand, target the farmland's top face) → crop spawns in air above
im.interactBlock(mc.player, Hand.MAIN_HAND, hitOnFarmland);  swing;

// BREAK (reap) — crops are instabreak, one call drops items + clears the block
im.attackBlock(pos, Direction.UP);  mc.player.swingHand(Hand.MAIN_HAND);
```
- `ensureHeld(item)`: scan hotbar, set `player.getInventory().selectedSlot`. If not in hotbar → `warnOnce`, drop that action type (v1 doesn't pull from main inventory).
- `dist`/reach: `mc.player.getBlockInteractionRange()` (+1 if the toggle is on).
- **Success check** = re-read `world.getBlockState(pos)` and confirm it matches the expected post-state (farmland / crop-age-0 / air). Drives idempotency + retry.

---

## 5. Gotchas / edge cases (each must be handled, not discovered)
- **Out of seeds mid-run** → `warnOnce` to HUD, keep doing the *other* action (tilling continues); don't spam.
- **Non-tillable / occupied cell** → resolver predicate skips (not dirt-family for TILL; crop already present for PLANT; mob standing on it → place fails, success-check catches it, retry later).
- **You walk away mid-row** → queue is positional, not index-locked; out-of-reach cells are just skipped this pass and revisited when back in reach. No "current index" to corrupt.
- **Double-fire / idempotency** → every action is state-checked pre (resolver) and post (adapter) + a 2s per-pos cooldown cache. Re-entry is a safe no-op.
- **Water-source cells** → `cropCells` excludes `field.waterSpots()`; never till/plant a water source.
- **Trampling** → if the player jumps and reverts farmland to dirt, the resolver simply re-emits TILL next pass (idempotency covers it). HUD hint: "don't jump on the field."
- **Client/server state lag** → client may think a cell is dirt the server already tilled; the post-action success-check + cooldown prevent a wasted second action.
- **Full inventory on Reap** → broken crops drop on the ground; walk-by pickup handles it. Note in HUD if inventory is full.
- **Seed/crop mismatch** → PLANT only fires for the configured seed; a stray melon stem etc. is ignored in v1.

---

## 6. Config (v1 knobs)
`mode` (TILL_PLANT | REAP) · `seed` (item) · `tillEnabled` (bool) · `placeMode` (PRINTER | PLACE_NOW) · `intervalMs` (rate limit, default ~120) · `reachPlusOne` (bool) · `rotateBeforeAct` (bool, default false) · `hoeSlot`/`seedSlot` (hotbar hints). Persist with the Field plan (FarmHand's persistence chore).

---

## 7. Wiring & UX
- New keybind: **start/stop the printer** (e.g. `B` for "build/run") + the existing FarmHand mode select (Water / Till+Plant / Reap — the `[decision]` in MOD_IDEAS §A).
- Reuse `HydroRenderer` to tint work cells by state (to-do / done / blocked) — `[CB]` shapes, not just hue.
- Package: `farmhand/printer/` → `WalkByPrinter`, `InteractAdapter`, `TillPlantResolver`, `ReapResolver`; `farmhand/field/Field` (extend existing).

---

## 8. Test plan
1. **SP flat world:** mark a small field. Till+Plant → confirm tilled + planted, ≤1 swap per reach-window, no double-place. Pull seeds mid-run → confirm one warning + tilling continues.
2. **Reap:** bonemeal the field to mature, run Reap → confirm break+replant, items picked up, zero tool swaps.
3. **Edge:** jump on a cell (trample) → confirm re-till. Stand outside reach → confirm nothing fires.
4. **Shedmon:** small field, confirm packets accepted, no kick, reach feels right.

---

## 9. v2 backlog
Bonemeal pass · melon/pumpkin stem handling · inventory auto-refill from a linked chest · right-click-harvest-mod detection (skip the break) · circle/shape resolvers · terraced fields · water auto-placement · SnackStacker resolver (place from a `.litematic`).
