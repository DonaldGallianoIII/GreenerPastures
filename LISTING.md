# 🌱 Greener Pastures - Modrinth/CurseForge listing draft (rev 2026-07-06, post-Game-Corner)

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
  each tier also faster and droppier), pair your parents on named breeding lines. Lines ARE the
  breeding switch: an unwired pasture idles, and a warning chip tells you why instead of leaving you
  guessing.
- Eggs flow through **your node graph** - IV / EV / nature / shiny filters → **BioBank** (256/species,
  sortable by any stat) or **→ Data**. New lines come pre-wired to the BioBank so nothing is ever lost
  while you learn, and every mod recipe is in your recipe book from the moment you join.
- Drops trickle in passively from each mon's own drop table - plus **type-drops** (Fire types are your
  blaze rod farm; Ghost/Dark trickle echo shards) so a Cobblemon-only world is fully farmable.
- **Data** feeds your **Daemon** (16 buffs that go BEYOND vanilla enchant caps - Fortune past max,
  Vein Miner, Auto-Smelt, Magnet - rented per second) and **Soul Tethers** (amplifiers on your Kernel).
- Going big? Flip a pasture to **ghost mode** - the mons de-render entirely and keep breeding as
  pure data. Sixteen-pasture towers, zero entity soup, your TPS none the wiser.
- Walk away: drops **and** eggs accrue while chunks are loaded and **catch up the instant you return**
  (12h cap, online-time only - verified to the exact sweep, and honestly billed).

📸 *the node graph wiring a shiny filter into the BioBank*
📸 *the Dashboard: live session stats, shiny sparkline, MissingNo. odometer*

### 17 hidden rituals
Assemble the right Pokémon in one pasture and something happens. Every ritual is a **hidden recipe**
teased only by a riddle - *"Say it out loud."* · *"Three heads. Three skulls. The restless dead."* -
with gacha pulls, visible pity, and a dedicated spoils pool. Elytra from Shedinja's shed husk. A
jukebox band that plays random discs. A Shaymin-hosted feast that grows the mod's only enchanted
golden apples. Two-pasture collection quests: **all 27 starters** print Rare
Candies; **all 11 base fossils** wake a Sniffer Egg. And the **Black Market** fences Illicit Data
Disks - a corruption orb that Vaal-rolls your Kernel: blessed (augments past every cap, up to a
corruption-only **Tier III**), wild, nothing, or bricked. Forever.

📸 *the Rituals tab: locked riddle cards + a discovered recipe with pity meter*

### Breeding meta, in the UI
Nature Lock (all 25, stat hints), Ball Lock, targeted **EV spreads** (full 510 allocator), IV floors,
hidden-ability splice, egg moves, **hatch acceleration** - installed onto Kernels through the
**Augmenter** for GPU, upgradeable to level II (1.5× power, triple the slots - choices matter),
re-configured free. Name your Kernels; multi-kernel target cards; Masuda/Crystal-aware shiny
indicators; parent inspection down to OT. And because Cobbreeding ships the shiny-parent Crystal
bonus silently OFF, GP guarantees at least **×2** on eggs bred through its lines - server-tuned
higher values always win.

📸 *the EV allocator + nature picker*

### The Game Corner
Six arcade cabinets inside the Notebook, all server-authoritative: hidden tiles, decks, and trees
never leave the server, so no cabinet can be client-cheated. **DAEMON FLIP** is full Voltorb Flip -
deduction, push-your-luck, seven persistent levels; flip a multiplier and a *delighted* Pokémon
portrait greets you, flip a bomb and someone is devastated. **TREELINE** spooks a Scorbunny into the
woods - Zelda-pan to the forest, ten sweeps to find her, decoys snitch with arrows, and the pot
shrinks with every tree you rustle (deliberately pocket-change; it's the chill one). **TOP DECK**
fans twenty cards face-up, each wearing a random emotion portrait, slides them back, and hides ONE
survivor among strangers - up to five flips to find it, then a 2x / 6x / 20x let-it-ride ladder at a
fixed, auditable 25% per rung; lose, and *Mercy* offers a free one-shot memory check (which face was
that card wearing?) that refunds the wager and nothing more. **SLOTS** is the classic: three portrait
reels, triple angry Voltorb pays 100x, and the return-to-player is a fixed, enumerable **457/512**
(about 89%) - no config, no rigging, pinned by a unit test. **VIBE CHECK** is free to play: twelve
cards, four sour; every happy face doubles the pot (128 max) while a live odds panel shows exactly
how sour the next draw could be, and the first frown torches everything. **QUICK CLAW** is also
free: an ambient crowd of real walk-cycle sprites ambles by, one WANTED runner sprints across - tag
her and the payout scales with your reaction time, judged by the server on the click packet's
arrival against its own clock, so reaction time can't be spoofed.

All art comes from the community-run **PMD Sprite Collab**: 500+ portraits and 23 walk sheets, every
single one verified fan-made per emotion and animation - zero game rips ship (the famous faces whose
collab entries are CHUNSOFT rips got cut, Pikachu included), with 45 artists credited in the mod and
on the About card. Winnings are **Game Corner Coins** - the arcade's own closed currency. Coins never
convert to Data and Data never converts to Coins; the arcade only pays in prizes. The **Prize
Counter** redeems them mobile-style: six shelves per player, stock rotating every 15 real-time
minutes, plus every purchase refreshes all the shelves on the spot. Shelf art is the real Minecraft
item texture, the catalog runs to nearly 70 wares (effectively every Cobblemon held item), and the
crown jewel is the **Mystery Egg**: 1200 coins for a random species egg with at least two perfect
IVs and its hidden ability. Always.

📸 *the six-cabinet lobby (each cabinet is a screenshot in its own right)*
📸 *DAEMON FLIP mid-round - two happy flips and a nervous board*
📸 *the Prize Counter shelves + a Mystery Egg listing*

### The hunter's toolkit (bundled)
Shiny eggs **gold-glow in any container** - no more hover-checking a full box. A lifetime tally keeps
your real "1 in N" shiny rate. And **EggOracle**, the built-in odds planner, answers the only question
that matters: *at my egg rate, how many days to a shiny?* (eggs/hr → shinies/day → average-to-shiny,
Masuda-aware).

### Snack science
Merge whole Poké Snacks into one **Ultra Compressed Snack** - up to 9 bait effects, honest tooltips
(including the REAL spawn speed - the vanilla "Reduce Bite Time" tooltip lies; ours doesn't. And yes,
we fixed Cobblemon's hidden 2× speed cap: every bite-time berry now counts, up to 20× throughput). Charge a **Snack Repel** can with the berries of the type you
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
cores. One example: review found that an unreadable egg could once slip past IV filters - now an egg
that can't be decrypted is KEPT, enforced in four independent layers, because a shiny you can't read
is still a shiny. Even the Daemon's mining buffs are tuned so no single buff breaks the game - but
Mining Damage III + Haste III + your own pick, together, cross the deepslate instamine line. On purpose.
Eggs are bagged and tagged: every voided egg is listed with the exact filter that rejected it.

📸 *the /gp perf flame graph*

### The fine print (a.k.a. our promises)
- **No pay-to-win surface.** Every rate a server could sell back - drop rates, augment power, Data
  values - is baked into the mod, deliberately **no config**. (Buff availability and ritual tuning are
  admin JSON so server owners can *balance*, but there's nothing to zero-out-and-charge-for.)
- **Nothing is destroyed silently.** Shiny or unreadable eggs are ALWAYS kept - even if the egg data
  can't be decrypted, it's kept. Every render is logged in the void log. Full pastures pause, never discard.
- **All data stays local.** The analytics are yours, on your machine. Nothing phones home.

### Requirements
**Required** (client & server):
- Minecraft **1.21.1** · Fabric Loader **0.16+** · **Java 21** · Fabric API
- **Cobblemon 1.7.3** (we integrate deeply with pastures, snacks and spawning - 1.7.x only, the mod refuses to load on other majors rather than crash mid-game)
- **owo-lib 0.12.15+** (the console's fallback UI)

**Strongly recommended**:
- **Cobbreeding 2.2.x** - the breeding half of the mod activates with it (multi-pair Kernels, egg IV/nature reads). Without it, Greener Pastures self-disables those features with a friendly log line instead of crashing.
- **MCEF 2.1.6+** (client only) - renders the full Notebook console. Without it you get a simpler built-in UI; everything still works.

Nothing is bundled - install each as its own mod. Works in singleplayer and on dedicated servers (MCEF is only ever needed on clients).

*MIT licensed. Built by DonaldGalliano. Game Corner art: PMD Sprite Collab - 500+ fan-made portraits + 23 walk sheets, 45 artists, credited in CREDITS-PMD.md, in the jar, and on the in-game About card. Enjoying it? There's an optional Ko-fi (ko-fi.com/donaldgallianoiii) on the About card - it buys nothing in-game, ever.*

---

## Version naming
- First public: `1.0.0-beta.1` (rename jar from 0.1.0 at publish time - gradle.properties `mod_version`).
- Changelog lives in `CHANGELOG.md`.

## Publish checklist
- [ ] Icon (128×128) - needs art (Notebook + leaf motif?)
- [ ] 8-10 screenshots (marked 📸 above) - capture during the QA pass at 1080p+; the six Game Corner cabinets are prime material
- [ ] Specimen Disk sprite (placeholder green disk in) + any MissingNo-era disk art
- [ ] Modrinth: create project, slug `greener-pastures`, category Game Mechanics + Utility
- [ ] CurseForge mirror
- [ ] Cobbleverse discord post (server dev already approves)
- [ ] Tag dependencies on the platform (cobblemon required, cobbreeding/mcef optional-but-recommended)
- [ ] Q79 friend session (Masuda + fresh-eyes onboarding) before flipping public
