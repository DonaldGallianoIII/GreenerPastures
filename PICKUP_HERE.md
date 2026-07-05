# 🎯 PICKUP_HERE — session handoff (rewritten 2026-07-05, post QA-marathon)

> **READ THIS WHOLE FILE FIRST** after a /clear. This + the memory dir + repo docs replace all context.
> Yesterday (07-04) was a monster: full QA day with Deuce live + two feature builds. Commits `81a1a4d`,
> `0a73bab`, + tonight's overdrive commit. 282 unit tests green.

---

## 0 · WHAT IS HAPPENING RIGHT NOW

- **Jar `d5a11066` is DEPLOYED** (md5-verified, zip OK) to the instance mods dir — Deuce has NOT booted it
  yet (he ended QA for the night on `fd4d69d7`). It adds Snack Overdrive pt.2 (spawn-speed credit queue) on
  top of everything below. First boot = first live test of pt.2.
- **QA mode is PERMANENT** via `config/greenerpastures/qa.flag` (marker file in the instance). NEVER fight
  CurseForge's JVM-args UI again — CF rewrites `minecraftinstance.json` from its internal DB at launch and
  silently discards file edits (burned us 3× on 07-04 before the marker-file switch shipped).
- **Deuce's parting state**: "stepping away from QA for the night, RC enabled for further coding" — i.e.
  autonomous dev is green-lit; QA resumes when he says so.

## 1 · THE PROJECT (unchanged)

**Greener Pastures — "A Data Science Mod"** (Fabric 1.21.1, Java 21, MIT, id `greenerpastures`): Cobblemon/
Cobbreeding companion. Notebook = the whole UI (React in-game via MCEF, DsBridge WS :25599). Kernels breed
multi-pair; eggs → data → BioBank keep or render to Data; Daemon buffs + Soul Tethers burn Data; GPU pays
installs; disks = physical Data; hidden gacha rituals + type-drops make the mobless world farmable.
Repo `~/pokemon-prediction`, mod `greener-pastures/`, React `greener-pastures-ui/` (vite singlefile → jar).

## 2 · WHAT LANDED 2026-07-04/05 (all in the deployed jar)

**Features:**
- **Snack Repel (Overdrive pt.1, Deuce's charged-can design + his sprite)**: glass×6+ingot×2+wart → 2 empty
  cans · can + 1–6 SAME-species typed berries (any grid) → charged can `🚫 ÷N Xxx Types` (count scales,
  6-copy cap; `SnackRepelChargeRecipe`) · charged can(s) into the Ultra Snack craft → snack carries
  `gp:repel_types` (per-type sums, cap 2× strongest can — `SnackRepelMath`). `PokeSnackOverdriveMixin`
  persists payload on the block + joins `GpRepelInfluence` **at spawner creation** (Cobblenav reads
  `getSpawner().getInfluences()` — lazy join made the Nav miss it; Deuce caught it Nav-in-hand). Logs:
  `repel armed` (INFO) + `repel divide` (DEBUG ≤1/5s).
- **Snack speed credit queue (Overdrive pt.2, tonight, UNTESTED in-game)**: vanilla rot (ONE random
  bite_time entry, hard 2× cap) replaced via `randomTick` HEAD-cancel: every copy counts multiplicatively
  (`SnackSpeed`, Π(1-v), ×0.1 fence), fractional credits carry, burst cap 3, credit cap 8 while no player,
  NBT-persisted, fail-soft to vanilla on any error. Truthful `⚡ spawn speed ×N.N` lore on the ultra craft.
- **Professor's Summit (first SPANNING ritual)**: all 27 starters ≥1 across the UNION of TWO linked
  pastures (16-slot pastures make the span mechanically forced) → rare candy 3%/soft 40/hard 80. Engine:
  `pastureSpan` on Ritual (back-compat clamp), `Composition.union`, player-level pity in RitualLedger
  (`spanState`), sweep-snapshot pairing (5-min fresh, never guesses unloaded rosters), `SpanGate` banks
  exactly once per satisfied pair. Auto-merges into his rituals.json on boot.
- **QoL set**: kernel right-click → rename screen (name → tooltip/target card/pasture KERNEL row) · Kernel/
  Daemon **target selector cards** in Augmenter/Compiler (per-player slot target, revalidated every use,
  falls back to first-found) · Rituals tab **per-card spoils tiles** (bottom pool = undiscovered orphans
  only) · `⟳ N pulls` counters · full-loadout kernel tooltip · timestamped flame graphs · `ev_spread` +
  `iv_floor` breeding log lines.

**Fixes (all QA-caught live)**: BUG-009 hooks-order black screen (CRITICAL) · BUG-010 IV floor always
hp/atk/def → shuffled · BUG-011 ghost pairing entries → breeder self-heal · BUG-012 prefetch downgraded the
focused pasture's roster ("can't breed" false alarms) → stats backfill · BUG-013 kernels stacked ×16, one
augment payment enchanted all (CRITICAL) → maxCount 1 · BUG-014 slotted-kernel tooltip showed tier defaults
→ display stack dressed from extras channel. Details in QA_RESULTS.md.

**Recipe revs (Deuce's picks)**: GPU = 4 quartz blocks corners + 2 redstone blocks N/S + 2 iron blocks E/W
+ kilobyte disk center · blank disk = 2 iron ingots + quartz block + redstone + paper · copper kernel =
4 copper blocks corners + 4 quartz blocks cardinals + blank disk center.

## 3 · QA STATE (queue = QA_PENDING.md, `glow` it)

~30 rows closed 07-04 (breeding-meta suite fully hatched-verified, rituals v2 end-to-end incl. discovery/
pity/exactness/spoils, GPU gates, disks, idle-off/change-gate/catch-up perf, snacks, flame graphs).
**OPEN**: Q39/Q40/Q41 (health visuals) · Q46-half (REMOVE) · Q48 · Q50 (umbrella) · Q55 · Q58 (Compiler ◈)
· Q61 (listing 📸) · Q62 re-QA (new recipes) · Q65 · Q72 (per-card spoils) · Q73 · Q74 (corruption — he has
~29 illicit disks banked!) · Q76 (long-soak) · Q79 (two-player Masuda, PRE-PUBLISH GATE) · Q80 selector ·
Q81 rename · Q82 summit · Q83 repel (Nav = the verification tool: chances crater ÷N; re-place any snack
placed pre-fd4d69d7) · Q84 speed queue (plain-vs-golden-apple spawn counting). BUG-013/014 verify post-boot.
Perf anchor: 0.087% of wall time over a 90-min AFK window; watch item = single breeder.scan max spike
(76→129ms, once per session, likely first-scan cold).

## 4 · NUMBERS DELTA (rest unchanged from CHANGELOG/old docs)

- Repel: charge = berries × per-copy typing value, 6-copy cap (chilan ÷10 … ÷60); snack merge cap 2× strongest
  can (÷120 ceiling). Snack-spawner scope only. Hard-ban (×0) tier NOT built — Deuce never picked.
- Speed: interval = 2 random ticks × Π(1-biteValue), fence ×0.1 (=20× throughput max). 6 golden apples
  (0.25 ea) = ×5.6 throughput — BEATS 6 EGAs (0.1 ea = ×1.88). Random tick ≈ 1/68s per block at default 3.
- Summit: 27 starters (bulbasaur…quaxly, 3×9 gens), rare candy 1× @3%, soft 40, hard 80, span 2.
- Cobbreeding config: masuda 4.0 ON, `crystal: 1.0 = OFF` (open lever — 2.0 was the grid assumption; his
  file, his call). OT self-testing impossible → Q79 friend session.

## 5 · OPEN LEVERS / BACKLOG (memory: gp-dev-backlog + snack-repellent-idea)

1. Data onboarding trio (daemon cost / render value / starter Data) — Deuce owes numbers.
2. Crystal multiplier (his cobbreeding file) — set 2.0 + restart to light the 💎 chip.
3. Field-guide removal (decided; open: what first-joiners get — recommended: the Notebook).
4. Echo shards + amethyst as pasture type-drops (progression gate: tether/daemon need echo in a mobless
   world) — needs his type mapping + rate sim.
5. Mon compression → Specimen Disks (the big one, own batch).
6. Publish tail: icon, LISTING.md 📸 shots, version bump, CHANGELOG, Q79 friend session.

## 6 · WORKFLOWS (unchanged, key bits)

Build: `cd greener-pastures-ui && npm run build` (if React touched) → `JAVA_HOME=~/jdks/jdk-21.0.11+10
greener-pastures/gradlew -p greener-pastures build` from repo root (read REAL output; verify test XML
counts). Deploy ONLY with the game closed (md5 both sides + ziptest); watch for **zombie javaw** (8GB
javaw lingering after window close — JCEF; taskkill it, then re-copy). Logs: instance `gp-logs/latest.log`
(JSONL, rotates per session — check `gp-*.log` archives before calling an event missing!). Decompile:
python zipfile + `java -jar ~/cfr.jar` (also used on cobblenav-fabric-2.3.3 — its SpawnDataHelper reads the
snack spawner's influence list, so the Nav IS a valid repel/attract verifier).

## 7 · NEW TRAPS LEARNED 07-04 (on top of the old list — jar-over-running-game, gradle greps, tuple-6,
insertStack, React TDZ, SP statics, preview-lying recipes, test-tuned cobbreeding config)

- **CurseForge owns minecraftinstance.json** — rewrites it from internal DB at launch. JVM args via file
  edits NEVER stick. The qa.flag marker file is the answer (GpLog.computeQaMode).
- **React hooks after early returns** = black screen that survives screen reopens (preloaded MCEF page
  keeps the dead root). Audit: hooks above ALL conditional returns, always.
- **Item components are STACK-wide** — anything augmentable must be maxCount(1) (BUG-013).
- **Client display stacks lie** — a reconstructed ItemStack shows default components; dress it from a
  server channel (BUG-014 pattern).
- **Lazy influence joins lose races** — anything reading spawner.getInfluences() (Cobblenav!) sees only
  what's there at spawner creation. Join in the creation lambda (`spawner_delegate$lambda$0`).
- **Log rotation**: latest.log is THIS session only; grep gp-*.log before declaring an event unlogged.

## 8 · DEUCE (unchanged) — tight replies, he tests personally, offer the board + let him pick, tables with
real numbers, observability-first (read logs before theorizing), no p2w config knobs, hidden content stays
hidden, loves chaining systems. When he says "check" — read the log.

## 9 · DOCS: QA_PENDING.md (queue) · QA_RESULTS.md (BUG-001…014) · CHANGELOG.md · LISTING.md ·
OBSERVABILITY.md · RITUALS.md · specs per feature · memory dir has all standing rules.
