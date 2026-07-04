# 🎯 PICKUP — session handoff (2026-07-03, late)

> **LIVE STATE:** deployed jar is **`f1769a7f`** (features batch); jars **`08a14db9`** (perf R3) and
> **`c19c74b6`** (release batch, commit `05d7a6d`) are built + committed but **NOT deployed** — Deuce was
> couch-steering ("remote control work"), batching code with **deferred QA**. Next deploy = `c19c74b6`
> (it contains everything), ONLY on his explicit quit-to-desktop confirm.
> ⚠️ **His instance MUST add `-Dgreenerpastures.qa=true` to JVM args before testing** (QA commands +
> DEBUG logging are now gated behind it — release builds ship clean). It's the first line of the QA section.
> **Pending QA: Q39–Q61** (three stacked batches: features / perf+profiler / release+GPU-economy+guide).
> **NEVER deploy while he's in game** (WSL `cp` corrupts the RUNNING jar; see [[mod-deploy-workflow]]).

## His world (for log-reading)
- World `New Worlddasdasdadsa`, spawn `-400,-336`. Pastures: **spawn farm** `-394,69,-290` (16 Tentacool/Cruel/Frillish, Greener 6.00%), `-400,69,-288` (2 Eevee, Netherite 5.50%), `-398,69,-288` (2 Drilbur, Copper 3.50%); **west** `-902,69,-297` (6 mons, Greener) + `-923,63,-453` (2 mons, Diamond 5.00%); **nether** `-165,68,-19`.
- `renderDistance:36` → chunks stay loaded within **576 blocks** — the **nether hop** is the reliable unload lever for catch-up tests.
- Logs: `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/gp-logs/latest.log` (JSONL). Key events: `sweep` (`sweeps:N`, `proc_pct`, `stored`, `items`), `proc` (species + items or `dry`), `pull`/`pull_full` (`n`≤`capacity`), `brood`, `client_connect`, `prefetch`, `gap_applied`.

## Shipped TODAY (all in 7c3a46ef, mostly field-verified)
1. **Drop-rate QA kit** — doubled kernel rates (`BASE_DROP_RATE` 25→**50** centipercent/tier → copper +0.50%…greener +3.00%; droprate-boost augment 100→**200**; `BreedingTierTest` updated). `/gp harvest interval <s>|default` (op-2, reset on server start). Audit logging: per-pasture `sweep` line every sweep + per-proc `proc` line (`dry` = table missed). **Statistically verified live**: 3 kernels within ±1σ over 65 min (570e/558o · 41e/47o · 65e/58o). Drilbur "100% dirt" has `quantityRange 0-1` → ~50% dry (confirmed 0.49 dirt/proc); Tentacool 1-3 can't dry (0/489). Cobblemon's multi-draw gives low-% entries extra tries when the amount budget is big (Drop Yield quietly boosts rares).
2. **Balance anchor (memory: [[drop-rate-balance-anchor]])** — full-dex sim (1,025 species, decompiled `DropTable.getDrops` algorithm; plain-int amount = N..N fixed). Worst 16-mon farms: 199 raw iron/hr (Steelix), **14.4 diamonds/hr (Sableye)** — sane vs vanilla. **No species drops e-gapples/netherite/totems.** **Deuce's standing rule: NO drop-rate config ever** (admins would zero it + sell paid ranks). Baked constants only.
3. **Catch-up system (task #38 done)** — `PastureData.lastHarvestTick` + `lastBreedTick` (NBT). Chunk reloads roll missed sweeps/broods (12h cap; eggs run the FULL pipeline: shiny proc → Daemon graph → BioBank/void log → goals; sterile-pasture early-out; tether drain × productive). **Online gate**: `notebook/OfflineProgress` (PersistentState) stamps logout world-time, join shifts owned pastures' anchors past the offline gap (credits exactly online-away time; clamps if chunk stayed loaded). Claim anchors both clocks. **Verified: 13-min trip → `sweeps:13` exact + 4 broods, clean reset to sweeps:1.**
4. **Instant harvest catch-up** — harvest now schedules **per pasture** (20-tick scan, sweep when that pasture's interval is due) instead of a global minute-modulo → drop catch-up fires ~instantly on reload, lined up with eggs. (This changed sweep cadence semantics: per-pasture clocks, same rate.)
5. **Pull safety** — `pull()` counts MAIN-inventory capacity itself and **places stacks into slots manually** (`insertStack` is mixin-hijackable — ~3,072 ink sacs vanished pre-fix, cause unproven but the class of bug is closed). Zero capacity → chat refusal + `pull_full` log (**verified live**: soft_sand refusal at 17:01, then n=6/cap=128 after making room). `withdrawEgg` same. UI: Harvester cells grey out (`cell-full`) + "⚠ inventory full" banner.
6. **Inbox (new)** — `notify/Inbox` (per-player, cap 50, session-scoped, collects while owner offline) + `NotebookNotifsS2C` + `notifications` bridge channel + `DISMISS_NOTE` action (id or "all") + React **Inbox tab** (unread badge, icon+text+time-ago, per-note ✕, clear-all). Catch-up pings live THERE now — **no more chat messages**.
7. **Session hygiene** (fixed his new-world-shows-old-stats bug) — SERVER_STARTED clears EggLog/GoalStore/prefetch cooldowns/QA overrides/Inbox; client DISCONNECT clears NotebookState + pasture caches + re-baselines DsBridge.
8. **Seamless console** (earlier today) — root cause of 2-3s view swaps: MCEF pumps `N_DoMessageLoopWork()` **once per frame** → starved Chromium. `NotebookBrowserScreen.pump(slices,budget)`: 10/2.5ms per frame open, 4/1ms per tick background, 48/8ms burst during transitions. + pasture-config **prefetch** (join + 1×/min poll, cap 16, focus-aware appliers so background pushes never hijack the open view) + stale-while-revalidate cache → basically every open instant. Native "loading pasture…" overlay = rare fallback (lifts on React `PASTURE_READY`).
9. **Graph/UX batch** — parent inspector (IVs/nature/gender/shiny/OT; right-click chips or click nodes), gender ♂/♀/⚲ on chips, pair validation (♂+♀ or exactly one Ditto), Masuda/Crystal badge (vs server `getShinyMethod()`), draggable pop-ups (`usePanelDrag`), wheel-zoom no longer scrolls the pane (native non-passive listener; **TDZ lesson**: deps `[doc.active]` not `[active]` — a mid-component const in a hook dep = ReferenceError = black screen), palette nodes cascade (no more stacking), **pair-as-one** (wiring one parent auto-wires the other; dashed gold pair link).
10. **Dashboard live + Goals** — real session stats (EggLog counters) + 🎯 hunts (dex-validated species + autocomplete from BioBank). **Threads** = named breeding-line tabs (earlier).

## Environment (don't re-derive)
- **UI build:** `cd greener-pastures-ui && npm run build` (single-file → jar resources). **Java:** `JAVA_HOME=/home/donaldgalliano/jdks/jdk-21.0.11+10 greener-pastures/gradlew -p greener-pastures build` (sandbox off). **Read the REAL gradle output** — a grep pipe's exit 0 is NOT gradle's (bit us again today: committed+deployed on a red build; the jar happened to be fine, test-only failure, amended after).
- **Deploy (only on confirmed quit):** `cp` jar → `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/mods/greenerpastures-0.1.0.jar` + md5 + `zipfile.testzip()`.
- Commit trailer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` + `Claude-Session: https://claude.ai/code/session_017Dq3vi9HWWc9bYUDAaUSYs`.
- Jar decompile workflow (Cobblemon/MCEF internals): python zipfile extract + `~/cfr.jar`.

## Open board
- **QA the 2026-07-03 batch (Q39–Q49)** — that's the immediate next step when he launches.
- **#7 release track** (+ #18 awareness book) — strip/gate QA commands, perf/log-level pass, icon/screenshots/listing, Modrinth/CF. "Features first, then release" agreed — the feature board is now EMPTY except release.
- Deferred: native-inventory viewport scaling; Augmenter GPU/Data cost pass (installs are slot-gated only, §7.5).

## How #34/#35/#37 are built (for debugging)
- **Pure cores** (unit-tested): `notebook/PastureHealth.evaluate(linked, hasKernel, monCount(-1=unknown), queueFull, fullSpecies)` → flags; `notebook/AugmentArg.parse("TYPE" | "TYPE:idx" | "EV:6csv")` → null on malformed.
- **Transport** (tuple-6 dodges): `NotebookPastureExtraS2C(pos, json{health,kernel})` rides with every pushPastureConfig (pos-keyed focus-aware client cache, like config/graph); `NotebookAugmenterMetaS2C(json{values,natures,balls})` rides with pushAugmenter; `NotebookPasturesS2C` grew `healthJson` ({"dim|pos":"id,id"}).
- **AugmentType** gained NATURE/BALL (parameterized selectors — level = catalog index), ABILITY, EGG_MOVES; **EV is now the parameterized EV Primer** (installedOn = EV_SPREAD component present; the v1 +20-blanket value was dead code at the breeder and is retired). Re-pick in place = no new slot; `applyAugment` validates catalog ranges server-side.
- **React**: `NaturePicker`/`BallPicker`/`EvAllocator` are draggable `dcfg` pop-ups in the Augmenter tab (PICK…/EDIT buttons); catalogs come from the SERVER meta (never hardcode ids — only the NATURE_FX display hints are client-side). Fixed a latent dead-APPLY bug (`gpu >= a.gpuCost` with gpuCost never sent → always disabled; now `?? 0`).

## Deuce's operating style (respect it)
Tight replies; he tests everything personally; deploy ONLY on explicit "I'm out/quit" confirmation; batch-QA; observability-first (every feature ships JSONL logging via GpLog — check logs before theorizing); no pay-to-win surfaces ever; he steers priorities — offer the board, let him pick.
