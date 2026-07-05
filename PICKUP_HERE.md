# 🎯 PICKUP_HERE - QA-session handoff (rewritten 2026-07-05 evening, pre-compact)

> **READ THIS WHOLE FILE FIRST** after a /clear or compaction. Deuce is hopping on his computer to
> run a QA session RIGHT AFTER this compact - be ready to watch logs and verify rows immediately.

---

## 0 · WHAT IS HAPPENING RIGHT NOW

- **Jar `ceb9fb9a` is DEPLOYED** (md5-verified, zip OK) to
  `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/mods/greenerpastures-0.1.0.jar`.
  Deuce has NOT booted it yet. It contains EVERYTHING: two days of features + the full adversarial-review
  fix set (3 rounds, zero open findings - see REVIEW_FINDINGS.md).
- **QA mode is automatic** via the `config/greenerpastures/qa.flag` marker file (never touch CurseForge's
  JVM args - CF rewrites minecraftinstance.json from its internal DB and silently discards edits).
- **The QA queue is the job**: open rows in QA_PENDING.md (glow it). Watch
  `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/gp-logs/latest.log` (JSONL;
  rotates per session - check gp-*.log archives before calling an event missing). When Deuce says
  "check" - read the log. First line must show `minLevel:DEBUG`.
- **Working tree clean**, all committed through `7f22f3a`. 305 unit tests green.

## 1 · THE PROJECT IN ONE PARAGRAPH

**Greener Pastures - "A Data Science Mod"** (Fabric 1.21.1, Java 21, MIT, id `greenerpastures`):
Cobblemon/Cobbreeding companion for Deuce (Deuce222XX). The **Notebook** is the whole UI - a React app
in-game via MCEF (DsBridge WS :25599). Kernels breed up to 8 pairs; eggs route through a node graph to
BioBank (keep) or render to **Data**; Data feeds Daemon buffs + Soul Tethers; GPU pays installs; disks
are physical Data; 17 hidden riddle-hinted gacha rituals + type-drops make the mobless world farmable;
Specimen Disks store mons as data; MissingNo. is the 1M-lifetime capstone. Repo `~/pokemon-prediction`,
mod `greener-pastures/`, React `greener-pastures-ui/`.

## 2 · THE OPEN QA QUEUE (verify from the log wherever possible)

**Older rows still open**: Q39/Q40 (health chips/badges) · Q41 (BioBank-full via `/gp breed interval 15`)
· Q44 egg-half (`breeding ev_spread` log) · Q46 REMOVE-half · Q48 LOADOUT chips · Q50 umbrella ·
Q53 chat-table half · Q55 cross-dim · Q58 Compiler ◈ · Q61 📸 screenshots (LISTING.md shot plan, 8 marked)
· Q62 recipes re-QA (GPU=quartz blocks+redstone blocks+iron blocks+kB disk; blank disk=2 iron+quartz
block+redstone+paper; copper kernel=4 copper blocks corners+4 quartz blocks+blank disk) · Q65 type-drops
· Q72 per-card spoils · Q73 kernel egg-speed · Q76 breadcrumb (long-soak) · Q79 friend session (DEFERRED).

**New rows**: **Q80** target selector cards · **Q81** kernel rename (right-click air) · **Q82**
Professor's Summit (27 starters across 2 pastures) · **Q83** Snack Repel (charge can → bake → Nav shows
÷N; re-place old snacks) · **Q84** spawn-speed queue (plain vs 6-golden-apple snack) · **Q85**
echo/amethyst drops · **Q86** Specimen Disks · **Q87** Notebook first-join gift · **Q88** Ritual Batch 2
(locked riddle cards; Elytra any-mix gate; Alolan-gated Ominous Bottle; jukebox pool) · **Q89** egg cake ·
**Q90** MissingNo (he has ~29 Illicit Disks banked for corruption testing; `/gp data` does NOT move the
odometer - only rendered eggs) · **Q91** Hatch Haste · **Q92** augment level II (UPGRADE button, 3 slots,
copper refusal, Greener un-phantomed) · **Q93** review criticals/majors spot-checks (Tier III via
corruption ⛧III, WILD-on-Greener tier push, pause exploit dead, per-brood billing `starvedMidway`,
span clique = ONE pulls line, repel survives recompress) · **Q94** UX batch (Guide-first landing,
auto-wired BioBank sink, pity chips ⏳N/hard, Inventory [E], full-pasture Inbox ⚠, offhand safe,
"GPU saved" on floored Speed).

**BUG verifies**: BUG-013 (kernels never stack), BUG-014 (slotted kernel hover = real tooltip).

## 3 · WHAT LANDED TODAY (2026-07-05) - the delta a tester feels

Features: echo/amethyst type-drops · Specimen Disks (Specimens tab) · field guide REMOVED (Notebook is
the first-join gift) · Ritual Batch 2 (14 hinted rituals + GroupCount/form-key/hint/outputPool engine) ·
OG rituals got riddles (A/A/A) · Pokémon-egg cake (`#c:eggs`) · MissingNo (1M lifetime, rotates 5
species, unbattleable, untetherable) · Hatch Haste augment · augment level II (1.5×/3 slots) ·
**Tier III = corruption-only** (BLESSED ladder I→II→III at 2× base; IV floor caps at 5; WILD on Greener
pushes a tier instead of dead +1 pair) · em-dash purge (1,076) + tripwire test.

Review fixes (REVIEW_FINDINGS.md has everything): sacred-rule hole closed (unreadable eggs KEPT) ·
BLESSED never downgrades · MissingNo can't tether/breed · span clique banks once · catch-up billed
per-brood (starves mid-burst honestly) · pause exploit dead · autoPull=false ignored+warned · repel
survives recompression · overdrive cancel-first · graph-parse LRU + biobank 5s throttle + repel memo ·
ghost pastures pruned · Speed-on-floor refuses free · full UX copy pass.

## 4 · WORKFLOWS (the QA loop)

- **Log watch**: `latest.log` hot events: session_start (minLevel!) · sweep · proc · brood · ritual
  pulls/hit/discovered (pity in pulls lines) · breeding nature_lock/ball_lock/ability_lock/egg_moves/
  iv_floor(promoted:)/ev_spread/hatch_haste · disk write/read · augment_apply (upgrade:true) ·
  corrupt kernel · specimen compress/release · missingno rotate/summon · repel armed/divide ·
  snack speed_tick · tether drain (starvedMidway) · pairing_pruned · queue_full.
- **Marking rows**: python re-sub on QA_PENDING.md status cells (see git history for the pattern).
- **Build** (if fixes needed): `cd greener-pastures-ui && npm run build` (React) then
  `JAVA_HOME=~/jdks/jdk-21.0.11+10 greener-pastures/gradlew -p greener-pastures build` from repo root
  (dangerouslyDisableSandbox; read REAL output + test XML counts; 305 green).
- **Deploy**: ONLY game-closed (watch for ZOMBIE javaw - 8GB javaw lingering after window close = JCEF;
  taskkill it) → cp → md5 both sides + ziptest. Deuce says when he's out.
- **Commits**: after verified batches, trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`
  + Claude-Session link.

## 5 · OPEN LEVERS / AFTER QA

1. **Onboarding numbers** (oldest lever): Daemon 16,384 wall · egg render value 10→20-25? · starter Data.
2. **Art**: icon 128², LISTING 📸 shots (8, during QA), Specimen Disk sprite (green placeholder in),
   Deuce's MissingNo-era disk idea.
3. **CHANGELOG.md** rewrite + version → 1.0.0-beta.1 (LISTING.md + SHOWCASE.md are DONE, rev today).
4. **Q79 friend session** = pre-publish gate (Masuda ×4 config is ON; crystal 1.0 = OFF, his file).
5. Accepted-risk leftovers in REVIEW_FINDINGS (1s buff window, mixin pin, Cobblenav replace notice).

## 6 · TRAPS (all have bitten us)

Jar-over-running-game corrupts · zombie javaw after window close · CF owns minecraftinstance.json
(qa.flag marker is the answer) · gradle `| grep` swallows failures (check test XML) · tuple-6 packet cap
(JSON-string packets or EXACTLY 6) · never insertStack · React hooks above ALL early returns · SP statics
clear on SERVER_STARTED + DISCONNECT · SpecialCraftingRecipe.craft runs for PREVIEW (deterministic only) ·
item components are STACK-wide (augmentables = maxCount 1) · client display stacks lie (dress from server
channel) · influence joins at spawner CREATION (Cobblenav reads it) · log rotates per session ·
Cobbreeding config is TEST-TUNED (600t breeding) - not defaults · em dashes can hide as — escapes.

## 7 · DEUCE - tight replies · he tests, you watch logs + fix · offer boards, he picks · real numbers in
tables · observability-first · no p2w knobs · hidden content stays hidden · loves chained systems ·
"check" = read the log · 💀 = affectionate.

## 8 · DOCS: QA_PENDING.md (queue) · QA_RESULTS.md (BUG-001..014) · REVIEW_FINDINGS.md (adversarial
ledger, zero open) · SHOWCASE.md (public feature list, current) · LISTING.md (store page, current) ·
CHANGELOG.md (STALE - needs rewrite) · RITUALS.md · memory dir = standing rules.
