# 🐛 Bug & Robustness Hunt — Greener Pastures (2026-06-28)

_Two background agents swept the whole mod after the perf pass (one **regression-reviewed** the perf refactors, one **hunted bugs/edge-cases/robustness**), analysis-only. Every finding was re-verified against the real source before any change. Goal — same as the perf pass: **don't brick people's games.** `glow BUG_HUNT.md`._

## TL;DR
- **No CRITICAL or HIGH regression** from the perf refactors. The registry restructure, off-thread serialization, in-place egg encode, and the Daemon/Arrange caches all preserve behavior; the only regression was one **LOW** stale-frame in the Daemon cache (fixed).
- The bug hunt found **one real server-crash path** (the breeding tick had no per-pasture guard), **two save-corruption risks** (container insert without `isValid`, BioBank cap not enforced on load), and **one C2S trust hole** (unbounded pairings packet). All fixed.
- It also **cleared several false positives** (the BioBank "dupe", any NBT key mismatch, the mod-coupling fail-safety) — recorded below so they're not re-investigated.
- All fixes are **committed, NOT deployed** (one consolidated QA item, **Q8**). Pure-logic guards (#8, #13) are unit-tested and trusted.

---

## 🔴 CRITICAL — fixed
**#1 · Unguarded Cobblemon calls on the breeding tick could crash the server world-tick** · `pasture/breeding/MultiPairBreeder.java:49-77`
`onWorldTick` iterated every managed pasture with **no try/catch** around the per-pasture work. `pasture.getTetheredPokemon()` and `addEgg→eggsAt` are unguarded Cobblemon/registry calls — one tethering whose Pokémon failed to deserialize (or any API drift) would propagate out of the tick callback and **take the whole world down**, every load.
**Fix ✅** Wrapped the per-pasture loop body in `try { … } catch (Throwable t) { GpLog.w("breeder","pasture_skip", …); }` — one bad pasture is skipped and logged, never fatal. (The bridge already self-disables on egg-build failure; this guards the rest of the loop.)

---

## 🟠 HIGH — fixed
- **#2 · `SavePairingsPayload` pairings map was unbounded** · `pasture/breeding/net/SavePairingsPayload.java:27` (+`PastureNet.onPairings`). The 3-arg `PacketCodecs.map` has no size limit in 1.21.1; a crafted client packet could decode millions of `(uuid→bucket)` entries straight into the persisted pasture record (decode spike + multi-MB save bloat forever). **Fix ✅** 4-arg size-bounded codec (`MAX_PAIRINGS=256`, far above any real ~16-mon roster) **+** server-side `sanitize()` in `onPairings` that drops nulls / buckets outside `[1,8]` and re-caps size. Defense in depth at the trust boundary.
- **#3 · `PastureCollector.insert` wrote to neighbor inventories without `isValid`** · `pasture/keeper/PastureCollector.java:58-84` (live via the pasture mixin). `findInventory` returns *any* `Inventory` neighbor, including sided/filtered modded containers; writing an item their logic assumes can't be present can corrupt their save or lose items. **Fix ✅** Guarded both the merge (`&& inv.isValid(s, slot)`) and the fill-empty (`if (!inv.isValid(s, put)) continue;`) paths.
- **#4 · BioBank cap was enforced only at deposit, not on load** · `biobank/BioBankData.java` + `BioBankBlock.depositOne`. A migrated/hand-edited save loaded **every** stored egg with no clamp, then break-scatter spewed the entire unbounded pile as loose item entities (the exact spam `DEFAULT_CAP=256` exists to prevent). **Fix ✅** The cap now lives inside `BioBankData.add()` as the single source of truth (returns `false` when full); `fromNbt` clamps on load and logs `biobank load_over_cap`; `depositOne` relies on `add()`'s return.

---

## 🟡 MEDIUM — fixed
- **#9 · Log writer threads died permanently on the first `IOException`** · `analytics/EventLog.java` + `core/GpLog.java`. One mid-session disk error (full / handle revoked) ended the writer thread; logging then went dark forever with only a close-time warning. **Fix ✅** Both `drainLoop`s now catch `IOException` per-write, close + null the writer, and **reopen on the next row** — a transient error no longer kills the pipe.
- **#5 · `PastureMenu.quickMove` could overstack the single-item upgrade slots** · `pasture/breeding/gui/PastureMenu.java`. Vanilla `insertItem` ignores `getMaxItemCount()`, so shift-clicking a stack of (maxCount-16) upgrades could land >1 in a "max 1" slot, locking the extras. **Fix ✅** New `insertIntoUpgrades()` honors each slot's `getMaxItemCount()` (and `canInsert`) — shift-click now lands exactly one, the rest stay in inventory.
- **#6 · `EggReader.read()`/`species()` could reach client-only `MinecraftClient` on a server** · `egg/oracle/cull/EggReader.java`. `shinyByName` dereferences `MinecraftClient`; `species()` is called server-side from BioBank, so a future server-side `read()` call would `NoClassDefFoundError`. **Fix ✅** `shinyByName` returns early unless `FabricLoader…getEnvironmentType()==CLIENT`, before any `MinecraftClient` reference loads.
- **#7 · `EggCuller.CACHE` was an unsynchronized `IdentityHashMap` with a full-clear** · `egg/oracle/cull/EggCuller.java`. Latent (render-thread only), but inconsistent with the detector. **Fix ✅** Swapped to the same `Collections.synchronizedMap(LinkedHashMap access-order LRU)`, `CACHE_MAX=4096` — gradual eviction, no recompute storm.

## 🟢 LOW — fixed
- **Regression (Daemon model cache)** · `client/ui/DaemonController.java:190`. Right-click-unpair mutated state but didn't invalidate the model cache, so the chip rendered as *still paired* until the button released (never persisted, never mis-saved). **Fix ✅** `unpair()` now sets `modelDirty = true`, mirroring `flash`/`pair`.
- **#8 · Economy overflow** · `economy/DataAccount.java` + `RenderValuation.java` (dead code today; the currency, about to be wired). `credit`'s `balance += amount` could wrap negative, breaking "never negative". **Fix ✅** Saturating add (caps at `Long.MAX_VALUE`); `RenderValuation` floors a NaN/sub-1 multiplier to 1× (double→long already saturates per JLS 5.1.3). **Unit-tested.**
- **#10 · `EventLog.dropped` non-atomic** → `AtomicLong` (multi-producer safe). **Fix ✅**
- **#11 · `Analytics.record` reserved keys could be clobbered** by a caller field of the same name (would silently drop the event from dashboards keyed on `type`). **Fix ✅** Reserved stamps (`type/t/gameTime/dimension`) now win; caller fields can't overwrite them.
- **#13 · `ShinyOdds.effectiveOdds` had no `baseRate<=0`/NaN guard** · a server setting `shinyRate=0` ("no shinies") made `shinyProbability` clamp to 1.0 → **every firing proc a guaranteed shiny** (opposite of intent, and a breach of the gated-shiny invariant). **Fix ✅** `baseRate<=0`/NaN now returns `POSITIVE_INFINITY` (never). **Unit-tested.**

## ⏸️ Deferred
- **#12 · PastureKeeper suppression is in-memory only** (re-enables wandering on restart). **Deferred** — that subsystem is slated for retirement (its loot role → BioBank); not worth a `PersistentState` investment now. Revisit only if it survives the cut.

---

## ✅ Verified NON-issues (don't re-investigate)
- **BioBank deposit is NOT a dupe** — a sub-audit's "CRITICAL dupe" was a false positive: `depositOne` banks `copyWithCount(1)` and the caller `decrement(1)`s — net-neutral; bank-full leaves the remainder live; the slot is cleared only after it's drained.
- **No NBT key mismatch** in live persistence — `PastureData`, `BioBankData`, and the registry's flat `dim|pos` keys round-trip key-for-key; malformed entries are caught and dropped, not fatal.
- **Mod-coupling is fail-safe** — `CobbreedingBridge` guards every API call and self-disables on first egg-build failure; `MultiPairBreeder` re-checks `isAvailable()` every tick; init order is correct. (#1 was the one gap, now closed.)
- **`EggReader.species()` never returns null** ("unknown" fallback) → no BioBank `NbtCompound.put(null,…)` crash.
- **Dead code:** `economy/*` is unreferenced (so #8 is latent — it's the currency, about to be wired). `CatchUp`/`lastBred` (offline-egg catch-up) was never wired and was **removed 2026-06-28** (Deuce's call) — pastures only breed in loaded chunks.
- **Clean:** `OddsEngine`, `ShinyEggDetector`, `IvFilter`, `DashboardStats`/`Export`, `EventReader`, the pasture mixin.

## 🛠️ Fix status — commits (2026-06-28, on `f882fba`)
All 11 actioned findings + the regression LOW are **fixed, test-backed (92 green), build-clean, committed, NOT deployed.** Tracked for in-game verification as **Q8** in `QA_PENDING.md`. Pure-logic (#8, #13) excluded from QA — proven by unit tests.

---

# 🌊 Wave 2 — three more agents (2026-06-28)

After Wave 1, three more analysis-only agents ran: **(a)** an adversarial review of the Wave-1 *fixes themselves*, **(b)** a **2nd perf pass** with an MP/dedicated-server-scale lens, **(c)** an adversarial **SP-vs-MP** edge-case + ideas pass. Headline: **side-safety came back CLEAN** (a dedicated server loads no client class — verified file-by-file), and the Wave-1 fixes verified correct against real 1.21.1 bytecode **except two defects in the new log-reopen code**, both fixed.

## Fixed (commits on `aeeca44` → `78175f0`)
- **[MED] Log busy-spin on a *persistent* disk outage** — the #9 reopen loop had no backoff; `poll(1s)` returns instantly with a backlog, so a sustained outage would pin a core + flood SLF4J. Both drain loops now `Thread.sleep(1000)` after a failed reopen.
- **[LOW] GpLog drop-count lost on reopen failure** — `getAndSet(0)` cleared it before the write; now peek-then-`addAndGet(-lost)` only after a durable flush, so the gap-marker survives the outage.
- **[PERF N2] `refreshHasEgg`** — replaced `eggs.stream().anyMatch` with an indexed loop (zero-alloc on the one hot server path).
- **[HIGH H1] Pasture break = item loss + record leak** — breaking/pistoning a pasture destroyed its slotted upgrade + augmented Kernels + queued eggs, and orphaned the `PastureData` (re-saved + rescanned forever). Added `PlayerBlockBreakEvents.AFTER`: scatter the upgrades + eggs, then `remove()` the record. `BetterPasture.registerBreakCleanup`.
- **[MED H2] Phantom records via packets** — `SaveName`/`SavePairings` checked reach but not block type → a client could mint `PastureData` at any nearby pos. Both handlers now require a real `PokemonPastureBlockEntity` at pos.
- **[MED H3] EventLog Gson-death** — the writer caught only `IOException`; a non-finite double (likely once the economy feeds doubles) would kill it permanently. `safeJson()` turns a bad row into a valid-JSON breadcrumb.
- **[LOW I1] `Analytics.init` idempotent** — a double-init would have raced two writer threads on one file.
- **[N1 decision] Pasture loot-sweep CUT** — `PastureCollector` deleted, mixin hook stripped (no-wander stays). Replaced later by the %-chance loot block.

## Deferred / tracked (not done — by choice)
- **[MED M1] Menus never `canUse`-expire** — `CompilerMenu`/`PastureMenu` `return true`. Largely mitigated by H2 (packets now reject a gone block) + server-authoritative menu logic; proper fix is a real `canUse` (reach + block-type). **Backlog.**
- **[LOW N3]** `Analytics.record` row-assembly still on the tick thread (residual of perf H3) — enqueue the `Event`, build the map on the writer thread. **Backlog.**
- **[LOW N4]** `BioBankBlock.depositAll` re-fetches the store + reflects species per egg; hoist out of the loop, cache species per stack. One-shot action, bounded by the 256 cap. **Backlog.**
- **[IDEA I2]** capture `Finder.target` once (it's `volatile`; not a live race today). **[IDEA I3]** validate/coerce `Event.put` values at the boundary (pairs with H3). **Backlog.**
- **Perf H2/H5** — re-confirmed **stay deferred** at MP scale (H2 is off-tick autosave only, H6 de-fanged it; H5 is one client's local frame, MP doesn't multiply it).

## Verified NON-issues (Wave 2 — don't re-chase)
- **Side-safety across the whole tree** — every `net.minecraft.client.*` importer is a genuine client-only class registered only from the client entrypoint; `studio/*` is a desktop dev tool never classloaded by the mod. The biggest SP-vs-MP risk is clean.
- **Compiler compile path** — server-authoritative, augment consumed in-place, inputs returned `onClosed` → the sub-agents' two "CRITICAL dupe" flags were **false positives**.
- **`PastureBreedingData.registry` plain HashMap** — server world-ticks are sequential on one thread → no race. `Registries.ITEM.getId()`/`stack.getName()` null fears — false positives in 1.21.1.

**Status:** all Wave-2 fixes **build-clean, 85 tests green, committed, NOT deployed.** New MC-bound items tracked as **Q9** in `QA_PENDING.md`.

---

# 🌊 Wave 3 — Soul-Tether swarm (2026-06-28)

Three agents over the new dark-economy + tether code (perf · correctness · design-conformance + SP/MP side-safety). **Side-safety came back CLEAN again** — no client class reaches a dedicated server, the `TETHER`/`AUGMENTS` components register common-side, `EggReader` stays env-guarded. No CRITICAL.

## Fixed (commit `f33656f`)
- **[HIGH] Tether read from a downgrade-hidden slot** — `slottedTethers()` iterated *all* functional slots; a tether in a slot a Kernel downgrade hid still amplified + **drained Data from an inaccessible slot** (silent loss). Now gated by `tier().slots`.
- **[HIGH · design] Balance constant inverted** — `BASE_DATA_PER_EGG=10` let a trophy pasture self-fund its tethers (8 eggs × 10 = 80 vs a tier-III quality tether's 24/cycle burn), breaking the design's one hard rule. Lowered to **2** (tunable; pin in QA).
- **[HIGH · perf] Renderer double-decrypt** — culling did *two* reflective Cobbreeding decrypts per egg at 1 Hz (`read` + `species`); species is unused by the cull (`ValueRule` keys on shiny/IV) → dropped the 2nd.
- **[MED] Stale owner** — `PastureMenu.onClosed` now sets the operator symmetrically (cleared when no tether remains) — no stale billing / silent ownership grief.
- **[MED · design] Enrichment dead stat** — wired the base Enrichment augment into the Renderer's valuation so a compiled Enrichment isn't silently inert (tether-amplified enrichment + its drain is a later step).
- **[LOW] Augments save codec** bounded in the ctor (the packet codec was already capped).

## Deferred (by-design / immeasurable)
- Shared-owner multi-pasture drain is order-dependent (unordered map) but **safe** — degrades to free base, no double-spend. Document, don't fix.
- `DataStore.get` twice/cycle (cached lookup), `AugmentFunction.byId` linear scan, per-cycle EnumMap allocs — all behind the **~2.5–20 min breed gate**; immeasurable.
- **Tether-amplified Enrichment + its drain** — the income (Renderer, 1 Hz) and drain (breeder, per breed cycle) run on different clocks; reconcile before amplifying enrichment.

## Verified CLEAN (don't re-chase)
SACRED shiny holds through the tether path (a Shiny Tether amplifies only the bred reroll, never wild — traced); base=free / rented / starved→base-never-pauses; offline-cut confirmed (nothing drains or amplifies offline); side-safety; the economy cores (single-pass, bounded, saturating); packet codecs bounded.

**Status:** all Wave-3 fixes **build-clean, 110 tests green, committed, NOT deployed.** Folds into Q11/Q12 + new **Q13** in `QA_PENDING.md`.
