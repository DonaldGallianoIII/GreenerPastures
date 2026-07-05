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

> **🔧 Batch status (2026-06-30):** all 6 findings triaged. **BUG-001/002/003/004/006 = ✅ ALL QA-VERIFIED in-game (`035ff6b`) + 230 headless tests, 0 fail.**
> BUG-005 + the deferred UIs (BUG-002 EV screen, BUG-006 graph feedback, BUG-004 Compiler) → the **web-dev UI pass**.
> ✅ **DEPLOYED 2026-06-30** — jar md5 `035ff6b` (5 fixes + BUG-003 NPE fix + placement refine) live in *Greener Pastures Test*.
> **In-game so far:** BUG-004 Feather Falling **✅ verified from the log** (`/gp daemon` compile + ON glint + inventory-grant
> + drain-only-installed — `buff tick buffs:1 paid:1,1,1,0` = the 0.75/s carry). BUG-003 un-hide: NPE fixed → **respawn works ✅**;
> re-test showed mons clumped one side → **placement refined** (ring around the pasture) → re-confirm pending on `035ff6b`.
> Round-2 command kit: `QA_SETUP.md` (Cluster A step 5 + the new Cluster B).

### 🐛 Findings — index
| ID | Sev | Q# | Feature | Symptom | Status |
|----|-----|-----|---------|---------|--------|
| BUG-001 | 🟠 | Q16 | Kernel base drop-rate | Flat +0.25% on every tier; never scales (copper = iron = gold = diamond) | ✅ fixed |
| BUG-002 | 🟠 | Q21 | EV augment / Soul Tether | Flat +N EV on ALL 6 stats (blanket); wants per-stat allocation + a Compiler UI | ✅ data+cmd fixed (UI→web pass) |
| BUG-003 | 🟠 | Q38 | Ghost-pasture toggle | One-way: hide works + persists, un-hide does nothing (increment-2 gap). ✅ breeding survives hide (log-confirmed) — NOT a blocker | ✅ verified in-game |
| BUG-004 | 🟠 | Q23 | Daemon drain model | Holding a fed Daemon bills the WHOLE 15-buff suite every second (~5.25/sec/tier) even idle; event buffs should bill on-use, not passively | ✅ verified in-game |
| BUG-005 | 🟡 | Q3 | Daemon node-graph UI | Pokémon nodes too sparse (want type/nickname/gender/IVs/nature); canvas won't zoom out far enough + nodes too small | 🐛 open |
| BUG-006 | 🟠 | Q3 | Daemon graph validation | Graph accepts incompatible pairs (Drilbur×Pidgey) with no feedback → silent dead pair. ✅ breeding layer safe — Cobbreeding gates egg-gen, no illegal eggs | ✅ core verified in-game (graph UI→web) |
| BUG-007 | 🟡 | Q4 | BioBank browse UI | Opens as a right-click text summary, not a chest. Want a scrollable species grid (one egg per species) → click a species → its egg collection (AE2/ME two-level browse) | 🗓️ deferred → web-dev pass |
| BUG-008 | 🟡 | Q15 | Harvester ↔ pasture linking | Harvester reads only the FIRST adjacent pasture (a center-of-4 pulls just one). Redesign: explicit linked-network — link N pastures, harvester stores their coords, no scan, no dup | 🗓️ backend buildable now; link GUI → web pass |

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
- **Open Q (resolved):** the ramp **continues past diamond** — netherite +1.25%, greener +1.50%.
- **✅ Fixed (2026-06-30):** `BreedingTier.baseDropRateCentipercent()` = `BASE_DROP_RATE × (ordinal+1)` (copper 0.25% → … → greener 1.50%); `BetterPasture.registerItems` builds the base **per-tier inside the loop**. Headless test asserts the ramp. _(Re-`/give` Kernels minted before the fix.)_
- **Status:** ✅ fixed

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
- **✅ Fixed (2026-06-30, data + command):** the EV augment now carries a 6-value `EvSpread` (clamp ≤252/stat, ≤510 total) via the `ev_spread` component; `CobbreedingBridge.applyEvSpread` writes each stat by name (raise-only); `/gp augment ev <hp> <atk> <def> <spa> <spd> <spe>` authors it. Headless `EvSpreadTest`. **Allocation GUI deferred → web-dev UI pass.**
- **Status:** ✅ fixed (data + command); allocation UI → web pass

### BUG-003 · 🟠 MAJOR (→ 🔴 BLOCKER if breeding dies) · Q38 · Ghost-pasture toggle is one-way (can't un-hide)
- **Confirmed working ✅ (increment 1):** shift-RC hides roamers; toggling ON while they roam hides them in place; persists across save/reload.
- **Broken:** shift-RC again to un-hide does nothing — mons stay gone. Workaround: remove from pasture → toggle OFF → reinsert.
- **Root cause — primary (BY DESIGN, the known increment-2 gap):** hide removes each roamer with `RemovalReason.DISCARDED` (destroyed for good, never reloads) + re-asserts its `tetheringId` to keep the data. Un-suppress only clears the flag so *future* tethers spawn — it does **not** re-create the already-discarded entities, so there's no entity left to "show." This is the documented Q38 increment-2 follow-up (re-materialise on un-suppress), **not a regression**.
- **⚠️ Possible SECOND bug (needs 1 check):** `gp-logs` shows `keeper ghost_on cleared:2` firing repeatedly (11:16:27 / :36 / :46) rather than alternating on/off. Consistent with the remove/reinsert workaround (each reinsert respawns 2 → next hide clears 2) — but could also mean the toggle isn't flipping to OFF in state (every click re-enables). **Disambiguator:** on the 2nd (un-hide) click, does chat say "ghost pasture **OFF**" or "**ON**"? OFF ⇒ pure increment-2 gap; ON ⇒ a real toggle-state read bug to fix too.
- **⚠️ BLOCKER gate — does a hidden pasture still breed?** Code says **yes by design**: the breeder reads the pasture's tether *data* (`MultiPairBreeder.breedPairs:150` → `pasture.getTetheredPokemon()` → `buildEggForPair`), never the roaming entity. So breeding survives **iff** the Tethering survived the discard. Two checks confirm it: **(a)** after hiding, open the pasture GUI — are the mons **still listed**? **(b)** on a hidden pasture with a Kernel + a configured pair, wait ~2.5 min → do eggs hit the tray / `breeder` lines appear in `gp-logs`? If mons also vanish from the GUI ⇒ tether lost ⇒ BLOCKER (increment-1's core failed). _No breeder activity in the log yet this session — no pair+Kernel set up on the test pasture._
- **Fix:** build **Ghost Pasture increment 2** — on un-suppress, re-materialise the tethered mons from their stored data (replicate Cobblemon's tether-spawn). Already on the roadmap; this finding promotes it to "next ghost-pasture work." (+ fix the toggle-state read if the chat check reveals the 2nd bug.)
- **✅ UPDATE (live log, 12:15):** breeding **survives suppression** — pasture `-15,86,24` laid an egg at `12:15:43` *while ghosted* (toggled `ghost_on` at `12:13:06`). So increment-1's core holds (the tether survives the DISCARD → breeding keeps producing) and this is **NOT a blocker**. Severity stays 🟠 MAJOR (the one-way-toggle UX only). _Side-finding: the "missing eggs" weren't missing — the adjacent Renderer culled each into Data on lay (`brood 12:12:51` → `render 12:12:53`), so the tray stayed empty while Data climbed._
- **✅ Fixed (2026-06-30, increment-2 built):** `PastureKeeper.respawnTethered` re-materialises each tethering with no live entity (fresh `PokemonEntity` from the stored `Pokemon` → re-link the **existing** `Tethering`); `setSuppressed` calls it on un-hide, and `liveTetheredEntities` makes the scan entityId-independent.
- **🐛→✅ In-game NPE found + fixed (2026-06-30, live log, jar `edf05bf`→`4da7999`):** the first deploy respawned **nothing** — log showed `keeper ghost_off spawned:0` + `keeper respawn_skip NullPointerException: Vec3i.getX()`. **Root cause** (from decompiled Cobblemon `tether()`): `makeSuitableY` **returns `null`** when it finds no floor within ±16, and I called it on the **solid pasture block itself**, then did `Vec3d.ofCenter(null)` → NPE. **Fix:** new `suitableSpawn()` mirrors Cobblemon's own search — offset one entity-width off the pasture, step outward, **`continue` past `null`**, fall back to the offset spot if all probes miss (so it never NPEs and always places the mon). **Persistence confirmed from the decompile:** Cobblemon's `checkPokemon` keeps a tethering purely on `pokemon.getTetheringId() == tethering.id` (which we re-assert) — it **ignores `entityId`** — so the respawned mons survive the next pasture tick. Redeployed `4da7999`.
- **✅ Placement refined (2026-06-30, `4da7999`→`035ff6b`):** the NPE-fix re-test confirmed mons **do** re-materialise + persist — but they **clumped a few blocks off one side** (I'd hardcoded `Direction.NORTH` + stepped outward). `suitableSpawn` now rings them N/E/S/W around the pasture (stepping one block further every 4 mons) at the pasture's own Y, so they stand **right next to it**, grounded.
- **✅ VERIFIED in-game (2026-06-30, `035ff6b`):** un-hide re-materialises the mons, **grounded and ringed around the pasture** on all sides; repeat on/off toggles produce no duplicate roamers; breeding/data intact throughout. Increment-2 closed.
- **Status:** ✅ VERIFIED in-game (un-hide respawns + rings the pasture, no dupes)

### BUG-004 · 🟠 MAJOR (design/balance) · Q23 · Daemon bills the full buff suite continuously, even when idle
- **Observed (Deuce):** Data balance drains just from *holding* a fed Daemon with nothing actively in use.
- **Root cause — CONFIRMED (by design, but the design is the gripe):** `DaemonBuffs.settle()` resolves ALL `SUPPORTED` buffs (15) at the Daemon's tier every second and debits the summed `Σ tier × costPerSec` — no "is this buff doing work" gate. Idle-holding bills the entire roster. Rate = **5.25 Data/sec/tier** (6 gathering ×0.5 + 9 QOL ×0.25): **Mk I ≈ 5.25/s (~315/min), Mk III ≈ 15.75/s (~945/min)**.
- **The tension:** *passive* buffs (Haste, Saturation, Magnet, Respiration, Swift Sneak, Feather Falling, Frost Walker, Potion Duration) genuinely work every tick → fair to bill per-second. *Event* buffs (Fortune, Auto-Smelt, Vein-Mine, Looting, Lure, Luck, XP Boost) do nothing until you mine/fish/kill → billing them each idle second is the wrong feel.
- **Options:** (1) **NOW (no rebuild):** `config/greenerpastures/buffs.json` — per-buff `enabled:false` or lower `costPerSec`. (2) **RECOMMENDED:** split billing — passive = per-second, event = **pay-on-trigger**; idle drain drops to ~2.0/sec/tier and gathering buffs cost only when they actually proc. (3) in-game on/off (or per-buff) toggle on the Daemon.
- **✅ DECIDED → redesign:** Deuce chose the bigger fix — the Daemon becomes a **compile-your-own-buffs** item: compile a chosen buff loadout in the Compiler, right-click to toggle ON (enchant glint), works from your inventory/backpack, drains **only the installed buffs**, and **never force-loads a chunk**. Supersedes the "hold → whole suite at global Mk" model. Full spec: **`DAEMON_REDESIGN.md`**.
- **✅ BUILT (2026-06-30, headless-green):** the compile-your-own model is in.
  - **Core (MC-free, tested):** new pure `DaemonLoadout` (a `buff → level` map component mirroring `Augments`) + `BuffResolver.resolveLoadout` — bills `Σ tier × costPerSec` over **only the installed** buffs, each re-clamped to its own cap + the +3 ceiling. **+11 headless tests** (6 `DaemonLoadoutTest` + 5 `resolveLoadout`); **230 total, 0 fail**.
  - **Item:** right-click toggles `DAEMON_ON` ↔ vanilla `ENCHANTMENT_GLINT_OVERRIDE`; tooltip lists installed buffs + ON/OFF. The Mk-level cycle / `daemon_level` drive is retired (`daemon_level` left registered for back-compat).
  - **Grant/drain:** `DaemonBuffs.settle` now scans the player's **whole inventory** for the first ON Daemon (`firstActiveDaemon`) and resolves its loadout — works from inventory, not just in-hand; never force-loads (an online player's inventory is always loaded).
  - **No-UI path:** new `/gp daemon set <buff> <level> · list · clear · on · off` (mirrors `/gp augment`) — validates against the 15 `SUPPORTED` + the per-buff cap; fully testable before the Compiler GUI.
  - **Carried over unchanged:** all buff *delivery* (mixins, attribute reconcile, magnet, vein-mine) + the drain economy + fractional carry — only *what's resolved + billed* flipped from "global suite" to "this item's loadout."
  - **Defaults taken (flag if wrong):** compile cost = running-drain-only (no upfront sink); per-buff cap = each buff's own `maxTier`/+3; compilable set = the 15 `SUPPORTED`.
- **⚠️ In-game QA pending (can't headless):** right-click glint toggle; buffs granting from inventory (not hand); drain = only-installed; **migration** — existing in-world Daemons carry no loadout/ON → grant nothing until re-compiled + toggled (changelog note for public).
- **✅ VERIFIED in-game (2026-06-30, `035ff6b`):** the loadout path delivers (Feather Falling from the log + multi-buff loadouts); right-click glint toggle, inventory-grant, drain-scales-with-loadout, OFF=inert, broke-account=no-buffs, and `/gp daemon` cap/undeliverable validation all pass.
- **Status:** ✅ VERIFIED in-game · Compiler GUI → web-dev pass

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
- **✅ Fixed (2026-06-30, core):** pure `BreedingCompat.canBreed(a, b)` — the 4 rules (undiscovered / both-ditto / either-ditto / shared-group + opposite-gender); **8 headless tests** incl. Drilbur×Pidgey. `CobbreedingBridge` logs a `pair_incompatible` line when `getPossibleEggs` returns empty, so a dead pair shows in `gp-logs` instead of failing silently. **Graph wire-time feedback (red wire + tooltip) deferred → Daemon UI web pass.**
- **Status:** ✅ core fixed; graph feedback → web pass

### BUG-007 · 🟡 MINOR (enhancement) · Q4 · BioBank should open as a two-level scrollable egg browser
- **Now:** the BioBank has **no browse GUI** — right-click empty-hand = a text summary, right-click holding eggs = deposit (Q4). You can't open it like a chest to see/pull what's stored.
- **Want (Deuce) — AE2/ME-style two-level browse:**
  1. **Species grid (level 1):** open the BioBank → a **scrollable, chest-style GUI** with room for **every egg species** it holds — one slot per species, each showing that species' egg (icon) + its count.
  2. **Species collection (level 2):** click a species' egg (e.g. **Charmander**) → a second screen opens listing **all the individual Charmander eggs** stored (the real eggs — sortable by shiny / IVs, per the original BioBank design).
- **Fits the original design:** this *is* the BioBank's always-intended "eggs as data, bucketed by species, sortable by shiny/IVs" browse front-end — just specified concretely now. The deposit/summary/persist/scatter back-end (Q4 ✅) already exists; this is the missing **view** layer.
- **Decision:** **defer to the web-dev UI pass** (Deuce: "prolly defer that til we know what the ui looks like for now"). Captured here so it's ready when we build the deferred UIs — it joins the web-dev backlog alongside BUG-002 (EV) / BUG-004 (Compiler) / BUG-005 (node detail) / BUG-006 (graph feedback) + the dashboard.
- **Status:** 🗓️ deferred — web-dev UI pass

### BUG-008 · 🟡 ENHANCEMENT (redesign) · Q15 · Harvester → explicit linked-network (not adjacency/radius)
- **Now:** `adjacentPasture()` returns only the FIRST face-neighbour pasture (DOWN→UP→N→S→W→E), so a Harvester touching 4 pastures harvests just ONE. Found live during QA (Deuce's center-of-4 layout; the heartbeat showed each harvester locked to a single `pasture:` with `mons:16`).
- **Considered + set aside — radius scan:** collect from all pastures within ~16 blocks. Cheap if done right (iterate *loaded chunks' block-entity maps* once/min, never a raw block scan), but invites overlap/double-harvest + neighbour-leeching.
- **Chosen design (Deuce) — explicit links, ME-drive style:**
  - The Harvester stores a **persisted list of linked pasture positions**; each harvest tick it just iterates them (zero search — "not checking over and over") and rolls each linked pasture's mons.
  - **A pasture links to exactly one Harvester** → no duplication / double-harvest by construction.
  - Each linked pasture's drops compute from **that pasture's own Kernel** (the existing per-pasture dropPlan — drop_rate/yield + tethers), unchanged.
  - The Harvester GUI **lists the linked pastures by coords** (+ status: loaded / mons / unlink); a link action toggles "harvest from this pasture?".
- **UX nuance:** the pasture's in-world GUI is **Cobblemon's** (can't bolt our button onto it), so "pick which pasture" wants either a small **link tool** (RC harvester = select, RC pasture = toggle) or a harvester-side "link the pasture I'm looking at" — settle in the UI pass.
- **Build path (logic-first):** the **link store + harvest-from-links backend + a `/gp harvester link|unlink|list` no-UI command** are buildable NOW (functional + testable), exactly like `/gp daemon`. The **linking GUI + linked-coords view → web-dev UI pass** (joins the deferred-UI backlog).
- **Status:** 🗓️ design agreed — backend buildable now; GUI → web pass

### BUG-009 · 🔴 CRITICAL (console bricked) · Q42–48/Q74 · Notebook black-screens once a Kernel enters the inventory
- **Repro:** with the console previously opened kernel-less, pick up a Kernel (Daemon coincidental) → open Notebook → permanent black screen every open until MC relaunch.
- **Root cause:** React hooks-order violation — batch 6's ⛧ CORRUPT button added `useChannel('inventory')` in `Augmenter()` AFTER the `!d`/`!d.hasKernel` early returns. Kernel-less renders = 3 hooks, first kernel render = 4 → React throws, app unmounts; the preloaded MCEF page persists across opens, so the dead root stays dead.
- **Evidence:** no Java-side exception (JS-only crash); audit swept App.jsx — this was the sole hook-after-early-return instance.
- **Fix:** hoisted the hook above the early returns (App.jsx:684); rebuilt UI + jar, 270 tests green; jar `74c7f3b1` deployed 2026-07-04 (replaces `60f95467`).
- **Status:** 🚀 fix deployed — pending Deuce's in-game re-check (open console, pick up Kernel, Augmenter tab must render)

### BUG-010 · 🟠 MAJOR (breeding quality) · Q21/Q44 · IV Floor always perfects HP/Atk/Def — not random
- **Repro:** IV Floor 3 on a Kernel → every floored egg lands perfect HP/Atk/Def (Deuce noticed the pattern live, 2026-07-04).
- **Root cause:** `CobbreedingBridge.applyIvFloor` promoted the first N non-perfect stats iterating `Stats.PERMANENT` in fixed declaration order (HP→Atk→Def→SpA→SpD→Spe) — deterministic, junk for special-attacker builds.
- **Fix:** collect non-perfect stats → `Collections.shuffle` → promote N; still a true raise-only floor (inherited 31s count, never lowers). New DEBUG line `breeding iv_floor already:N promoted:spa,spe,…` per egg. In jar `26cac147`.
- **Status:** 🚀 built — deploys at next exit window; verify a few floored eggs show varied perfect stats + the new log line

### BUG-011 · 🟡 MINOR (UI ghost) · Q3/Q50 · Untethered mon leaves a hex-labelled ghost parent on the line graph
- **Repro:** take a mon out of a pasture that's on a breeding line → its parent node/chip stays, labelled with a UUID fragment (e.g. "E531ec"), pair shows "can't breed" (screenshot 680, 2026-07-04).
- **Root cause:** `PastureData.pairings` keeps the removed mon's tetheringId; the breeder skips unresolvable pairs (no gameplay harm) but nothing ever pruned the stale entry, and the UI label falls back to the raw id.
- **Fix:** breeder scan self-heals — for LOADED pastures (authoritative roster) any pairing key not in the live tether list is dropped (`breeder pairing_pruned` DEBUG line); ghost vanishes within ~2s of removal. Unloaded pastures are never pruned (no roster-guessing). In jar `f9b38134`.
- **Status:** 🚀 built — verify: untether a paired mon → node disappears within a couple seconds; relog persists the cleaned board

### BUG-012 · 🟠 MAJOR (UI false alarm) · Q3/Q50 · All lines flip to "this pair can't breed" after ~1 min on a pasture view
- **Repro:** stay on a pasture view >60s (Deuce hit it via a kernel unslot→augment→reslot dance) → every 2-parent line shows the ♂+♀/Ditto warning despite valid pairs; refocusing fixes it for another minute.
- **Root cause:** the 1-min prefetch re-warm (`prefetchConfigs`, stats-less roster shape by design) includes the FOCUSED pasture; `applyPastureConfig`'s guard only blocked cross-pasture hijacks, so the same-pos prefetch replaced the live config → `MonEntry.stats` (gender) blank → `pairValid` false on every line. Not kernel-related — kernel cycling just kept him on the view past the sweep boundary.
- **Fix:** client stale-while-revalidate backfill — a stats-less incoming roster entry inherits the cached entry's stats (by tethering id); entries arriving WITH stats always win. Cache and live view both upgraded. In jar `47f0909d`.
- **Status:** 🚀 built — verify: sit on a pasture view 2+ min → warnings never appear on valid pairs; kernel out→edit→in keeps lines intact

### BUG-013 · 🔴 CRITICAL (economy exploit) · Q80/Q57 · Kernels stack — one augment payment upgrades the whole stack
- **Repro:** hold a stack of same-tier kernels → Augmenter APPLY → the component write lands on the ItemStack = ALL N kernels augmented for one GPU fee (Deuce caught it testing the target selector, 2026-07-04). Same holds for corruption.
- **Root cause:** `BreedingUpgradeItem` registered `maxCount(16)`; augments/corruption are stack-wide data-component writes.
- **Fix:** `maxCount(1)` — kernels can never stack (matches Notebook/Daemon). Pre-existing stacks in a world split on first interaction (vanilla behavior); their shared components were already identical so nothing is lost. In jar `8a723694`.
- **Status:** 🚀 built — verify post-swap: kernels refuse to stack (craft 2 coppers → 2 slots); augment applies to exactly the one targeted kernel

### BUG-014 · 🟡 MINOR (display lie) · Q77/Q48 · Slotted kernel's hover tooltip shows tier defaults, not its real augments
- **Repro:** slot an augmented kernel → hover its cell in the pasture view's inventory overlay → "1 augment installed · +1.50% drop rate" (fresh-kernel tooltip) while the real item carries 5 (screenshots 684/685, Deuce 2026-07-04).
- **Root cause:** the overlay's kernel cell is a display stack rebuilt client-side from just the TIER name (`new ItemStack(tier item)`) — default components. Server-side stack (and breeding) always had the real augments; remove returned them intact.
- **Fix:** extras channel now carries the slotted kernel's full augment map + EV spread + corruption; the client dresses the display stack with those components, so `appendTooltip` renders truth. Fail-soft (dressing errors → tier defaults, never a broken overlay). In jar `9300b866`.
- **Status:** 🚀 built — verify post-swap: hover slotted augmented kernel → same tooltip as when held

### BUG-015 · 🟡 MINOR (UI) · Q92/Q80 · Augmenter (and Compiler) tab can't scroll
- **Repro:** open Augmenter with the grown catalog (Hatch Haste + UPGRADE rows) → content past the viewport is clipped, wheel does nothing (Deuce, live QA 2026-07-05).
- **Root cause:** the Q80 target-card wrapper div broke the height chain into `gp-body` (overflow:hidden) - wrapper height went auto, the `.tcol` columns thought they had infinite height, their own `overflow:auto` never engaged. Compiler had the identical wrapper bug.
- **Fix:** wrappers are full-height flex columns, triptych = flex:1 + minHeight:0 (commit `66e4023`). In jar `ab6cad16`.
- **Status:** 🚀 built — verify post-swap: Augmenter catalog wheel-scrolls with 12+ rows; Compiler ditto

### BUG-016 · 🔴 MAJOR (design) · Q93-adjacent · Kernel-slotted pastures bred with ZERO scripting (adjacency fallback)
- **Repro:** slot a kernel, tether a roster, wire nothing → `breeder brood` starts pairing slot-adjacent mons (Deuce, live QA 2026-07-05: gastly/murkrow/clefairy rosters bred + banked eggs uninvited).
- **Root cause:** intentional-at-the-time "zero-config" adjacencyPairs fallback in MultiPairBreeder when `pd.pairings` was empty. Eggs still routed safe (default keep → BioBank, sacred rule held) - but breeding ran without consent, and pairs were unmonitorable.
- **Fix (design change, Deuce's call):** the visual script IS the limiter - `buildPairs` now returns bucket pairs only; no wired lines = no breeding, ever. New health chip `🧵 no_lines` ("No breeding lines - wire a pair in this pasture's graph") shows on kerneled+populated+unwired pastures, registry-known even with the chunk unloaded. Guide copy sharpened. +4 tests (309). Commit pending this entry; in jar `769ce853`.
- **Status:** 🚀 built — verify post-swap: unwired kerneled pasture NEVER broods + shows 🧵; wire a line → breeding starts; unwire → stops

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
