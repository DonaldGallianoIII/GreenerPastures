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
| _none yet — fire away_ | | | | | |

### ✅ Verified working
| Q# | Feature | Note |
|----|---------|------|
| — | Mod load | Clean load: common+client init, ghost-pasture mixin resolved, 15 buffs registered, no red lines |

---

## Detail log
_(Per-finding detail — repro, expected/actual, log evidence, root-cause + fix — appended as they come.)_

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
