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
