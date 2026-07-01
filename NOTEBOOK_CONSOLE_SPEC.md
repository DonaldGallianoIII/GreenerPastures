# 💻 Notebook Console — the unified terminal

Deuce's structural decision (2026-06-30): **full console.** Every standalone block collapses into ONE item — the Notebook. Right-click it → a tabbed, browser-style UI. Extends the existing `NotebookView` (tab strip: Daemon · Dashboard · Compiler) into THE console for the whole mod. Renders via the viewport PNG-skin pipeline (`viewport-ui-principle`).

---

## 1 · The shell
- **Two open modes:** right-click **a pasture** → that pasture's config screen (the Notebook **replaces the Pasture Wand** — pairing buckets, Kernel slot, name). Right-click **in the air** (no pasture) → the **console** (the tabbed hub below).
- **Browser-tab strip across the top** — click a tab to jump to a function.
- Tabs (starting set): **Pastures** (network) · **BioBank** (eggs) · **Storage** (harvested drops) · **Augmenter** (Kernels) · **Compiler** (Daemon) · **Dashboard** (analytics). Room for more (Rituals, Goals…).
- **Tab icons (decided):** Pastures → Cobblemon pasture · BioBank → `tab_biobank.png` (staged) · Storage → vanilla `minecraft:bundle` · Augmenter → `gpu` · Compiler → `daemon` (ghost) · Dashboard → custom (WIP). Only Dashboard needs new art; the rest reuse existing/vanilla textures.
- Each tab is a screen on the shared harness — the **Augment Bench** tabs (Compiler, Augmenter) are just the triptych (`augment-bench-ui-pattern`).

## 2 · Pasture network (linking + ownership)
- The link control is a **button inside the pasture screen** (open a pasture with the Notebook → "Link to Notebook"). Linking stores the pasture's coords on the Notebook **and claims ownership.**
- **Ownership = who collects.** Anyone can add Pokémon to a pasture, but **only the owner** receives its drops / eggs / outputs into *their* Notebook. One owner per pasture; the link button is the explicit claim (see `pasture-operator-claim`). Re-clicking releases it.
- The **Pastures tab** lists your linked pastures by coords → click one → **browse exactly what's in it** remotely (mons, pairs, eggs, Kernel + its augments).
- This link/claim registry is the unifying mechanic — it **retires/solves harvester-linking (BUG-008)**.
- **Future note:** eggs currently queue in the pasture block's back tray (Cobbreeding). In the Notebook format, route laid eggs **straight into the owner's Notebook** (as data/items), bypassing the block queue — no tray cap, collected as items.

## 3 · Digital storage — the killer feature
- Linked pastures' **harvested drops flow directly into the Notebook** — **no Harvester block, no chest farms.**
- Items **stack to the integer limit (2,147,483,647)**, **gated by upgrades** ("upgrade your stuff enough").
- **Pull out on demand** (extract to inventory) — ME-terminal style.
- Same storage backbone holds **eggs (BioBank tab)** and **drops (Storage tab)** — everything is data. This is the BioBank's ME-style storage, **generalized to all items.**

## 4 · Machines → background network processes
The blocks don't vanish — they become **automatic processes on linked pastures**, gated by upgrades/Data:
- **Renderer** → auto-culls eggs on linked pastures → Data to your balance.
- **Harvester** → auto-collects drops on linked pastures → your digital Storage.
- **BioBank** → the BioBank/Storage tabs (browse + pull).
- **Compiler / Augmenter** → tabs; insert a Daemon/Kernel, apply.
- So they stop being world objects. The **Cobblemon pasture stays the only physical anchor.**

## 5 · Architecture calls
**✅ Decided (2026-06-30):**
1. **Storage lives on the PLAYER, not the item.** The Notebook is a *terminal/key* into player-bound network data — losing/dropping/despawning it does **not** delete your warehouse, and billion-count stacks don't bloat item NBT. Any Notebook you hold = your terminal (**one network per player**).
2. **Upgrades are per-tab, paid in GPU.** Each tab levels independently by sinking **GPU** into it — BioBank tab → more egg capacity, Storage tab → higher item cap (toward int-limit), Augmenter → slot bonuses, etc. GPU is the universal **capital** currency (the same reagent the Augmenter consumes). This *is* the capacity/progression gate.

**✅ Also decided:**
3. **GPUs are crafted from Minecraft items** (mined materials — survival grind). NOT fabricated from Data.
4. **Every GPU-consuming action *also* costs Data.** Applying an augment, installing a Soul Tether, upgrading a tab = **GPU cost + Data cost, together.** GPU is the crafted capital; Data is the operating toll you pay to *spend* that capital — you need both flowing to progress, nothing idle.

**⏳ Still open:**
5. **Data cost — one-time vs ongoing?** Default: **one-time** for permanent things (augments baked onto a Kernel, tab upgrades); **ongoing burn** for rented things (Soul Tethers, already Data/cycle). Confirm.
6. **Passive network run-cost?** Do linked pastures cost Data/tick just to collect, or is collection free (Data charged only on actions)?

## 5b · Economy (two currencies)
- **GPU** = *capital* — **crafted from mined MC items.** The item you sink into augments, Soul Tethers, and tab upgrades.
- **Data** = operating *fuel* — flows from rendering eggs; feeds the Daemon, and is the **per-action toll.**
- **The rule: every GPU-consuming action also costs Data** (GPU + Data together). Capital to buy the upgrade, fuel to install it — you can't progress by stockpiling one currency, the whole machine has to be running (mining for GPUs *and* rendering for Data).

## 5c · Data disks (Data's physical form)
Data exists as both a **player balance** AND a **floppy-disk item.** The **Notebook is the drive:** *write* balance → a blank disk (externalize Data), *read* a disk → balance (load Data to spend).
- **Why:** makes Data **craftable + tradeable** — a Data disk can be a literal crafting ingredient. E.g., the **GPU recipe = mined mats + a Data disk**, which is how the "GPU costs Data" rule gets paid at craft time.
- **Capacity tiers:** blank disks come in color-coded tiers (higher tier holds more Data), à la the Kernel tiers. ~7 sprites made.
- **Team Rocket "illicit Data" disk:** a special / black-market variant (role TBD — pure flavor vs. bonus-capacity-with-a-catch).
- **New items (my side):** the tiered blank disks + the Notebook read/write logic + a stored-Data tooltip.

## 6 · What this consolidates (the UI backlog collapses)
These stop being separate screens/blocks and become **Notebook tabs/functions:** BioBank browser (#33), EV allocator (#34), breeding-meta picker (#35), Daemon Compiler (#32), analytics dashboard (#6), egg culler (#17), harvester linking (BUG-008). The work becomes: **build the Notebook shell (tab strip + viewport skin) + each tab on the shared harness.**

## 7 · Build order (when greenlit — logic-first)
1. **Network link registry** (coords + linked set), headless-tested.
2. **Player-bound digital storage** (item→count, int-limit, capacity tiers), headless-tested.
3. **Background tick**: linked pastures → render (Data) + harvest (Storage).
4. **Notebook shell**: tab strip + viewport PNG-skin (grow `NotebookView`).
5. **Tabs**, in order: Storage + Pastures first (the new core), then the Augment Bench tabs (Compiler/Augmenter), then Dashboard/BioBank.

---

## 10 · Reference mock (2026-06-30)
Deuce's full React/JSX mock realizes the entire shell (title bar · tab strip · `gp://` command bar · status bar) + every tab in one prototype. **Build-oriented digest + exact palette + per-tab schemas:** `design/design_reference/notebook-console.NOTES.md`. Open reconciliations:
- **Tabs (mock):** BioBank · Harvester · Pastures · Compiler · Augmenter. Map "Harvester" → the **Storage** tab — the mock shows auto-collected **eggs**; confirm whether harvested loot/drops is the same tab or a second. **Dashboard** not mocked (icon staged).
- **EMC** shows as a status-bar currency (cyan) beyond Data — confirm it's a real 2nd currency (ties to the dark-economy) or drop it.
- All augment/buff/cost numbers in the mock are placeholders.

---

*Supersedes the standalone-block framing across BioBank / Harvester / Renderer / Compiler. See `COMPILER_UI_SPEC.md`, `KERNEL_AUGMENTER_SPEC.md` for the two Augment Bench tabs.*
