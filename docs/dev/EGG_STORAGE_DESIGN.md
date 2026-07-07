# 🥚 Egg Storage & FIFO Queue — Design + Verified Facts

_Greener Pastures · captured **2026-06-26** (power-work checkpoint). The runtime/storage layer for the dark-economy egg pipeline. Builds on the dark-economy turn (memory: `greener-pastures-dark-economy`) + `BETTER_PASTURE_SPEC.md`. `glow EGG_STORAGE_DESIGN.md`._

## Why this doc exists
We decompiled the Cobbreeding + Cobblemon jars to find the **real** egg/tether limits (expensive to re-derive — needed CFR), then decided the storage architecture. This file captures both so a fresh session never has to re-investigate.

## ✅ Verified facts (from jar decompile — treat as ground truth)
| Limit | Value | Source |
|---|---|---|
| **Eggs held in a pasture** | **5** (default) | Cobbreeding `Config.pastureInventorySize` (floor 1, configurable). Stored as `DefaultedList<ItemStack> eggs = DefaultedList.ofSize(n, ItemStack.EMPTY)`, created in Cobbreeding's `PokemonPastureBlockEntityMixin`, held in `PastureBreedingData` keyed by BlockPos in a static `registry` map. |
| **Pasture tether capacity** | **16 Pokémon** (default) | Cobblemon `CobblemonConfig.defaultPasturedPokemonLimit`; `PokemonPastureBlockEntity.getMaxTethered()` returns it verbatim. **We never override it.** |
| **Max breeding pairs** | **8** | `floor(16 / 2)`. Hard ceiling for ALL Kernel tiers. |
| **Our FIFO queue cap** | **min/default 24** | 8 pairs × 3 breeding cycles. Configurable upward. |

**Persistence:** Cobbreeding's mixin injects `writeNbt`/`readNbt` into the pasture block entity, so the 5-slot egg list rides in the block entity NBT (saved with the chunk). The static `registry` map is just the live index.

**Jar paths + recipe (to pull more):**
- Cobbreeding: `…/.gradle/loom-cache/remapped_mods/…/Cobbreeding-fabric-2.2.1-c34567d7e536.jar`
- Cobblemon: `…/.gradle/loom-cache/remapped_mods/…/Cobblemon-fabric-1.7.3+1.21.1-f7c25955176b.jar`
- Decompile: `javap -c -p -classpath <jar> <class>` for quick signatures, or extract the `.class` and run CFR (`~/cfr.jar`) with `--extraclasspath <jar>`. JDK: `~/jdks/jdk-21.0.11+10`.

## Current behavior (today, before the queue)
- `MultiPairBreeder` lays one egg per configured pair per interval via `CobbreedingBridge.addEgg` → fills the first empty tray slot, **`break`s when full** (`MultiPairBreeder.java:86`).
- ⇒ **past 5 unharvested eggs, breeding output is silently discarded.**
- Unloaded/away: the chunk doesn't tick → no eggs while away; we also suppress Cobbreeding's native catch-up ⇒ **no offline progress** right now.

## ✅ DECIDED architecture (2026-06-26): FIFO queue in front of the tray
```
 pairs breed  →  FILTER ──pass──►  our FIFO queue (PastureData, persisted, cap ≥24)
  (≤8 / cycle)     │                      │
                   │                      ▼  drain: while tray has an empty slot, pop queue → addEgg()
                   │              ┌──────────────────────┐
                   │              │ Cobbreeding 5-slot   │ ──► collector / hopper / player pulls
                   │              │ tray (the visible    │
                   │              │ output buffer)       │
                   │              └──────────────────────┘
                   └──fail──►  render pile / VOID  ──►  dark-economy Data fuel
```
**Decisions:**
- **FIFO always**, unless a **Soul Tether** (renamed augment, now on a **Daemon item**) overrides ordering (e.g. shinies jump the line). Soul Tether mechanics are being reworked in `~/pokemonthink` — **DO NOT build Soul Tether logic here until that spec arrives.**
- **Queue cap configurable, min 24** (= 3 breeding cycles for a full 8-pair pasture; would be 36 only if max pairs were 12 — it is NOT).
- Queue lives in **our** `PastureData` (already persisted, no 5-slot limit), NOT Cobbreeding's list. The 5-slot tray stays the visible output buffer a collector drains.
- **Everything is built around the 16-mon pasture and does NOT grow it.** `BreedingTier.GREENER` trimmed **12 → 8 pairs** (now exactly fills the pasture). No tier may exceed 8.

## Away-from-chunk plan (offline progress)
Chunks don't tick while unloaded ⇒ "away progress" = **lazy catch-up on return**: persist `lastBred` in `PastureData`; on reload/first scan, compute elapsed intervals → generate N eggs → run through the filter → enqueue (bounded by cap). Mirrors Cobbreeding's own catch-up loop but feeds OUR queue; keep suppressing its native version so it doesn't double-dip.

## Node-graph mapping (already designed visually)
Daemon node kinds map 1:1: **UNIT** = pair (producer) · **FILTER** = IV/shiny filter · **COLLECTION** = the keep queue's backing store · **VOID** = the render pile's backing store · **AUGMENT** = now the Soul Tether. So the queue is just the runtime for the COLLECTION node already in `DaemonView`.

## 🔜 To build (only when the Soul Tether / dark-economy spec lands — NOT before)
- [ ] Add `eggQueue` + `lastBred` to `PastureData` (persisted; FIFO; capped).
- [ ] `MultiPairBreeder`: breed → filter → **enqueue** (instead of `addEgg` directly).
- [ ] Drain step each scan: while tray has empty slots, pop queue → `addEgg`.
- [ ] Filter fork: failing eggs → render/VOID ledger (dark-economy intake; folds in the #17 culler rework).
- [ ] Lazy away-catch-up using `lastBred`.
- [ ] Config: queue cap (min 24). Optional Soul Tether ordering override.

## This session's code changes (already on disk)
- `greener-pastures/src/main/java/com/greenerpastures/pasture/breeding/BreedingTier.java` — `GREENER` 12→8 pairs + hard-ceiling javadoc.
- `ITEM_REGISTRY.md` — greener row 12→8 pairs.
- `cobblemon-drops-ref/` — reference bundle for the other Claude's drop-modification plan (see its `DROPS_REFERENCE.md`, `all-species-drops.json`, `species-samples/`, `distinct-drop-items.txt`).
