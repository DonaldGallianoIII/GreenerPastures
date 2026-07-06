# Changelog - Greener Pastures

## 1.0.0-beta.1 (first public release)

Everything below ships in the first public beta. 321 unit tests; adversarially reviewed
system-by-system pre-release; live-QA'd in singleplayer and on a dedicated server.

### The Notebook
- One item is the whole mod: a real web-app console rendered in-game (MCEF/Chromium, with a
  built-in fallback UI when MCEF isn't installed). Gifted on first join; craftable for more.
- 10 tabs: BioBank · Harvester · Pastures · Compiler · Augmenter · Dashboard · Inbox ·
  Rituals · Specimens · Guide. New players land on the Guide; all recipes are in the recipe
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
