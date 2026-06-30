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
| BUG-001 | 🟠 | Q16 | Kernel base drop-rate | Flat +0.25% on every tier; never scales (copper = iron = gold = diamond) | ✅ fixed |
| BUG-002 | 🟠 | Q21 | EV augment / Soul Tether | Flat +N EV on ALL 6 stats (blanket); wants per-stat allocation + a Compiler UI | ✅ data+cmd fixed (UI→web pass) |
| BUG-003 | 🟠 | Q38 | Ghost-pasture toggle | One-way: hide works + persists, un-hide does nothing (increment-2 gap). ✅ breeding survives hide (log-confirmed) — NOT a blocker | ✅ inc-2 built (needs in-game QA) |
| BUG-004 | 🟠 | Q23 | Daemon drain model | Holding a fed Daemon bills the WHOLE 15-buff suite every second (~5.25/sec/tier) even idle; event buffs should bill on-use, not passively | 🔧 redesign |
| BUG-005 | 🟡 | Q3 | Daemon node-graph UI | Pokémon nodes too sparse (want type/nickname/gender/IVs/nature); canvas won't zoom out far enough + nodes too small | 🐛 open |
| BUG-006 | 🟠 | Q3 | Daemon graph validation | Graph accepts incompatible pairs (Drilbur×Pidgey) with no feedback → silent dead pair. ✅ breeding layer safe — Cobbreeding gates egg-gen, no illegal eggs | ✅ core fixed (graph UI→web pass) |

### ✅ Verified working
| Q# | Feature | Note |
|----|---------|------|
| — | Mod load | Clean load: common+client init, ghost-pasture mixin resolved, 15 buffs registered, no red lines |
| Q24 | Daemon Fortune boost | ✅ fires at Mk I (`from:3→to:4`) **and Mk III (`from:3→to:6`, Fortune VI)**; vein cap scales 32→96, drain ~3× (paid 5→16/s) — tier system fully validated. _Pending: negatives (no-Fortune pick / Data-0 → no boost)._ ⚠️ needs Daemon off-hand until the inventory redesign |
| Q27 | Vein-miner | ✅ 17-block diamond vein, capped tier×32; **Fortune applied to EVERY block** (the "whole-vein compliant" claim holds — 18 boost events = the full vein). _Pending: safety negatives (dirt/stone must NOT vein), auto-smelt on iron._ Note: conflicts w/ SuperMartijn's ore-vein-miner (redesign makes ours opt-in) |
| Q25 | Auto-Smelt | ✅ iron ore → `iron_ingot` (`auto_smelt to:minecraft:iron_ingot`); **composes with Fortune + vein-mine** — a 32-block iron vein, each block Fortune-boosted (n up to 5) then smelted to ingots. Full gather-stack chains |
| Q1 | Egg-queue cap | ✅ `breeder queue_full cap:24` fired (no silent discard past the cap) — the FIFO buffer guard works under a fast (15s) breed rate |
| Q26 | XP Boost | ✅ `/xp add 100` → `xp_boost from:100 to:175 tier:3` (**+75%** at Mk III); scales by tier (Mk I `7→9` = +25%). Deterministic |
| Q28 | Potion Duration | ✅ Night Vision `potion_extend from:3600 to:9000 tier:3` (3:00 → 7:30, +150%). _Combat-potion control (Strength must NOT extend) not yet checked._ |
| Q29 | Attribute buffs | ✅ all 3 at Mk III: `respiration value:3.0` · `swift_sneak +0.45` · `feather_falling −0.45`; reconciled each second |
| Q30 | Value-effect buffs | ✅ **Lure** (`fish_lure 15→30`) + **Luck of the Sea** (`fish_luck 3→6`) confirmed boosting at Mk III — proves the `EnchantmentValueBoostMixin` seam. **Looting + Frost Walker** ride the same mixin's 3rd inject (`getEquipmentLevel`) → trust by extension (1 kill / water-walk would log `equip_boost` to fully close) |
| Q38 | Ghost pasture — breeding | ✅ breeding survives suppression (see BUG-003 update) |

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

### BUG-003 · 🟠 MAJOR (→ 🔴 BLOCKER if breeding dies) · Q38 · Ghost-pasture toggle is one-way (can't un-hide)
- **Confirmed working ✅ (increment 1):** shift-RC hides roamers; toggling ON while they roam hides them in place; persists across save/reload.
- **Broken:** shift-RC again to un-hide does nothing — mons stay gone. Workaround: remove from pasture → toggle OFF → reinsert.
- **Root cause — primary (BY DESIGN, the known increment-2 gap):** hide removes each roamer with `RemovalReason.DISCARDED` (destroyed for good, never reloads) + re-asserts its `tetheringId` to keep the data. Un-suppress only clears the flag so *future* tethers spawn — it does **not** re-create the already-discarded entities, so there's no entity left to "show." This is the documented Q38 increment-2 follow-up (re-materialise on un-suppress), **not a regression**.
- **⚠️ Possible SECOND bug (needs 1 check):** `gp-logs` shows `keeper ghost_on cleared:2` firing repeatedly (11:16:27 / :36 / :46) rather than alternating on/off. Consistent with the remove/reinsert workaround (each reinsert respawns 2 → next hide clears 2) — but could also mean the toggle isn't flipping to OFF in state (every click re-enables). **Disambiguator:** on the 2nd (un-hide) click, does chat say "ghost pasture **OFF**" or "**ON**"? OFF ⇒ pure increment-2 gap; ON ⇒ a real toggle-state read bug to fix too.
- **⚠️ BLOCKER gate — does a hidden pasture still breed?** Code says **yes by design**: the breeder reads the pasture's tether *data* (`MultiPairBreeder.breedPairs:150` → `pasture.getTetheredPokemon()` → `buildEggForPair`), never the roaming entity. So breeding survives **iff** the Tethering survived the discard. Two checks confirm it: **(a)** after hiding, open the pasture GUI — are the mons **still listed**? **(b)** on a hidden pasture with a Kernel + a configured pair, wait ~2.5 min → do eggs hit the tray / `breeder` lines appear in `gp-logs`? If mons also vanish from the GUI ⇒ tether lost ⇒ BLOCKER (increment-1's core failed). _No breeder activity in the log yet this session — no pair+Kernel set up on the test pasture._
- **Fix:** build **Ghost Pasture increment 2** — on un-suppress, re-materialise the tethered mons from their stored data (replicate Cobblemon's tether-spawn). Already on the roadmap; this finding promotes it to "next ghost-pasture work." (+ fix the toggle-state read if the chat check reveals the 2nd bug.)
- **✅ UPDATE (live log, 12:15):** breeding **survives suppression** — pasture `-15,86,24` laid an egg at `12:15:43` *while ghosted* (toggled `ghost_on` at `12:13:06`). So increment-1's core holds (the tether survives the DISCARD → breeding keeps producing) and this is **NOT a blocker**. Severity stays 🟠 MAJOR (the one-way-toggle UX only). _Side-finding: the "missing eggs" weren't missing — the adjacent Renderer culled each into Data on lay (`brood 12:12:51` → `render 12:12:53`), so the tray stayed empty while Data climbed._
- **Status:** 🐛 open — increment-2 (proper un-hide) recommended; severity 🟠 (no longer a blocker risk)

### BUG-004 · 🟠 MAJOR (design/balance) · Q23 · Daemon bills the full buff suite continuously, even when idle
- **Observed (Deuce):** Data balance drains just from *holding* a fed Daemon with nothing actively in use.
- **Root cause — CONFIRMED (by design, but the design is the gripe):** `DaemonBuffs.settle()` resolves ALL `SUPPORTED` buffs (15) at the Daemon's tier every second and debits the summed `Σ tier × costPerSec` — no "is this buff doing work" gate. Idle-holding bills the entire roster. Rate = **5.25 Data/sec/tier** (6 gathering ×0.5 + 9 QOL ×0.25): **Mk I ≈ 5.25/s (~315/min), Mk III ≈ 15.75/s (~945/min)**.
- **The tension:** *passive* buffs (Haste, Saturation, Magnet, Respiration, Swift Sneak, Feather Falling, Frost Walker, Potion Duration) genuinely work every tick → fair to bill per-second. *Event* buffs (Fortune, Auto-Smelt, Vein-Mine, Looting, Lure, Luck, XP Boost) do nothing until you mine/fish/kill → billing them each idle second is the wrong feel.
- **Options:** (1) **NOW (no rebuild):** `config/greenerpastures/buffs.json` — per-buff `enabled:false` or lower `costPerSec`. (2) **RECOMMENDED:** split billing — passive = per-second, event = **pay-on-trigger**; idle drain drops to ~2.0/sec/tier and gathering buffs cost only when they actually proc. (3) in-game on/off (or per-buff) toggle on the Daemon.
- **✅ DECIDED → redesign:** Deuce chose the bigger fix — the Daemon becomes a **compile-your-own-buffs** item: compile a chosen buff loadout in the Compiler, right-click to toggle ON (enchant glint), works from your inventory/backpack, drains **only the installed buffs**, and **never force-loads a chunk**. Supersedes the "hold → whole suite at global Mk" model. Full spec: **`DAEMON_REDESIGN.md`**.
- **Status:** 🔧 spec'd — build pending (logic-first; `/gp daemon` no-UI path first, Compiler GUI later)

### BUG-005 · 🟡 MINOR (enhancement) · Q3 · Daemon node-graph — sparse nodes + can't zoom out enough
- **Component:** the Daemon visual-scripting node graph (in-world GUI).
- **Need — surface on each Pokémon node:** **Type(s)** · **Nickname** (if set) · **Gender** · breeding-relevant stats: **IVs + Nature**. (All readable off the tethered `Pokemon`: `getTypes` / `getNickname` / `getGender` / `ivs` / `getNature`.)
- **Canvas:** (a) widen the **max zoom-out** — currently can't pull back far enough to see the graph at scale (anchor: `MIN_ZOOM = 0.25` in `client/ui/DaemonController.java:31` → lower it); (b) **bigger nodes/icons** so the new detail is legible.
- **Rollout (Deuce):** bundle into the **next web-dev pass of the Daemon UI** — do NOT hotfix piecemeal.
- 🔗 **Reinforces the UI strategy:** the Daemon canvas is exactly the screen `PORTING_WEB_UI.md` flagged as the hard one. Bigger legible nodes + per-node Pokémon detail panels + a wider zoom range are painful in raw `Screen`/matrix math but natural in **owo-ui** (or a web canvas). Strong vote to **rebuild the canvas the new way**, not patch the old Screen — and to fold these node fields into that rebuild.
- **Status:** 🐛 open — deferred to the Daemon UI rework (intentionally not piecemeal)

### BUG-006 · 🟠 MAJOR (graph UX) · Q3 · Daemon graph accepts incompatible breeding pairs
- **Repro:** wired **Drilbur → Pidgey** in the graph; the link was accepted, no error. (Drilbur = **Field** egg group, Pidgey = **Flying** → no shared group → can't breed in any legal ruleset.)
- **✅ SCARY UNKNOWN RESOLVED — NOT a blocker, no illegal eggs:** the breeder defers egg-gen to Cobbreeding's own `getPossibleEggs` (`CobbreedingBridge.buildEggForPair:225`). An incompatible pair → **empty** list → `if (possible.isEmpty()) return null;` → **no egg**. The javadoc states it outright ("Returns null if the pair is incompatible"). Live log corroborates: only the valid pasture `-15,86,24` is laying; the illegal pair produced nothing. The breeding layer **cannot** spawn a Drilbur×Pidgey egg — Cobbreeding's ruleset gates it.
- **The actual bug (graph UX):** the graph accepts the illegal wiring with **no feedback**, creating a **silently dead pair** that never lays — wastes a pair slot, and the user has no idea why it's not producing.
- **Fix — validation the node needs (mirror Cobbreeding's ruleset):**
  1. **Shared egg group** — parents intersect on ≥1 egg group.
  2. **Gender** — one ♂ + one ♀ (Ditto bypasses).
  3. **Ditto special case** — Ditto breeds with anything *except* another Ditto and the Undiscovered group.
  4. **Undiscovered / No-Eggs exclusion** — legendaries / babies / etc. can't breed at all.
- **Build plan:** a pure, headless-tested **`BreedingCompat.canBreed(a, b)`** core (the 4 rules over abstracted egg-groups / gender / ditto / undiscovered) + a thin adapter reading `species.getEggGroups()` / `getGender()` from Cobblemon. The graph calls it at **wire-time** → red wire + "incompatible: no shared egg group" tooltip. **Graph feedback bundles into the Daemon UI web-dev pass** (with BUG-005). **Interim cheap win (batchable now):** a `GpLog` line where `possible.isEmpty()` so a dead pair shows in `gp-logs` instead of failing silently.
- **Status:** 🐛 open — compat core buildable now (pure); graph feedback → Daemon UI pass

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
