# 🌱 Greener Pastures - Modrinth/CurseForge listing draft (rev 2026-07-05, post-review)

> Paste-ready description for the mod page. Screenshots to capture during QA are marked 📸.

---

## Greener Pastures - A Data Science Mod

**Your Cobblemon breeding operation, run like a lab.** One item - the **Notebook** - is a real
in-game console (an actual web app rendered inside Minecraft). Link pastures to it and every egg
becomes *data*: filtered by IVs, nature, and shininess through a visual node graph, banked losslessly
in the BioBank, or rendered into **Data** - the currency that powers everything else. No chests, no
hoppers, no lag-farm entity soup.

📸 *hero: the Notebook console on a pasture view (breeding lines + node graph)*

### The loop
- **Link** a pasture, slot a **Kernel** (Copper → Greener: up to **8 breeding pairs in parallel**,
  each tier also faster and droppier), pair your parents on named breeding lines.
- Eggs flow through **your node graph** - IV / EV / nature / shiny filters → **BioBank** (256/species,
  sortable by any stat) or **→ Data**. New lines come pre-wired to the BioBank so nothing is ever lost
  while you learn.
- Drops trickle in passively from each mon's own drop table - plus **type-drops** (Fire types are your
  blaze rod farm; Ghost/Dark trickle echo shards) so a Cobblemon-only world is fully farmable.
- **Data** feeds your **Daemon** (16 buffs that go BEYOND vanilla enchant caps - Fortune past max,
  Vein Miner, Auto-Smelt, Magnet - rented per second) and **Soul Tethers** (amplifiers on your Kernel).
- Walk away: drops **and** eggs accrue while chunks are loaded and **catch up the instant you return**
  (12h cap, online-time only - verified to the exact sweep, and honestly billed).

📸 *the node graph wiring a shiny filter into the BioBank*
📸 *the Dashboard: live session stats, shiny sparkline, MissingNo. odometer*

### 17 hidden rituals
Assemble the right Pokémon in one pasture and something happens. Every ritual is a **hidden recipe**
teased only by a riddle - *"Say it out loud."* · *"Three heads. Three skulls. The restless dead."* -
with gacha pulls, visible pity, and a dedicated spoils pool. Elytra from Shedinja's shed husk. A
jukebox band that plays random discs. Two-pasture collection quests: **all 27 starters** print Rare
Candies; **all 11 base fossils** wake a Sniffer Egg. And the **Black Market** fences Illicit Data
Disks - a corruption orb that Vaal-rolls your Kernel: blessed (augments past every cap, up to a
corruption-only **Tier III**), wild, nothing, or bricked. Forever.

📸 *the Rituals tab: locked riddle cards + a discovered recipe with pity meter*

### Breeding meta, in the UI
Nature Lock (all 25, stat hints), Ball Lock, targeted **EV spreads** (full 510 allocator), IV floors,
hidden-ability splice, egg moves, **hatch acceleration** - installed onto Kernels through the
**Augmenter** for GPU, upgradeable to level II (1.5× power, triple the slots - choices matter),
re-configured free. Name your Kernels; multi-kernel target cards; Masuda/Crystal-aware shiny
indicators; parent inspection down to OT.

📸 *the EV allocator + nature picker*

### Snack science
Merge whole Poké Snacks into one **Ultra Compressed Snack** - up to 9 bait effects, honest tooltips
(including the REAL spawn speed - and yes, we fixed Cobblemon's hidden 2× speed cap: every bite-time
berry now counts, up to 20× throughput). Charge a **Snack Repel** can with the berries of the type you
DON'T want and bake it in: that type's snack spawns divide by up to ÷60. Sculpt your spawn pool.

### Your mons, as data
**Specimen Disks** archive a party Pokémon losslessly onto reusable media - IVs, nature, moves, OT,
shiny, everything - released with a right-click. Box clutter solved: keepers live on a shelf. And at
**one million Data rendered**, the Dashboard lets you summon **M̸i̷s̶s̴i̵n̷g̸N̵o̶.** - a glitch trophy
that rewrites its own species every few seconds and refuses all battles. One per million. Forever.

📸 *a shelf of Specimen Disks + MissingNo. mid-glitch*

### Obsessively hardened - and it shows its work
This mod **profiles itself** (`/gp perf flame` renders a browser-openable flame graph; measured idle
cost: ~0.1% of wall time). It was also **adversarially reviewed system-by-system** before release -
economy exploits, dupe surfaces, and edge cases hunted and closed, with 300+ unit tests on the pure
cores. Eggs are bagged and tagged: every voided egg is listed with the exact filter that rejected it.

📸 *the /gp perf flame graph*

### The fine print (a.k.a. our promises)
- **No pay-to-win surface.** Drop rates and costs are baked into the mod - there is deliberately
  **no config** a server can zero-out and sell back to you.
- **Nothing is destroyed silently.** Shiny or unreadable eggs are ALWAYS kept - even if the egg data
  can't be decrypted, it's kept. Every render is logged in the void log. Full pastures pause, never discard.
- **All data stays local.** The analytics are yours, on your machine. Nothing phones home.

### Requirements
- Fabric 1.21.1 · Java 21 · Fabric API
- **Cobblemon** (required) · **Cobbreeding** (strongly recommended - breeding features activate with it)
- **MCEF** (strongly recommended - the full console; without it a basic fallback UI is used)
- owo-lib

*MIT licensed. Built by Deuce222XX.*

---

## Version naming
- First public: `1.0.0-beta.1` (rename jar from 0.1.0 at publish time - gradle.properties `mod_version`).
- Changelog lives in `CHANGELOG.md`.

## Publish checklist
- [ ] Icon (128×128) - needs art (Notebook + leaf motif?)
- [ ] 6-8 screenshots (marked 📸 above) - capture during the QA pass at 1080p+
- [ ] Specimen Disk sprite (placeholder green disk in) + any MissingNo-era disk art
- [ ] Modrinth: create project, slug `greener-pastures`, category Game Mechanics + Utility
- [ ] CurseForge mirror
- [ ] Cobbleverse discord post (server dev already approves)
- [ ] Tag dependencies on the platform (cobblemon required, cobbreeding/mcef optional-but-recommended)
- [ ] Q79 friend session (Masuda + fresh-eyes onboarding) before flipping public
