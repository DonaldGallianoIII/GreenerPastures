# 🌱 Greener Pastures — Modrinth/CurseForge listing draft

> Paste-ready description for the mod page. Screenshots to capture during QA are marked 📸.

---

## Greener Pastures — A Data Science Mod

**Your Cobblemon breeding operation, run like a lab.** Link pastures to your Notebook — a real
in-game console — and every egg becomes *data*: filtered by IVs, nature, and shininess through a visual
node graph, banked losslessly in the BioBank, or rendered into Data that powers your Daemon's buffs.
No chests, no hoppers, no lag-farm entity soup.

📸 *hero: the Notebook console on a pasture view (Threads + node graph)*

### The loop
- **Link** a pasture, slot a **Kernel** (Copper → Greener: up to 8 breeding pairs), pair your parents in named **Threads**.
- Eggs flow through **your node graph** — IV / EV / nature / shiny filters → **BioBank** (256/species, sortable) or **→ Data**.
- Drops trickle in passively from each mon's own drop table — plus **type-drops and gacha rituals** with pity, so a Cobblemon-only world is fully farmable.
- **Data** feeds your **Daemon** (compiled buff loadout, drains per second) and **Soul Tethers** (rented amplifiers on your Kernel's augments).
- Walk away: drops **and** eggs accrue while chunks are loaded and **catch up the instant you return** (12h cap, online-time only — verified to the exact sweep).

📸 *the Daemon node graph wiring a shiny filter into the BioBank*
📸 *the Dashboard: live session stats, shiny sparkline*

### The console is real
The Notebook renders a full React app **inside Minecraft** (via MCEF/Chromium): BioBank browser, Harvester
storage, pasture health warnings, breeding dashboards, goals, an inbox for away-progress — and a built-in
**Field Guide** so you never need a wiki (every player gets one on first join).

### Breeding meta, in the UI
Nature Lock (all 25, with stat hints), Ball Lock, targeted **EV spreads** (full 510 allocator), IV floors,
hidden-ability splice, egg moves — installed onto Kernels through the **Augmenter** for GPU, re-configured
free. Masuda/Crystal-aware shiny indicators; parent inspection down to OT.

📸 *the EV allocator + nature picker*

### Obsessively optimized — and it shows its work
This mod **profiles itself**. `/gp perf` prints live millisecond timings for every hot path; `/gp perf flame`
renders a **flame graph** you can open in your browser. Idle cost is engineered to ~zero: the console pipeline
fully stands down when closed, the server never re-sends unchanged data, and saves only re-encode what changed.
184-pasture farms are the design target, not the failure mode.

📸 *the /gp perf flame graph*

### The fine print (a.k.a. our promises)
- **No pay-to-win surface.** Drop rates and costs are baked into the mod — there is deliberately **no config**
  a server can zero-out and sell back to you.
- **Nothing is destroyed silently.** Shiny or unreadable eggs are always kept; every render is logged in the void log.
- **All data stays local.** The analytics are yours, on your machine. Nothing phones home.

### Requirements
- Fabric 1.21.1 · Java 21 · Fabric API
- **Cobblemon** (required) · **Cobbreeding** (strongly recommended — breeding features activate with it)
- **MCEF** (strongly recommended — the full console; without it a basic fallback UI is used)
- owo-lib

*MIT licensed. Built by Deuce222XX.*

---

## Version naming
- First public: `1.0.0-beta.1` (rename jar from 0.1.0 at publish time — gradle.properties `mod_version`).
- Changelog lives in `CHANGELOG.md`.

## Publish checklist
- [ ] Icon (128×128) — needs art (Notebook + leaf motif?)
- [ ] 4–6 screenshots (marked 📸 above) — capture during the QA pass at 1080p+
- [ ] Modrinth: create project, slug `greener-pastures`, category Game Mechanics + Utility
- [ ] CurseForge mirror
- [ ] Cobbleverse discord post (server dev already approves)
- [ ] Tag dependencies on the platform (cobblemon required, cobbreeding/mcef optional-but-recommended)
