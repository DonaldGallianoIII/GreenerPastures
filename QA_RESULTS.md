# 🐛 QA Results — Greener Pastures

_Running log of Deuce's in-game QA findings. Companion to `QA_PENDING.md` (the checklist) + `QA_SETUP.md` (the command
kit). I log + **commit each finding as it comes in**; fixes are **batched** for after the pass (or pulled forward if
a finding is blocking). `glow QA_RESULTS.md`._

## Legend
- **Severity:** 🔴 blocker · 🟠 major · 🟡 minor · 🔵 polish/nit
- **Status:** 🐛 open · 🔧 fixing · ✅ fixed _(commit)_ · 🚫 wontfix / expected · ❓ needs-info / repro
- Each finding links its `QA_PENDING` row (Q#) where one applies. When a Q# fails I also flip it to ❌ there.

## How to report (freeform is fine — I'll structure it)
Just tell me, however's easy mid-test:
1. **What** you were testing (a Q# or just the feature name)
2. **What you did** (rough repro steps)
3. **Expected vs what actually happened**
4. _(if handy)_ a line from `~/gp-logs/latest.log`, or a crash/stacktrace

I'll fill in the rest, assign an ID + severity, and commit it.

---

## Session — 2026-06-30 · jar `f2b6662`
**Env:** *Greener Pastures Test* instance, MC 1.21.1, full restart. **Load: ✅ clean** — both entrypoints init,
ghost-pasture `@Redirect` resolved, all 15 buffs registered, GpLog live, no errors.

### 🐛 Findings — index
| ID | Sev | Q# | Feature | Symptom | Status |
|----|-----|-----|---------|---------|--------|
| BUG-001 | 🟠 | Q16 | Kernel base drop-rate | Flat +0.25% on every tier; never scales (copper = iron = gold = diamond) | 🐛 open |
| BUG-002 | 🟠 | Q21 | EV augment / Soul Tether | Flat +N EV on ALL 6 stats (blanket); wants per-stat allocation + a Compiler UI | 🐛 open |

### ✅ Verified working
| Q# | Feature | Note |
|----|---------|------|
| — | Mod load | Clean load: common+client init, ghost-pasture mixin resolved, 15 buffs registered, no red lines |

---

## Detail log
_(Per-finding detail — repro, expected/actual, log evidence, root-cause + fix — appended as they come.)_

### BUG-001 · 🟠 MAJOR · Q16 · Kernel base drop-rate doesn't scale per tier
- **Repro:** hover any Pasture Upgrade (copper / iron / gold / diamond / netherite / greener) → the `⛏ drop rate` line reads the same on all.
- **Expected (Deuce):** base ramps +0.25%/tier — copper +0.25%, iron +0.50%, gold +0.75%, diamond +1.00%, … (tier index × 0.25%).
- **Actual:** every tier is a flat +0.25%; never increments.
- **Root cause — CONFIRMED:** `BetterPasture.registerItems()` builds **one** shared `kernelBase = Augments.NONE.withLevel(DROP_RATE, BASE_DROP_RATE)` (`BASE_DROP_RATE = 25` centipercent) and assigns that **same flat component to every tier** in the loop — tier is never factored in. (`BetterPasture.java:66`; const `BreedingUpgradeItem.java:23`.) Deuce's `tier_count × 0.25 → 0.25` hunch is exactly right.
- **Fix (ready, batched):** compute the base *inside* the loop — `BASE_DROP_RATE * tierLevel(tier)` (copper = 1…) — so each item gets its own component. Tooltip + Harvester already read the component, so both pick it up free. One-line change + a headless test asserting the per-tier ramp. _(Note: Kernels already in-world keep the old baked value; re-`/give` after the fix.)_
- **Open Q:** does the ramp continue past diamond (netherite +1.25%, greener +1.50%) or cap at diamond? Deuce listed through diamond.
- **Status:** 🐛 open

### BUG-002 · 🟠 MAJOR (redesign) · Q21 · EV augment is a blanket "+N to all stats"; wants per-stat allocation
- **Problem:** the EV augment / EV Soul Tether pre-sets the **same** EV value on **all six** stats (a blanket head-start). Nonsensical for build identity — a targeted spread *is* the point of EVs.
- **Root cause — CONFIRMED (this is the Q21 placeholder semantic being called in):** `EffectiveAugments.evFloorPerStat()` returns one scalar and `CobbreedingBridge.applyEvFloor(eggData, perStat)` writes it onto **every** permanent stat (`CobbreedingBridge.java:307`; tooltip literally `✦ +N EV on every stat`, `AugmentType.java:85`). Q21 itself flagged: "EV = flat per-stat floor; tune the semantics if you want a targeted spread later." Now we tune it.
- **Proposed design (Deuce) — Compiler allocation screen (anvil-preview / RimWorld-trade layout):**
  - **LEFT:** input slot (the Tether / augment being fed in).
  - **MIDDLE:** the 6 EVs stacked (HP / Atk / Def / SpA / SpD / Spe) with +/− per stat.
  - **POOL:** the augment tier's allocation points + a running counter vs the legal max (≤252/stat, ≤510 total).
  - **RIGHT:** live result preview (the anvil's right-side pattern).
- **Build path (logic-first):**
  1. **Data model:** EV augment carries a **6-value spread**, not one scalar. `Augments`/`EffectiveAugments` + `applyEvFloor` consume the spread; clamp ≤252/stat & ≤510 total. Headless-tested core, no MC.
  2. **Interim no-UI authoring:** extend `/gp augment` to set the spread (e.g. `set ev <hp> <atk> <def> <spa> <spd> <spe>`), like nature/ball — testable before the screen exists.
  3. **The screen:** a prime **owo-ui** candidate (`PORTING_WEB_UI.md` Option C — flow + slots + live preview; the anvil-result pattern maps cleanly). Good "first real in-world GUI the new way."
- **Status:** 🐛 open (spec captured; scheduled, not started)

<!-- TEMPLATE
### BUG-01 · 🟠 · Q## · <feature>
- **Repro:** …
- **Expected:** …
- **Actual:** …
- **Evidence:** `gp-logs` line / screenshot / stacktrace
- **Root cause:** _(filled when diagnosed)_
- **Fix:** _(commit hash when fixed)_
- **Status:** 🐛 open
-->
