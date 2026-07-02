# 🎯 PICKUP — Greener Pastures (resume here)

> **Status (night, 2026-07-01): everything deployed + clean. Last jar `f55c4791` (valid, zip-OK).** MC crashed while Deuce was AFK — a **deploy-hygiene issue, NOT a code bug** (below). Deuce called it for the night; resumes in the morning. No pending build/deploy; nothing broken. On relaunch it loads fine.

## ⚠️ Deploy rule (cost 2 AFK crashes — see [[mod-deploy-workflow]])
NEVER `cp` the jar while MC is running. From WSL the copy writes through Windows' file lock and corrupts the *running* game's open jar → `ZipException: invalid LOC header` on a later **lazy** class-load (hours in) → "Exception ticking world" blaming Greener Pastures. The on-disk jar is fine; a fresh relaunch fixes it. **Only deploy when Deuce confirms MC is fully quit to desktop** — the Not-Enough-Crashes "keep playing" recovery screen keeps the JVM (and the jar handle) alive, so that does NOT count.

## Shipped this session (all deployed; awaiting Deuce's feel-feedback)
- **Daemon node-graph → Threads**: visual-scripting layer is a strip of named **breeding-line tabs** (`+ line`, rename, delete). Each line: assign ≤2 parents (roster chips) + wire their eggs → IV/EV/Nature/Shiny filters → →BioBank / →Data. Pan/zoom per line; server routes each egg through its parent's line (`GraphEval` by mon UUID); void log (`EggLogStrip`) under the graph. Detail: [[daemon-nodegraph-deckedout]].
- **Dashboard (live real data)**: eggs / shiny (+rate) / kept / voided / Data-earned cards, eggs-per-min sparkline, kept-vs-voided donut, eggs-by-Kernel bars — `dashboard` channel (NotebookDashboardS2C) off EggLog live counters.
- **Goals**: 🎯 panel atop the Dashboard — set a hunt (species / shiny / min perfect IVs / min IV total / count), live progress bar + reached ping; species validated vs the Cobblemon dex (fakes rejected) + autocompletes from BioBank. `goals` channel + NotebookGoalC2S.
- **Console polish**: pasture-open shows a "loading…" shell (no fake-empty flash) + a ~220 ms view-switch curtain (no leftover screen on air↔pasture).

## Open / next (Deuce steers)
- Batch-test the *feel*: Threads tabs + parent chips, live dashboard, goal hunts, the curtain.
- Known v1 limits (in [[daemon-nodegraph-deckedout]]): dashboard/goals are per server-session (in-memory); FILTER_EV likely trivial; one pipeline entry per line.
- **CSV/HTML export** (`DashboardExport` exists) not wired to a button — quick add if wanted (task #6 tail).
- Optional: atomic-rename deploy (write-temp → `mv`) as belt-and-suspenders vs the corruption issue.

## Build / deploy (key facts)
- **UI:** `cd greener-pastures-ui && npm run build` (single-file into the jar resources).
- **Java:** `JAVA_HOME=/home/donaldgalliano/jdks/jdk-21.0.11+10 /home/donaldgalliano/pokemon-prediction/greener-pastures/gradlew -p /home/donaldgalliano/pokemon-prediction/greener-pastures build` (Bash `dangerouslyDisableSandbox`). Confirm the real `BUILD SUCCESSFUL` (a grep pipe's exit 0 is NOT gradle's).
- **Deploy (MC fully quit ONLY):** cp jar → `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/mods/greenerpastures-0.1.0.jar`, then `zipfile.testzip()`.
- Commit trailer: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` + `Claude-Session: https://claude.ai/code/session_017Dq3vi9HWWc9bYUDAaUSYs`
