# 🌱 Greener Pastures - Feature Showcase
_Compiled 2026-07-05 from a full code survey; current as of jar ceb9fb9a, 305 tests, post-adversarial-review. The share-with-people list._

**One item is the whole mod: the Notebook.** Right-click → a real web app (React via MCEF/Chromium) running inside Minecraft. Every player gets one on first join.

## 📓 The Notebook Console - 10 tabs
- **BioBank** - eggs as searchable data: grouped by species, sortable by ΣIV/stat/shiny; egg cards (IVs/EVs/nature/gender); pull any egg back as a real hatchable item
- **Harvester** - auto-collected drops from linked pastures; L/⇧/R withdrawal, space-aware
- **Pastures** - network overview: tier, eggs, pairs, ⚠ health badges per pasture
- **Compiler** - Daemon buff loadout: −/+ tier steppers, GPU costs, live drain + runtime math, power toggle
- **Augmenter** - Kernel augment bench: pickers, slot pips, GPU gates, ⛧ CORRUPT button, target cards
- **Dashboard** - live analytics: eggs/min chart, kept-vs-voided donut, per-tier bars, shiny %, 💾 Disks card
- **Inbox** - dismissible away-progress + event feed with unread badge
- **Rituals** - hidden-recipe puzzle board + per-ritual spoils tiles
- **Specimens** - archive party mons onto disks
- **Guide** - full onboarding manual in-game

## 🧬 Breeding Engine
- **Multi-pair breeding** - a Kernel breeds up to **8 pairs in parallel** per pasture
- **Kernel ladder** (wrap the previous tier in blocks): Copper 2 → Iron 3 → Gold 4 → Diamond 5 → Netherite 6 → **Greener 8** pairs; egg speed ×1.1→×1.6; drops +0.5%→+5%
- **Visual egg pipelines** - node-graph editor per breeding line: parents → IV/EV/Nature/Shiny filters → BioBank (keep) or Data (render); pan/zoom/wire, Masuda/Crystal badges, mon inspector
- **12 augments, 3 tiers** - Shiny reroll, Speed, **Hatch Haste** (the last un-automated step, automated), IV Floor, EV Primer (510-budget allocator), Nature Lock (25), Ball Lock, Hidden Ability, Egg Moves, Enrichment, Drop Rate, Drop Yield. Level II = 1.5× effect but **3 slots total** (a Copper kernel can't even hold one - slot pressure is the governor); **Tier III exists and corruption is its ONLY door** (2× effect, ⛧III badge). GPU install costs (quality 2◈ / throughput 1◈), re-pick free, Speed refuses for free if the floor would eat it
- **Soul Tethers** - rented +10/20/30% amplifiers burning Data per cycle while the Daemon is fed
- **Away catch-up** - missed broods roll exactly on return (12h cap) → Inbox report
- **Kernel rename + target cards** - name rigs; choose which kernel/daemon a tab edits
- 2.5-min brood floor; shiny proc mathematically bounded; corrupted kernels locked forever

## ⛏ Harvest Economy (mobless-world friendly)
- Linked pastures trickle each mon's **real Cobblemon drop table** 1×/min - no combat, no ground items (base 3%/mon/min + kernel/tether bonuses)
- **Type-drops**: Fire→blaze rods, Ghost/Dark→**echo shards**, Fairy/Psychic/Rock→**amethyst**, Ice→ice, Grass→sugar cane… (admin-editable table)

## 🎰 Rituals - 17 hidden gacha recipes
- Secret compositions; first assembly = discovery pop, recipe revealed forever; 1 pull/sweep with soft/hard pity - **visible on every learned card** (⏳ 213/400) and it persists through everything
- Locked rituals show **riddle cards**: "Say it out loud." (8 Shuckle → Shulker Shells) · "Three heads. Three skulls. The restless dead." (Nether Star) · "Prepare for trouble. Make it double - twice." (Black Market)
- Highlights: Elytra (Shedinja's husk + 6 any-mix beetles) · Trident (shipwreck-graveyard crew) · Totem (Sableye+Blissey) · jukebox ritual (random disc per hit, 18-disc pool) · Ominous Bottle (true Alolan/Galarian syndicate - form-gated) · Wither supply chain (skulls trickle, Star crawls)
- **Two-pasture collection quests**: all 27 starters → Rare Candy · all 11 base fossils → Sniffer Egg
- **Black Market → Illicit Data Disk → ⛧ corruption**: Vaal-roll a Kernel - 30% BLESSED (climbs an augment I→II→**III**, past every slot cap - the only path to Tier III) / 25% WILD (drop mod ×2, +1 pair, or on a Greener: pushes an augment past the mortal ceiling) / 25% nothing / 20% bricked; corrupted forever
- Tiers: LOW ~3 hits/hr · MID ~1.5 · HIGH ~0.7 · APEX multi-hour

## 💾 Data Economy
- Rejected eggs **render into Data** (player-bound currency; Enrichment boosts value)
- **Data disks**: byte 8 · kB 1,024 · MB 16,384 · GB 262,144 · TB 4,194,304 - write, trade, read back; media survives
- **GPU** reagent (craft eats a kilobyte disk)
- **Daemon**: 16 buffs beyond vanilla enchant caps - Fortune/Efficiency/Looting past max, Vein Miner (96 cap), Auto-Smelt, Magnet, XP +25%/tier, Mining Damage, Haste, Saturation, Potion Duration, more - rented per-second from Data; PvP-neutral (no combat enchants); never writes gear
- Anti-p2w: all rates baked constants, deliberately no config knobs

## 🍰 Snack Science
- **Ultra Compressed Snack** - merge snacks into one mega-bait: up to 9 effects (3× the pot cap), double-additive (≤6 copies/effect), truthful lore incl. REAL spawn speed
- **Snack Repel** - charge a can with the berries of the unwanted type (1–6, ÷10…÷60) → bake in → that type's snack spawns divided; stacks to ÷120
- **Spawn-speed overdrive** - fixes Cobblemon's hidden 2× cap: every bite-time berry counts multiplicatively, fenced at 20× throughput, credit-banked while away

## 💽 Specimen Disks
- Party mon → lossless data on reusable media (blank disk + amethyst + echo shard); Notebook Specimens tab archives, right-click releases (party→PC), blank returned
- Dupe-proof rails: no mid-battle, never your last mon, landing verified before removal

## ▓▒░ MissingNo. - the endgame trophy
- Render **one million lifetime Data** → the Dashboard's glitch card lets you **summon MissingNo.** - repeatable every million, forever
- A real party Pokémon that **rewrites its own species every ~5 seconds** - Ditto → Aerodactyl → Gastly → Marowak → Kabutops, never the same twice (the fossils and the ghost, as the original sprite corruption intended)
- **Pure trophy**: it refuses all battles ("MissingNo. distorts the battlefield"), never shiny, nicknamed, and its glitch survives PC storage, trading, even Specimen Disks
- The odometer counts only genuinely rendered eggs - no disk tricks, no shortcuts. ~87 hours of maxed 5-pasture uptime per million. Flex accordingly

## 🛡 Trust & QoL
- **Sacred rule**: shiny/unreadable eggs NEVER auto-culled - even a failed decrypt is kept; full storage pauses production, never discards
- **Void log**: every rejected egg listed with the filter that rejected it
- **Breeding goals**: /gp goal or Dashboard - live hunt progress + completion ping
- Never-lose withdrawals; first-join Notebook gift; Pokémon-egg cake (community request 🎂)
- /gp perf self-profiling (flame graphs), JSONL observability log, 305 unit tests
- **Adversarially reviewed pre-release**: six independent system audits (edge cases / perf / new-player UX); every finding fixed or consciously accepted (REVIEW_FINDINGS.md)
- New players land on the **Guide tab** first, new breeding lines come **pre-wired to the BioBank**, and full pastures send an Inbox warning - the sharp edges got sanded
