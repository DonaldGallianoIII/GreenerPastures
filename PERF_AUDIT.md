# 🔬 Performance Audit — Greener Pastures (2026-06-28)

_Two-agent sweep (server hot-paths + client/heap), analysis-only — nothing was changed. Goal: **don't brick people's games.** `file:line` cited; full reasoning in the agent transcripts. `glow PERF_AUDIT.md`._

## TL;DR
- ~~One server-wide TPS-killer (the collector's cube scan)~~ → **✅ GONE: the Shiny Egg Collector was deleted entirely** (2026-06-28; its role → BioBank + Renderer + scripting filters). The worst offender is removed by deletion.
- **One client per-frame offender** (the Arrange board relayouts every frame).
- A cluster of **HIGH**s: per-tick registry rebuild+string-parse, whole-dataset NBT re-encode on every save, on-thread Gson, per-frame Daemon model+wire allocation, two leaking static maps.
- ✅ **The pure-logic cores are clean** — economy/analytics/`EggQueue`/`CatchUp`/`ShinyOdds`/biobank: no O(n²), no leaks, all single-pass. (The test-first discipline paid off.)

---

## 🔴 CRITICAL

**C1 · ✅ RESOLVED (DELETED 2026-06-28)** — the Shiny Egg Collector block is removed entirely (role → BioBank + Renderer + scripting filters), so this ~2,196-lookup/sec cube scan no longer exists. _Original finding, for the record:_ `egg/collector/ShinyCollectorBlockEntity.java:44-69`
Every 20 ticks each collector scans a **13×13×13 cube** (RADIUS 6), allocating a `BlockPos` and doing a full `ItemStorage.SIDED.find` at every position — even air. 100 collectors ≈ **~220k API lookups + 220k garbage objects/sec.** Server-wide TPS death on any real shiny farm.
**Fix:** reject air with a cheap `world.getBlockState(p).hasBlockEntity()` before the API lookup · shrink RADIUS (2 = 125 positions, 17× cheaper) · cache adjacent-container positions, refresh on neighbor-update · raise interval to 40–100 ticks.

**C2 · Arrange board relayouts every frame (client)** · `pasture/breeding/gui/PastureArrangementScreen.java:177` (+118-160, 201)
`render()` calls `relayout()` unconditionally each frame: clears + rebuilds the chip-position map (`new int[]` per mon/frame), runs `bucketCount` per bucket (twice), and a `roster.stream().count()` — none of which changes unless you drag.
**Fix:** relayout only from `init()` + after `mouseReleased`/unpair/node-size change; fill an `int[] counts` in one pass; cache the unpaired count.

---

## 🟠 HIGH
- **H1 · `PastureRegistry.inWorld()` rebuilds a HashMap + string-parses every key, every tick per world** · `pasture/breeding/PastureRegistry.java:65-75` (called `MultiPairBreeder.java:45`). **Fix:** store as `Map<dim, Map<BlockPos,PastureData>>` → O(1) sub-map, zero alloc, no parsing.
- **H2 · Whole-dataset NBT re-encode on every save** · `PastureData.java:52-64` + `biobank/BioBankData.java:46-55`. One egg laid → `markDirty()` (~1×/sec on an active farm) → next autosave re-encodes **every egg in every pasture/bank**. **Fix:** per-entry dirty flags + cached encoded `NbtList`; re-encode only what changed.
- **H3 · `Analytics.record` runs Gson serialization on the tick thread, per egg** · `analytics/Analytics.java:47-57`. A 200-pasture burst = hundreds of on-thread Gson serializes in one tick. **Fix:** enqueue the raw event; serialize on the writer thread (or reuse `GpLog`'s hand-rolled JSON).
- **H4 · Daemon `buildModel()` allocates the whole view model + `double[]` per wire EVERY frame** · `client/ui/DaemonController.java:122-145` (from `DaemonScreen.java:57`). **Fix:** cache the model, invalidate a `dirty` flag on input; rebuild only the temp drag-wire per frame.
- **H5 · Bézier wires rasterized as ~1,200 `fill()` quads each, per frame** · `client/ui/DaemonView.java:126-139` + `McGpCanvas.java:27-41` (`seg` plots one `fill` per pixel). 8 wires ≈ ~9,600 fills/frame. **Fix:** cache wire polylines (endpoints are static); in `stroke`, draw one rect between samples instead of per-pixel (~8× fewer fills).
- **H6 · `EggQueue.snapshot()` full ArrayList copy on every save** · `PastureData.java:59` → `EggQueue.java:72`. **Fix:** add a `forEach`/iterator so `writeNbt` encodes in place.

## 🟡 MEDIUM
- **M1 · Leaking static maps** — `nextBreed` (`MultiPairBreeder.java:33`) + `suppressed` (`PastureKeeper.java:34`) never evict broken pastures → slow heap creep. **Fix:** drop on tier-loss/break + clear on `SERVER_STOPPED`.
- **M2 · Unbounded log queues** — `EventLog`/`GpLog` `LinkedBlockingQueue` (no cap); disk stall → OOM. **Fix:** cap (e.g. 100k) + drop-oldest; default `GpLog.minLevel=INFO` for release.
- **M3 · `PastureCollector.collect`** — `getEntitiesByClass` AABB + 7-lookup `findInventory` per pasture's `checkPokemon` · `pasture/keeper/PastureCollector.java:25-46`. **Fix:** cache adjacent-inventory presence, early-out, throttle.
- **M4 · `NotebookView.paint` re-measures `textWidth` for static tabs/labels every frame** · `client/ui/NotebookView.java`. **Fix:** precompute widths on resize.
- **M5 · Highlighter `isEgg` uncached per slot per frame** · `egg/highlighter/ShinyEggDetector.java:41-46` (+`ShinyEggHighlighterClient.java:71-87`). **Fix:** fold into the identity cache.
- **M6 · `ShinyEggDetector` cache** unsynchronized + full `clear()` at 8192 (recompute storm). **Fix:** size-bounded LRU.
- **M7 · Dashboard re-reads + re-parses the full append-only `events.jsonl` every view** · `analytics/EventReader`. **Fix:** cache aggregate keyed on log mtime/size.

## 🟢 LOW
Bounded per-row `Text.literal`/`String.format` in `FarmScreen`/`EggOracleScreen`/`CompilerScreen`/`PastureScreen` (standard MC idiom) · per-breed pair-collection rebuilds (infrequent) · `studio/StudioLive` 20fps repaint (**dev-only `JavaExec`, not in-game**).

## ✅ Confirmed clean (checked, not problems)
economy/* (O(1) scalars) · `DashboardStats`/`RenderLedger`/`EggQueue`/`CatchUp`/`ShinyOdds`/`IvFilter`/`RenderSelection` (single-pass, bounded) · BioBank has **no ticker** · egg-read reflection resolved once + client-only + per-stack cached · hit-testing is O(nodes) not O(nodes²).

---

## 🛠️ Fix status — 2026-06-28 (commits `7aaa040` → `f882fba`)
- ✅ **C1** collector scan — DELETED entirely (`7aaa040`).
- ✅ **H1** registry → per-dimension map (Batch 1).
- ✅ **M1** `nextBreed` leak → moved onto `PastureData.nextBreedTick` (Batch 1).
- ✅ **H6** `EggQueue.forEach` in-place encode, no snapshot copy (Batch 1).
- ✅ **H3** Analytics Gson moved to the writer thread (Batch 2).
- ✅ **M2** `EventLog` + `GpLog` queues bounded (100k, drop-on-full) (Batch 2).
- ✅ **H4** Daemon `buildModel` cached, rebuild-on-input (Batch 3; studio render byte-identical).
- ✅ **C2** Arrange board relayout-on-input only (Batch 3).
- ✅ **M5/M6** highlighter LRU cache + cache-first `isShinyEgg` (Batch 3).
- ⏸️ **H2** per-pasture NBT dirty-cache — **deferred**: data-loss risk if any mutation path is missed, for a situational gain (only helps when many pastures are idle); H6 already cut the per-save cost. Revisit only if profiling shows the autosave spike matters.
- ⏸️ **H5** Bézier stroke per-pixel — **deferred**: needs a real line primitive; cheap mitigations change the look. Client-only jank, only while the Daemon is open.
- ⏸️ **M3** PastureCollector — PastureKeeper is being retired (cut); skip.
- ⏸️ **M4** NotebookView textWidth — low value (stateless paint, ~10 measures/frame); skip.
- ⏸️ **M7 + LOW** — Dashboard parse-cache (user-triggered) + bounded per-row formatting; skip.

All fixes are pure perf (no gameplay/behavior change), test-backed, build-clean — **committed, NOT deployed** (one QA item, Q7).

## 📈 How to capture a real flame graph + heap histogram (in-game)
A true flame graph needs a profiled JVM — do it on the live dev instance (dev greenlit).
- **`spark` mod (recommended):** drop `spark-fabric` (1.21.1) into `mods/`. Flame graph: `/spark profiler start --alloc --thread "Render thread"` while dragging a Daemon wire (catches the per-frame garbage), or `--thread "Server thread"` during an autosave (catches the NBT re-encode) → auto-stops, prints a browser URL. Heap: `/spark heapsummary` before/after farming 1000+ eggs and diff (watch `ItemStack`/`NbtCompound`/`byte[]` + the queue maps). `/spark health` for GC pauses.
- **async-profiler:** `-agentpath:.../libasyncProfiler.so=start,event=alloc,flamegraph,file=gp.html` on the dev JVM, or `asprof -e alloc -d 60 -f gp.html <pid>`. Heap: `jcmd <pid> GC.class_histogram`.
- **JMH on this box (headless):** the pure cores are MC-free — `@Benchmark` `DaemonController.buildModel()` / `DashboardStats.summarize` / `EggQueue` with `-prof gc` to measure **bytes/op** and prove a caching fix drops allocation to ~0 before deploying.
