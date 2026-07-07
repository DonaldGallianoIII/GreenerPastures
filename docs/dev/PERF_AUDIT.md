# 🔬 Performance Audit — Greener Pastures (2026-06-28)

_Two-agent sweep (server hot-paths + client/heap), analysis-only — nothing was changed. Goal: **don't brick people's games.** `file:line` cited; full reasoning in the agent transcripts. `glow PERF_AUDIT.md`._

## TL;DR
- ~~One server-wide TPS-killer (the collector's cube scan)~~ → **✅ GONE: the Shiny Egg Collector was deleted entirely** (2026-06-28; its role → BioBank + Renderer + scripting filters). The worst offender is removed by deletion.
- **One client per-frame offender** (the Arrange board relayouts every frame).
- A cluster of **HIGH**s: per-tick registry rebuild+string-parse, whole-dataset NBT re-encode on every save, on-thread Gson, per-frame Daemon model+wire allocation, two leaking static maps.
- ✅ **The pure-logic cores are clean** — economy/analytics/`EggQueue`/`ShinyOdds`/biobank: no O(n²), no leaks, all single-pass. (The test-first discipline paid off.)

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
economy/* (O(1) scalars) · `DashboardStats`/`RenderLedger`/`EggQueue`/`ShinyOdds`/`IvFilter`/`RenderSelection` (single-pass, bounded) · BioBank has **no ticker** · egg-read reflection resolved once + client-only + per-stack cached · hit-testing is O(nodes) not O(nodes²).

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

## 🩺 2026-07-01 — Overnight-crash audit (5-agent sweep) + fixes

_Trigger: Deuce reported a crash after the game was left running ~3–4h overnight. Five read-only agents swept
tick loops · collections/state · logging · threadlocals/mixins/registration · networking._

**Verdict: almost certainly NOT us.** No `hs_err_pid` (JVM fatal), no heapdump (`-XX:HeapDumpPath` is set — a
heap OOM writes one), no OOM report; the last session ended on a clean `Saving and pausing game…` with breeding
still hatching. The only GP crash on record is a **corrupted-jar deploy race** (`ZipException: invalid LOC
header` reading a GP class at startup, 0 players) — a half-written jar, not a leak. All slices agree: **nothing
grows unbounded on the heap while idle with zero players** (egg queue capped, log queue bounded+pruned, registry
has eviction). Likeliest real cause: external (Windows sleep/hibernate, GPU driver, another mod).

Still, the sweep found real 24/7-server hardening. **Fixed (build-clean, tests green):**
- 🔴 **`PastureSnapshotStore` unbounded + NBT-per-autosave** → access-order **LRU cap 64/player** + `removeAt(dim,pos)` wired into `BetterPasture.reclaim`.
- 🟡 **`GpLog` never drained on exit** → `shutdown()` + **JVM shutdown hook** (deliberately NOT `SERVER_STOPPING` — GpLog is JVM-lifetime; closing on quit-to-title would kill it for the next world that session).
- 🟡 **`PastureHarvest` could force-load idle chunks** → added the breeder's `isChunkLoaded` guard.
- 🟡 **Daemon buff caches never evicted a disconnected player** → `ServerPlayConnectionEvents.DISCONNECT → clear(player)` (covers `lastPaid`/`drainCarry`/`attributed`).
- 🟢 **Log volume** → launch-time knob `-Dgp.log.level` / `GP_LOG_LEVEL` (default DEBUG) + **TRACE-gated the idle heartbeats** (`buff:tick`, Harvester `skip_tick`/`tick`; Renderer `render` INFO→DEBUG). Finishes the 06-28 M2 "INFO for release" item as a runtime knob.
- 🟢 **`PastureRegistry.dimKey`** memoized (identity `ConcurrentHashMap<World,String>`) — no per-tick string alloc.
- ⓘ **`BlockDropBoostMixin`** exception-path self-heal documented (no behavior change).

**Deferred / skipped (with reason):** in-session log size-roll (writer is safety-critical; volume already
solved — add later if wanted) · duplicate `HandledScreen` accessors (cosmetic dedup, not perf) · `GoalStore`
disconnect eviction (**skipped** — would delete the player's active goal; the "leak" is 2 self-healing in-memory
entries).

**Deploy lesson:** the corrupted-jar crash was a deploy race — **fully close MC before copying the jar in, and
confirm the copy finished.**

## 📈 How to capture a real flame graph + heap histogram (in-game)
A true flame graph needs a profiled JVM — do it on the live dev instance (dev greenlit).
- **`spark` mod (recommended):** drop `spark-fabric` (1.21.1) into `mods/`. Flame graph: `/spark profiler start --alloc --thread "Render thread"` while dragging a Daemon wire (catches the per-frame garbage), or `--thread "Server thread"` during an autosave (catches the NBT re-encode) → auto-stops, prints a browser URL. Heap: `/spark heapsummary` before/after farming 1000+ eggs and diff (watch `ItemStack`/`NbtCompound`/`byte[]` + the queue maps). `/spark health` for GC pauses.
- **async-profiler:** `-agentpath:.../libasyncProfiler.so=start,event=alloc,flamegraph,file=gp.html` on the dev JVM, or `asprof -e alloc -d 60 -f gp.html <pid>`. Heap: `jcmd <pid> GC.class_histogram`.
- **JMH on this box (headless):** the pure cores are MC-free — `@Benchmark` `DaemonController.buildModel()` / `DashboardStats.summarize` / `EggQueue` with `-prof gc` to measure **bytes/op** and prove a caching fix drops allocation to ~0 before deploying.

---

# 🔍 Perf Audit Round 3 — 2026-07-03 (4 parallel Opus agents: tick paths · networking · client/MCEF · data/persistence)

Read-only audit; nothing changed yet. Findings deduped + adjudicated (one agent claimed the server BioBank path skips the
EggReader cache — wrong, the N1 LRU covers it, but banks >4096 eggs will thrash it). Health-key long→double precision
was flagged and DISPROVEN by direct computation for coordinates out to ±100k (packed BlockPos longs round-trip exactly).

## The three structural stories

**S1 · The console pipeline never idles.** The warm preload browser connects to the WS bridge ~2s into any world and
stays connected, so `hasClients()` is true forever → DsBridge rebuilds + GSON-serializes all 12 channels ~5×/s and
polls the server 1×/s **even with the console closed** — which triggers story S2 server-side, plus deep-equals in every
`NotebookState.apply*`, plus 80 CEF pump slices/s keeping Chromium hot 24/7. Fix: gate the pipeline + poll + steady-state
pump on console-actually-open (keep the dev-browser path via a preload tag; keep pushNow()/burst on transitions).

**S2 · The server has zero change detection.** `onRequest` rebuilds and re-sends ~13 channels every second per viewer:
BioBank flattens EVERY banked egg into a 20–40KB packet (10k-egg bank = 10k Entry allocs/s), pastures re-runs the health
pass (speciesCounts() map copy per pasture + getWorlds() scan per snapshot), augmenter meta rebuilds the immutable
25-nature/32-ball catalogs, dashboard/goals re-JSON — all usually discarded by downstream diffs after paying encode,
zlib, decode, and deep-equals. Fix: per-player-per-channel `pushIfChanged` (hash the serialized form, skip the send);
static-ify the catalog JSON; `BioBankData.countOf(species)` instead of speciesCounts() copies; dim→world map per push.

**S3 · markDirty amplification + the catch-up stall.** Harvest calls `reg.markDirty()` per due pasture (~always dirty)
→ every autosave re-encodes ALL pastures incl. full egg queues; BioBank/Notebook stores ditto per deposit. And the
catch-up loop runs up to 720 sweeps in ONE tick, each re-copying the roster + rolling drops — several pastures reloading
at once = a real tick spike. Fix: hoist markDirty out of the loop (breeder already does), hoist the roster copy out of
the sweep loop, optionally spread sweeps over a few ticks; later: cached per-entry NbtCompounds for the 3 big stores.

## Recommended batches

**Batch 1 — high impact, low risk (~1 session):**
1. Idle-off (S1): console-open gate for DsBridge serialize+poll; throttle closed-state pump (e.g. 1 slice/10 ticks after first paint); hoist `isModLoaded` to a static.
2. `pushIfChanged` server gate (S2) across all channels.
3. Harvest `markDirty` hoist + catch-up roster-copy hoist (S3).
4. Static augmenter catalog JSON (rebuild only `values{}`).
5. `DIM_KEY` lifecycle: synchronized WeakHashMap or SERVER_STOPPED clear (real SP leak: pins whole ServerWorld graphs across world switches).
6. DISCONNECT pruning for lastPrefetch/EggLog/GoalStore (Inbox stays — offline notes are the feature; TTL later).
7. Health-pass polish: countOf(), world-map hoist, skip when pasture unchanged.

**Batch 2 — medium:** cached per-entry NBT for PastureRegistry/BioBankStore/NotebookStore; lightweight prefetch (skip
monStats + snapshot capture, dedupe double rosterOf); DsBridge 1×/s cadence + per-channel dirty bits fed by apply*
booleans; client pasture caches → dim|pos keys + LRU cap; GpLog isEnabled() guard for per-proc lines; EggIngest single
decode; batch EggLog per brood.

**Batch 3 — cleanup:** React list keys (index→stable ids), useMemo on derived lists + DaemonGraph drag path (rAF
coalesce), bucketPairs hoist in catch-up, Analytics row-build off-thread + Notifier.observe cost check, BioBank
bulk-withdraw API (removeAt is O(n) per egg), coalesce post-action double pushes.

**Explicitly verified clean:** GpLog off-thread writer, EggQueue in-place encode, breeder CME guards, PastureKeeper off
the tick path, MCEF texture path (no per-frame upload), no JS timers/rAF loops in the React app.
