# 🧾 QA Pending — in-game checks Deuce needs to run

_A running tally of changes that need a real in-game pass. **Pure-logic cores are unit-tested (`./gradlew test`) and trusted — they do NOT appear here.** This list is only the Minecraft-bound / behavior-changing stuff a headless test can't prove. `glow QA_PENDING.md`._

## How this works
- Every time I make an **MC/adapter/UI** change that needs verifying, it gets a row here.
- **Status:** ☐ pending · ✅ verified · ❌ failed (I note what broke + fix it).
- After you QA something, tell me the result and I'll move it to **Verified** (or fix it).
- **Deployed?** = is it in the live test jar yet. Items marked *not deployed* need a `./gradlew build` + copy first (I'll do that whenever you're ready to test).
- Older, broad in-game tests live in `TEST_CHECKLIST.md`; this file is the focused "did the recent changes actually work" tally.

## ☐ Pending
| # | Change | Deployed? | How to verify | Status |
|---|--------|-----------|---------------|--------|
| Q1 | **Egg-queue**: breeding routes through a FIFO (buffers 5 tray + 24 queue, no silent discard past 5) | ❌ not yet | slot an upgrade, breed past 5 eggs → they keep coming; harvest the tray → it refills; relog → queue persists. `~/gp-logs/latest.log` shows `breeder brood`/`drain` | ☐ |
| Q2 | **ShinyOdds refactor** (`CobbreedingBridge` now delegates; behavior should be byte-identical) | ❌ not yet | breed with a shiny augment → `proc_shiny:true` still appears in `events.jsonl`; plain breeding unaffected (regression check) | ☐ |
| Q3 | **Daemon UI** rework (fit-viewport, scroll-zoom, **middle-drag=wire / side-drag=move**, top ports, text-stays-in-box, responsive header) | ✅ deployed | open Daemon: names stay inside boxes at any zoom; middle-drag wires, side-drag moves; PASTURE/KERNEL don't overlap | ☐ |
| Q4 | **BioBank Batch 1** (deposit/summary/persist/scatter) | ✅ deployed | `/give @s greenerpastures:biobank`, right-click eggs to bank (sneak=all), empty-hand=summary, relog persists, break scatters | ☐ |
| Q5 | **GpLog** observability pipe | ✅ deployed | `tail -F ~/gp-logs/latest.log` → `session_start` on load + `biobank.*` events when you use the BioBank | ☐ |
| Q6 | **Greener Kernel = 8 pairs** fills a 16-mon pasture | ✅ deployed | slot `breeding_upgrade_greener`, 16 mons → all 8 pairs breed | ☐ |
| Q7 | **Perf refactors** (per-dim registry, off-thread analytics, GUI caches — pure perf, behavior should be unchanged) | ❌ not yet | breeding still lays + queues + persists across relog; Arrange board still drags/drops/unpairs; highlighter still gold-glows shinies; Daemon still wires/pans/zooms; no red lines in the log | ☐ |
| Q8 | **Bug-hunt hardening batch** (see `BUG_HUNT.md`: breeder per-pasture crash-guard #1, pairings-packet bound/sanitize #2, container `isValid` guard #3, BioBank load-cap #4, log-writer reopen #9, menu slot-max #5, culler LRU #7, Daemon unpair cache, client-env gate #6 — all behavior should be **unchanged**, just safer) | ❌ not yet | normal breeding/loot/deposit/highlight all still work; **shift-click a stack of Pasture Upgrades onto the wand → only 1 lands in the slot**, the rest stay in your bag; deposit eggs past 256 in a BioBank → it refuses, break scatters ≤256; loot still sweeps into a plain chest; `~/gp-logs/latest.log` shows no new errors (and a `breeder pasture_skip` line only if a pasture genuinely faults) | ☐ |

> When you're free for a QA pass: tell me and I'll `build` + deploy the latest (folds in Q1/Q2), then you run down the list.

## ✅ Verified
_(moved here as you confirm them — date + note)_
