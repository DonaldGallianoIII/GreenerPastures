# Changelog - Greener Pastures

## 1.0.0-beta.2 (2026-07-22)

### Soul Tethers actually work now (the Loom)
- **beta.1 shipped Soul Tethers with no way to use them** - craftable, but nothing could inscribe
  a blank and the slot GUI was orphaned by the console migration. Both doors now exist:
- **The Loom** - a new Notebook tab, the tether's own bench (Compiler=Daemon, Augmenter=Kernel,
  Loom=Tether): select a tether from your inventory, inscribe [function · tier] for Data
  (100/400/900 by tier; +10/20/30% to the matching Kernel mod). Book-style re-inscription:
  wiping refunds half, so experimenting always has a real cost and flipping never profits.
- **TETHERS row on the pasture screen** - right where you slot the Kernel: your tier's unlocked
  functional slots as cells; click an empty cell to slot an inscribed tether from your inventory,
  click a filled one to take it back. Blanks refuse (visit the Loom first). The already-shipped
  runtime (amplification, Data burn while the Daemon is fed, drain billing) lights up unchanged.
- Tether tooltips now tell the truth about where to go.

### Soul Tether economy v2 (same dev cycle, after live QA)
- **Additive, past the cap**: a Tier-N tether now ADDS N flat levels on top of its Kernel's matching
  mod - deliberately beyond the augment's rollable max, stacking across tethers (each level = half a
  level-I install: Shiny +15%/lv, Drop Rate +1.00%/lv, Enrichment +10%/lv, Speed/Yield/Hatch +1 level;
  Speed and Hatch ladders extend to 6, tether-only territory). EV and IV Floor retired as targets.
- **Rent, per second**: quality 0.5 × tier, throughput 0.2 × tier Data/s - charged only while the
  tether sits on a linked pasture with mons inside a loaded chunk. Away catch-ups are PRE-PAID: the
  window's rent is checked and debited BEFORE the boost applies; can't pay = nothing charged, the
  burst runs at base mods. No surprise bills, no debt, no free boosts.
- **TETHER FX row** on the pasture screen: before → after per affected mod, a STARVED dim when Data
  can't cover the rent, and an explicit "idle - install the matching augment" warning.
- Soul Tethers are **nameable** at the Loom (like Kernels); names show everywhere the tether does.

### Fixes
- Shop eggs (Mystery Egg + Prime Egg) now always hatch **base evolution forms** - no more Politoeds
  out of the incubator; pool deduped per line, filters applied to what actually hatches.

### The Compression Press
- **New BioBank mechanic**: press **100 banked eggs of one species** into a permanent
  **+5% drop rate** for that species across every pasture you own - and it **stacks forever**
  (20 presses = ×2.0). The multiplier scales the whole drop proc your Kernel/Tether stack
  already computed, so it composes with Drop Rate / Drop Yield mods instead of replacing them.
- The press always eats the **worst 100** eggs (lowest IV total) and **never touches a shiny**;
  all-or-nothing, so it can never half-eat a bucket. Constants are baked (no config - the
  anti-p2w rule).
- BioBank capacity raised **256 → 1024 eggs per species** - a press habit needs stock.
- Press from the BioBank tab: every species row has a ⭓ press button + confirm modal showing
  current → next multiplier; presses land in the sweep audit log (`comp` / `comp_x` fields).
- **The SERVER press**: donate your 100-egg pulls to a communal pool instead - every **1000
  pooled eggs** of a species (from anyone) is a further **+1% drop rate for EVERYONE**, stacking
  forever and applied as a **"more" multiplier** (`your presses × server press`, never added).
- **The donation feed**: no chat spam - donations land in a separate section of the Inbox tab
  (who fed what, tier crossings highlighted with the donator's name). A global 24h rolling
  window, hard-capped in memory; go look when you want to, ignore it when you don't.

### Drops - gold for the gold Pokémon, and a Drop Yield that actually pays
- **Gimmighoul, Gholdengo and Persian now drop real gold.** A new per-species drop overlay layers
  `gold_ingot` onto their tables (Gimmighoul 1-2, Gholdengo & Persian 0-1) - the literal gold Pokémon
  dropped only relic_coin before, and gold was the single biggest farmability hole in a vanilla-mob-free
  world. Persian's ingot rides alongside its native gold_nugget; both obey Drop Rate and Drop Yield.
- **Drop Yield now widens EVERY drop's quantity**, including fixed percentage-only entries. A 5%-chance
  single Quick Claw now rolls a range when it procs instead of a flat 1 - every entry's ceiling lifts by
  the yield bonus, the floor never moves (only ever a chance at MORE).
- **Fix:** a Drop Yield augment at level II no longer misreads as the corruption-only tier III (its
  level-II and level-III magnitudes collided and painted a perfectly good Kernel "corrupted").

### Compression - bulk presses, whole evolution lines, and a smarter split
- **Compress All / Donate All** - one click clears every full 100-egg batch of a species instead of
  100-at-a-time; the single-100 buttons stay. All-or-nothing per batch, so nothing is ever half-eaten.
- **Presses now count for the whole EVOLUTION LINE.** Eggs are always base-form, so a Meowth press now
  boosts a tethered Persian, Gastly boosts Haunter and Gengar, every Eeveelution rides its Eevee press.
- **The press bonus is split 50/50** - half amplifies drop RATE (how often, capped at certainty), half
  amplifies drop YIELD (how much, uncapped) - so a hyper-compressed species keeps paying after the
  100%-per-sweep rate wall. A 2× press = ×1.5 more often AND ×1.5 bigger, expected-value rounded.

### Field Guide
- New **"Drop rate, yield & the cap"** card - the once-a-minute sweep, the per-mon drop chance, the
  100% cap, and how Drop Yield and the compression split get around it, in plain terms.
- Compression card corrected: the +5% now reads as an evolution-line bonus split across rate and yield.

## 1.0.0-beta.1 (first public release)

Everything below ships in the first public beta. 369 unit tests; adversarially reviewed
system-by-system pre-release plus a final multi-agent audit of money paths, dupes, tick
perf and lifecycle; live-QA'd in singleplayer and on a dedicated server. The Notebook
console renders through MCEF (without it: a friendly install prompt - all server-side
systems run regardless). Server operators: config/greenerpastures/pastures.json.

### The Notebook
- One item is the whole mod: a real web-app console rendered in-game (MCEF/Chromium, with a
  built-in fallback UI when MCEF isn't installed). Gifted on first join; craftable for more.
- 11 tabs: BioBank · Harvester · Pastures · Compiler · Augmenter · Dashboard · Inbox ·
  Rituals · Specimens · Game Corner · Guide. New players land on the Guide; all recipes are in the recipe
  book from join #1.

### Breeding
- Kernels breed up to **8 pairs in parallel** per pasture (Copper 2 → Greener 8; each tier
  faster and droppier). Breeding lines are wired in a visual node graph - lines are the ONLY
  thing that breeds; new lines come pre-wired to the BioBank so nothing is lost while learning.
- Eggs route through IV / EV / nature / shiny filters → BioBank (256/species, lossless) or
  render into **Data**. Shiny or unreadable eggs are NEVER auto-culled, enforced in four
  independent layers.
- **12 augments**: Shiny reroll, Speed, Hatch Haste, IV Floor (shuffles WHICH stats land
  perfect), EV Primer (510 allocator), Nature Lock, Ball Lock, Hidden Ability, Egg Moves,
  Enrichment, Drop Rate, Drop Yield. Level II = 1.5× at 3 slots; **Tier III is corruption-only**.
- **Crystal guaranteed**: Cobbreeding ships the shiny-parent bonus off - GP floors it at ×2
  (server-configured higher values win). Masuda/Crystal badges live on every line.
- Soul Tethers (rented amplifiers), 12h away catch-up (honestly billed per brood), kernel
  rename, multi-kernel target cards, ghost pastures (mons breed as pure data - zero entities).
- 2.5-minute brood floor regardless of stacking (server protection); pasture health chips
  explain every quiet farm (unlinked / no Kernel / no lines / half a line / tray full / bank full).

### Rituals & harvest
- **17 hidden gacha rituals** teased by riddle cards - first assembly reveals the recipe
  forever; visible soft/hard pity that survives everything. Collection quests span pastures.
- The Black Market fences corruption orbs: Vaal-roll a Kernel - BLESSED / WILD / nothing /
  BRICKED, forever.
- Linked pastures trickle every mon's real Cobblemon drop table + type-drops (fire → blaze
  rods, ghost/dark → echo shards, fairy/psychic/rock → amethyst...) - a mobless world is
  fully farmable, including Soul Tether + Daemon materials.

### Economy
- Rejected eggs render into **Data**; disks (byte → TB) make it physical and tradeable; GPU
  is the install reagent. The **Daemon** rents 16 buffs beyond vanilla limits (Fortune past
  max, Looting from nothing, Vein Mine, Auto-Smelt, Magnet, XP...) - per-second upkeep,
  PvP-neutral, never writes your gear.
- Anti-p2w: drop rates, augment power and Data values are baked constants - no config knob
  exists. Buff availability + ritual tuning are admin JSON (balance levers, not paywalls).

### Snacks
- **Ultra Compressed Snack**: merge up to 9 bait effects, 6 copies each - with truthful lore
  including REAL spawn speed (Cobblemon's hidden 2× throughput cap is fixed; every bite-time
  berry counts, fenced at 20×).
- **Snack Repel**: charge a can with the berries of the type you DON'T want (÷10..÷60,
  stacks to ÷120) and bake it in - sculpt your spawn pool.

### Game Corner
- Six arcade cabinets (DAEMON FLIP, TREELINE, TOP DECK, SLOTS, VIBE CHECK, QUICK CLAW), all
  server-authoritative - hidden tiles, trees, decks, and reaction clocks never leave the server.
  **DAEMON FLIP**: full Voltorb Flip, 7 levels, persisted machine level. **TREELINE**: find the
  Scorbunny in ten sweeps; decoys snitch with 8-way arrows; payout falls per sweep.
- Cabinets pay **Game Corner Coins** - the arcade's own currency, never convertible to Data -
  spent at the **Prize Counter**: six shelves per player, stock rotating every 15 real minutes
  from a catalog of nearly 70 wares - balls, potions, EVERY held item (type boosters, power items, choice
  gear, Destiny Knot...) and the **Mystery Egg**: a random species with at least 2 perfect IVs
  and its hidden ability, always. PMD Sprite Collab art, fan-made only, credited in CREDITS-PMD.md.

### Trophies
- **Specimen Disks**: archive party mons losslessly onto reusable media; release by
  right-click; dupe-proof rails; trade them like cartridges.
- **MissingNo.**: render one million lifetime Data → summon a glitch trophy that rewrites its
  species every ~5 s (Ditto/Aerodactyl/Haunter/Marowak/Kabutops), refuses battles and
  pastures, survives PC/trades/disks. One per million, forever.
- Bundled hunter tools: shiny-egg gold-glow highlighter + lifetime "1 in N" tally, and the
  EggOracle odds planner (eggs/hr → shinies/day → average-to-shiny).

### Observability
- `/gp perf` self-profiler with browser-openable flame graphs; JSONL event log; every voided
  egg listed with the exact filter that rejected it. Idle cost ≈ 0 (console pipeline stands
  down when closed; unchanged data is never re-sent).

### Requirements
- Fabric 1.21.1 · Java 21 · Fabric API · **Cobblemon 1.7.x** (range-pinned) · owo-lib.
- Recommended: **Cobbreeding 2.2.x** (breeding features self-disable cleanly without it) ·
  **MCEF** (client-only, full console; fallback UI without). Nothing bundled.
