# 🧪 Greener Pastures — Test Checklist

_Instance: **Greener Pastures Test** (CurseForge). Restart CF to see it. 98 mods = clean Shedmon base + `greenerpastures-0.1.0.jar` only._

**To re-deploy after a code change:** rebuild, then copy `greener-pastures/build/libs/greenerpastures-0.1.0.jar` over the one in
`.../Instances/Greener Pastures Test/mods/` (overwrite), relaunch.

**First, check the log** (`logs/latest.log`) for our init lines — all should appear, no stacktraces:
`Greener Pastures (A Data Science Mod) — common init` · `[better-pasture] Cobbreeding bridge ready` · `[better-pasture] active; 6 pasture-upgrade tiers + wand registered`

---

## 🆕 NEWEST — test these FIRST (deployed 2026-06-28)
1. ☐ **Observability pipe (GpLog)** — `tail -F ~/gp-logs/latest.log`. On MC load you get a `session_start` line; then using the BioBank should stream `biobank.*` events live. Confirms the whole debug-log pipe end-to-end.
2. ☐ **BioBank Batch 1** (`/give @s greenerpastures:biobank`, or creative *Functional Blocks*): place → right-click holding an egg = *"Banked 1 egg"* (sneak+right-click = bank ALL inventory eggs) → empty-hand right-click = chat summary by species → **relog → summary persists** → **break → eggs scatter back** (nothing lost). Log shows `biobank.deposit/summary/break_scatter`. _(No GUI yet — Batch 2; cap 256; withdraw = break for now.)_
3. ☐ **Greener Kernel = 8 pairs** — slot `breeding_upgrade_greener`, fill a pasture (16 mons / 8 pairs) → all **8** pairs breed (top tier now exactly fills the 16-mon pasture; was 12).

## 🔴 PENDING IN-GAME TEST QUEUE — built this session, NOT yet confirmed (do these)
Restart MC first (jar reloads at startup). Then, in order:
1. ☐ **No blur / custom buttons everywhere** — wand, board, Daemon, Compiler all crisp; buttons (Arrange/Daemon/Compile/Node-size) are the flat **GpButton** style; zero vanilla buttons, no menu-blur. (§A, §D)
2. ☐ **Esc → wand menu** — Daemon and the Arrange board both return to the **wand menu** on Esc (not out to the world). (§D, §H)
3. ☐ **Daemon = unified Notebook** (§H) — wand → **Daemon** opens the full Notebook (title bar, Daemon/Dashboard/Compiler tabs, pasture name, Kernel slot) over the canvas; **smooth wires** (no cubes); drag/wire/zoom/right-click work. Compare to `./gradlew studio` (how much transfers).
4. ☐ **Shiny-proc augment** (§F) — `/give @s greenerpastures:breeding_upgrade_copper[greenerpastures:augments={shiny:40}]` → tooltip shows proc → slot + breed → `proc_shiny` in `events.jsonl`. Plain upgrade = no change (regression check).
5. ☐ **Compiler block** (§G) — give `compiler` + a plain Kernel + `augment_shiny` → bench → **▸ Compile** → augmented Kernel, augment consumed, **no item loss** on close.
6. ☐ **Suppression still holds** (regression) — managed pasture lays only configured pairs, no rogue 3rd egg (confirmed 6/24; re-glance after all the GUI churn).
7. ☐ **(carryover)** Destiny-Knot'd parent → 4-IV egg through the multi-pair path (was never confirmed; see `IDLE_BREEDING_IDEA.md`).

## 🟢 Better Pasture + Pasture Wand GUI (the NEW/risky bit — test hardest)

**Get the items** (no crafting recipes yet — creative **Tools** tab or commands):
- `/give @s greenerpastures:pasture_wand`
- `/give @s greenerpastures:breeding_upgrade_copper` (…`iron`/`gold`/`diamond`/`netherite`/`greener`) — pairs per tier: **copper 2 · iron 3 · gold 4 · diamond 5 · netherite 6 · greener 8**.

**Setup:** place a Cobblemon pasture, tether several compatible mons (e.g. a species + Ditto ×N), and turn **breeding ON** in Cobbreeding's own pasture toggle.

**A. Open the wand GUI** — right-click the pasture **with the Pasture Wand** (NOT the upgrade; install is via the GUI now).
- [ ] Layout: title "Greener Pastures" (top-left), **"Arrange"** button (top-right), **name field** under it, the upgrade slot row with the **leftmost slot tinted green** (= Pasture Upgrade), then "Active Mons: N · pairs: P" and your inventory.

**B. Upgrade slots**
- [ ] Drop a Pasture Upgrade in the **green slot** → the functional slots to its right **light up** (copper 2 … greener 8) and "pairs: P" updates to the tier count.
- [ ] Green slot rejects normal items; functional slots reject Pasture Upgrades. (Functional slots accept other items but **do nothing yet** — that's task #14.)

**C. Name field**
- [ ] Type a name. ⚠️ **Pressing the inventory key (default `e`) must type into the field, NOT close the GUI.** Esc / clicking away closes.
- [ ] Close + reopen the wand → name **and** the slotted upgrade are still there.

**D. Pairing board** — click **"Arrange"**
- [ ] Blueprint board opens: **Pair 1..N** buckets (N = tier pairs) on top, a **"Node size"** button, an **Unpaired** pool below with all tethered mons.
- [ ] Drag two mons into **Pair 1** → it reads **2/2**.
- [ ] ⚠️ Drag a 3rd into Pair 1 → **rejected** + red flash "Pair 1 is full (max 2)".
- [ ] **Right-click** a chip → back to Unpaired. Drag a chip between two pairs → moves.
- [ ] Click **"Node size"** → chips cycle Large/Medium/Small.
- [ ] **Esc to close** (Back buttons removed) → reopen wand → Arrange → **pairings persisted**. Buttons here ("Node size") are now our own **GpButton** (no vanilla MC buttons / no blur).

**E. Breeding (the payoff)**
- [ ] ✅ With ≥1 full pair + breeding ON, wait an interval (test config ≈30s) → **one egg per configured pair**, of that pair's species. (**No rogue 3rd egg** — Cobbreeding's native ticker is suppressed for managed pastures; confirmed 6/24.)
- [ ] ✅ **Zero-config fallback:** unpair everything → breeder reverts to **slot-adjacency** (slots 0&1, 2&3…) and still lays eggs.
- [ ] ✅ **Restart persistence:** reload world → upgrade + name + pairings survive; breeding continues per your pairs.
- 💡 Egg slots: keep `pastureInventorySize` high (test config is 32) — eggs need empty slots.

**Confirm bucket-vs-auto in the data:** `saves/<world>/greenerpastures/events.jsonl` → `egg_laid` lines carry `"mode":"buckets"` (your pairings) vs `"mode":"auto"` (fallback), plus `tier` + `pair`. Precise proof it's pairing by your buckets.

**F. Shiny-proc augment (NEW — Task #14 slice A, built 6/25, not yet confirmed in-game)**
- Author one by hand (Compiler GUI not built yet): `/give @s greenerpastures:breeding_upgrade_copper[greenerpastures:augments={shiny:40}]`
- [ ] **Tooltip:** hover the upgrade → shows `2 pairs · 2 slots` and a cyan `✦ +40% shiny proc` line. (Plain `breeding_upgrade_copper` with no augment shows only the pairs/slots line.)
- [ ] **No regression:** slot the augmented upgrade, pair + breed → eggs still lay exactly as in E (the augment must not break egg-gen). A **plain** (un-augmented) upgrade must behave **identically to before** — proc=0, zero effect.
- [ ] **Mechanic fires in the data:** `events.jsonl` `egg_laid` lines now carry `"shiny":true/false` and `"proc_shiny":true/false`. `proc_shiny:true` = our reroll is what made it shiny.
- 💡 At base 1/8192 you won't *see* proc-shinies fast. To eyeball it: temporarily set Cobbreeding `config/cobbreeding/main.json` → `shinyMethod.always` high (e.g. 500), restart, breed a stack, then grep the log for `"proc_shiny":true`. (The reroll uses the **effective** rate, so a high `always` makes procs hit often.)
- ⚠️ This is a **separate paid augment**, not a tier perk — a tier upgrade with **no** `augments` component gives 0 proc by design.

**G. Compiler bench (NEW — Task #14, built 6/25, not yet confirmed in-game)** — the in-game way to author augments (replaces the hand-`/give` from F).
- Get the pieces: `/give @s greenerpastures:compiler` · `/give @s greenerpastures:breeding_upgrade_copper` (a plain Kernel) · `/give @s greenerpastures:augment_shiny`. (All in the creative **Tools** tab too. ⚠️ Compiler renders **missing-texture** — art pending, function works.)
- [ ] Place the Compiler, **right-click it** → bench opens: `Compiler` title, a `Kernel + Augment → [preview]` row (preview cell tinted green), a **▸ Compile** button, a `$ kernel ready` log line, your inventory.
- [ ] Put the copper upgrade in the **Kernel** slot + the Shiny Augment in the **Augment** slot → the **Augmented Kernel preview** appears in the green cell. Hover it → tooltip shows `✦ +30% shiny proc`.
- [ ] Click **▸ Compile** → progress bar fills + pip-install transcript scrolls (`Collecting… Building wheel… Successfully compiled shiny-boost==1.0 ✓`). The **Kernel slot now holds the augmented copper upgrade**; the Shiny Augment is **consumed** (−1).
- [ ] Take the augmented Kernel out → hover shows `✦ +30% shiny proc`. Slot it in a pasture via the wand → breeding now procs shiny (per F / slice A; check `proc_shiny` in `events.jsonl`).
- [ ] **Idempotent:** put the *augmented* Kernel back + another Shiny Augment → preview empty, **Compile disabled** ("already installed").
- [ ] **No item loss:** close the bench with items still in the slots → they return to your inventory (not dropped/voided).

**H. Daemon node-graph (NEW — Task #16 increment 1, built 6/25, not yet confirmed in-game)** — the visual-scripting view of pairing. *Additive: the bucket board (§D) still works; this is a second view of the same pairings.*
- Open the wand on a pasture → click the **Daemon** button (under "Arrange").
- [ ] **Unified Notebook** opens (matches the Design Studio): a **title bar** (`pasture.ipynb · Greener Pastures` + `● Kernel · running`), a **tab strip** (Daemon · Dashboard · Compiler), and a **header row** with the **pasture name** + a **Kernel slot** (tier + `N/max threads`). Below it, the node canvas. Compare it to `./gradlew studio` — should look the same ("how much transfers").
- [ ] Each tethered mon is a **unit node** (colored sprite letter + species + pair badge). **Wires are smooth solid lines** (no little cubes), even on curves/backwards drags.
- ℹ️ In-game the tabs + name + Kernel slot in the chrome are **display-only for now** (edit the name via the wand still); the IV-filter/collection pipeline from the studio isn't in-game yet (backend pending).
- [ ] **Opens fit-to-viewport** ✅NEW — units auto-arrange in a centered grid framed to the screen on open; **resize the window / change GUI scale** → re-fits. Resolution-independent.
- [ ] **Zoom + pan, crisp text** ✅NEW — **scroll to zoom** toward the cursor (25–300%, header shows %); **pan with middle-drag or empty-space drag**. **Left-drag only wires** (starts on a unit), so it never fights panning. **Text stays sharp at every zoom** (old blur fixed — integer font snap); zoom way out → labels hide for a clean overview.
- [ ] **Esc → returns to the wand menu** (Greener Pastures GUI), NOT out to the world. (Same for the Arrange board.)
- [ ] **Wire a pair:** **left-drag one unit onto another** (the whole node is the grab handle now — no tiny port to hit) → a purple **wire** connects them, both flip to `Pair N` (green), `threads` count goes up.
- [ ] **Right-click a unit** → unpairs it (wire disappears, both ends back to "unpaired").
- [ ] **Capacity:** with all pairs full, wiring one more flashes "All N threads are full".
- [ ] **Shared data:** wire a pair in the Daemon → **Esc** (lands back on the wand menu) → **Arrange** board shows that same pair in a bucket (and vice-versa). Breeding runs those pairs (one egg each, like §E).
- [ ] **No blur / custom buttons:** the canvas is crisp (no menu blur). Wand buttons (Arrange/Daemon), the Compiler's Compile, and the board's Node-size are all our own **GpButton** now — no vanilla MC buttons anywhere in the pasture system.
- [ ] **Persist:** reload world → the pairs survive. *(Node positions aren't stored — they auto-lay-out to fit the viewport each time; only pairings persist. By design now.)*
- ℹ️ Not in this increment: augment/filter/collection nodes, IVs on nodes, the egg-void filter. Those are later increments.

## 🐑 PastureKeeper (being retired — don't QA)
- ⚠️ **Decision: CUT** (still wired: sneak + empty-hand right-click toggles no-wander). Slated for removal in Task 7 — just confirm it doesn't error.

## 🥚 EggOracle (client UI)
- `=` opens the **odds calculator** GUI.
- `J` toggles the **pasture fill-check** overlay (boxes under-staffed pastures). `K` clears the finder.
- `C` while a container is open toggles the **egg culler** overlay (tints keep vs cull eggs by IV/shiny).

## ⭐ Shiny Egg Highlighter (client)
- Open any chest/storage with eggs → **shiny eggs glow gold**, others dimmed.
- `G` (in container) = count eggs into lifetime tally · `Shift+G` = reset · `\` = dump held item's data (debug).

## 📊 Analytics (the data-science core)
- `saves/<world>/greenerpastures/events.jsonl` → JSON lines, currently: `egg_laid` (with `source`/`tier`/`pair`/`mode`/`shiny`/`proc_shiny`), `pasture_toggle`. This is the raw data the dashboards (next) will chart.

---
## 🔴 Report back
- Wand GUI: layout right? Upgrade slots enable per tier? Name field doesn't close on `e`?
- Pairing board: drag-to-pair works? Full-bucket rejects? **Pairings + name persist across reopen AND restart?**
- Breeding: **one egg per configured pair**? `events.jsonl` shows `"mode":"buckets"` when paired, `"auto"` when not?
- Egg overlays / highlighter / collector still working (regression)?
- Any crashes or red lines in `logs/latest.log` (search `[better-pasture]`, `[analytics]`, `mixin`, `Exception`)?
