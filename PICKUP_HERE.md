# 🎯 PICKUP — mid-session handoff (2026-07-03, Deuce compacted while testing)

> **LIVE STATE:** jar **`7c3a46ef`** (commit `1425ee6`) deployed; **Deuce is IN GAME testing it right now.**
> His test loop: nether hop 3-5 min unpaused → portal back to the west pastures → BOTH catch-up pings should fire
> within ~1s of each other → open Notebook → **Inbox tab** (badge) → dismiss a note (✕) + clear-all → chat stays silent.
> When he says **"check"**: read the log — expect a `sweep` line with `sweeps:N>1` AND `brood` lines at ~the same
> second (the lineup fix), then confirm notes appeared/dismissed. **NEVER deploy while he's in game** (WSL `cp`
> through Windows' lock corrupts the RUNNING jar → ZipException hours later; see [[mod-deploy-workflow]]). Deploy
> only when he explicitly confirms quit-to-desktop.

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

## Open board (after his test verdict)
- **#37 Pasture health strip** — not-linked ⚠ + BioBank-full-for-species + queue-full/breeding-paused + no-Kernel/no-parents, in the config view + Pastures-tab markers (needs a small health payload server→client).
- **#34 EV allocator UI + #35 nature/ball picker** — the last feature gaps (backend honors EvSpread + nature/ball locks; no UI to set them). Recommended before release.
- **#6 tail** — CSV/HTML export button (DashboardExport exists server-side).
- **#7 release track** (+ #18 awareness book) — strip/gate QA commands, perf/log-level pass, icon/screenshots/listing, Modrinth/CF. "Features first, then release" agreed.
- Deferred: native-inventory viewport scaling; egg-catch-up ping when BioBank caps mid-catch-up (health strip covers it).

## Deuce's operating style (respect it)
Tight replies; he tests everything personally; deploy ONLY on explicit "I'm out/quit" confirmation; batch-QA; observability-first (every feature ships JSONL logging via GpLog — check logs before theorizing); no pay-to-win surfaces ever; he steers priorities — offer the board, let him pick.
