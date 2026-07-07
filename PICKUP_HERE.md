# 🎯 PICKUP_HERE - rewritten 2026-07-06 late (pre-compact, Deuce back for the night)

> **READ THIS WHOLE FILE FIRST** after /clear or compaction. Deuce is AT his computer, clients may be
> RUNNING (check javaw before ANY client deploy). Tonight = arcade QA (Q97/98/99) or launch tail.

## 0 · LIVE STATE RIGHT NOW
- **Jar `0b8f8e61` deployed IDENTICALLY to all 4 places**: CF instance mods/, BOTH Prism instances
  (`/mnt/c/Users/deuce/AppData/Roaming/PrismLauncher/instances/GP-Masuda-QA{,-2}/.minecraft/mods/`),
  and the WSL dedicated server `~/gp-qa-server/mods/`. Always deploy to ALL FOUR + md5-verify.
- **Dedicated QA server RUNNING** in WSL: `~/gp-qa-server`, port 25565, flat world, online-mode, Deuce
  op'd. Clients connect to `172.17.176.195` (WSL IP; Tailscale `100.95.66.88` also works - friend-
  session ready). GP log: `~/gp-qa-server/gp-logs/latest.log` (qa.flag set, DEBUG). Console:
  `~/gp-qa-server/server-console.log`. Restart recipe:
  `kill $(ss -tlnp|grep 25565|grep -oP 'pid=\K[0-9]+'|head -1)` → wait port free →
  `cd ~/gp-qa-server && nohup /home/donaldgalliano/jdks/jdk-21.0.11+10/bin/java -Xmx4G -jar server.jar nogui > server-console.log 2>&1 & disown`
- **Persistent Monitor** running on GP server log (discoveries/arcade settles/WARN+ERROR). Re-arm after
  compaction if gone: tail -F gp-logs/latest.log | grep events discovered/summon/clear/cashout/bust/WARN/ERROR.
- Git through `04ae5bc`, tree clean, **336 tests green**. Cobbreeding server+SP configs: breeding time
  12000t (set for Q73; QA override `/gp breed interval 15` outranks it when set).

## 1 · WHAT EXISTS NOW (2 days of building; singleplayer CF world + the server world are both QA sandboxes)
Everything in the old pickup PLUS today (2026-07-06):
- **🎰 GAME CORNER** (Notebook tab #11): lobby with TWO cabinets + Prize Counter.
  - **DAEMON FLIP** = Voltorb Flip: server-authoritative (face-down tiles never sent), 7 levels
    (persisted in ArcadeStore), PMD portraits: happy = 15 gen5-9 starters, bombs = sad mons,
    chips = JFain's angry Voltorb + danger-color count ramp.
  - **TREELINE** = Deuce's artifact ported: scatter → Zelda pan → 10 sweeps to find the SCORBUNNY
    (fan-made walk sheet; Ponyta was a CHUNSOFT rip - rejected); decoys snitch 8-way arrows
    (server-computed); payout 60×(sweeps left+1). Mod-native theme, px layout.
  - **Game Corner Coins 🪙**: cabinets pay COINS not Data (closed loop, only items leak). In
    ArcadeStore.Ledger.coins. NO daily cap (DAILY_CAP=0, fence math kept+tested for its return).
  - **Prize Counter**: 60-ware catalog (ALL Cobblemon held items - verified vs jar; charcoal is
    minecraft:charcoal), 6 shelves/player, rotation = deterministic f(15-min window, uuid) - no
    storage, relog-stable; buy re-derives shelf server-side; capacity-refuse BEFORE debit.
    **Mystery Egg 1200🪙**: random species (no legendary/mythical/UB/paradox), ≥2 perfect IVs
    (IVs.createRandomIVs(2)), hidden ability ALWAYS (HA-less species excluded); assembled via
    CobbreedingBridge.shopMysteryEgg → real Cobbreeding egg; jam-refusal keeps Coins; ware hidden
    without Cobbreeding.
- **PMD Sprite Collab pipeline**: fan-made ONLY, verified PER-EMOTION/PER-ANIMATION (charmander/
  chikorita Happy = rips → dropped; Ponyta walk = rip → Scorbunny). Credits: CREDITS-PMD.md +
  in-jar pmd_credits.txt + generator asserts no CHUNSOFT. Sprites live as data-URIs in
  greener-pastures-ui/src/pmdsprites.js + treelinesprites.js (regen scripts in scratchpad/pmd).
- **Crystal floor ×2** (ShinyOdds.flooredCrystal): max(server crystal, 2.0) in odds AND badge push.
- **Feast of the Bloom**: Kartana→Shaymin any-form (no Kartana model in Cobblemon 1.7.3).
- **MissingNo pool**: gastly→haunter (Discord canon catch).
- **Cobblemon dep pinned** `>=1.7.3 <1.8.0` (mixin brittleness → clean loader error). Deps audit clean.
- **Docs current**: LISTING/SHOWCASE (superfan audit applied: ghost pastures/highlighter/EggOracle
  promoted; anti-p2w claim honest), CHANGELOG = beta-1 manifest. CREDITS-PMD.md.
- **/gp data lifetime <n>** (odometer QA lever) · recipe book auto-unlock · brood log intervalTicks+tier.

## 2 · TODAY'S BUG LEDGER (all fixed, in QA_RESULTS.md)
BUG-015 Augmenter scroll · BUG-016 breeding limiter (lines are THE breeding switch; 🧵 no_lines chip)
· BUG-017 biobank ghost-abra (throttle ate rev signal; withdraw force-pushes) · BUG-018 line_incomplete
chip (half-line never silent) · BUG-019 Lucky Egg not an egg (highlighter) · BUG-020 pool spoils on
their card (Band discs itemized chips + Unclaimed filter) · **BUG-021 dedicated ingest dead**
(NoClassDefFoundError class_746: in-method EnvType guard ≠ side safety - client refs QUARANTINED in
EggTooltipProbe; LESSON: separate class, always).

## 3 · VERIFIED TODAY (highlights)
Q65/Q83/Q84/Q85/Q87/Q88(full)/Q90(full)/Q96/Q73 (intervalTicks receipts: copper 10909 · netherite
8000 · greener+SpdI 5000) + Deuce's checklist import (Q39/40/44/46/48/58/80/81/94/95/62/89 ✓).
**Server session**: Masuda ✓ (mixed OT pink check) · Crystal solo+stacked ✓ · MissingNo trades ✓ ·
disk trading ✓ · Specimen-disk trading ✓ · first dedicated run (caught BUG-021).

## 4 · OPEN QUEUE
- **Q97** DAEMON FLIP (portraits crisp? level ladder, cashout, odometer untouched)
- **Q98** TREELINE (scatter/pan, arrows point true, taunt escape, relog=fresh round, tl_found log)
- **Q99** Coins + Prize Counter (win→coins not Data, rotation at window turn, REDEEM, full-pack
  refusal keeps coins, Mystery Egg hatch: ≥2×31 IVs + HA)
- Stragglers: Q41 biobank-full eyeball · Q82 Summit · Q93c economy soak (pause/starved) · Q53 chat
  table · Q55 cross-dim · Q61 📸 (8 shots - arcade cabinets are shot-worthy) · Q79 friend session
  (MOSTLY covered by server session; Tailscale makes the real one trivial).
- BUG verifies: 018 chip · 019 no glow · 020 disc chips · 021 eggs bank on server.

## 5 · LAUNCH TAIL (in order)
1. **About card in Guide tab** (NEXT TASK - promised): author/version/MIT/PMD credits; dono line
   pending Deuce's Ko-fi (recommended, not set up yet). Also fabric.mod.json contact block.
2. Arcade QA above.
3. **Art**: icon 128² + 8 LISTING 📸.
4. **Version bump → 1.0.0-beta.1** (gradle.properties mod_version; LAST code action - renames jar).
5. Publish checklist in LISTING.md (Modrinth slug greener-pastures, dono field, dependency tags).
- Onboarding numbers CLOSED: 10 Data/egg · 0 starter Data · arcade uncapped (coins-only now anyway).
- Shop-egg spec CLOSED: random species / ≥2×31 / always-HA. Legendaries excluded (my call - flag if
  Deuce wants them in).

## 6 · TRAPS (old ones stand; new today)
All of the old pickup's traps PLUS: in-method EnvType guards do NOT make client refs server-safe
(verifier resolves at first call - quarantine class) · Prism instances = plain folders (mmc-pack.json)
· CF full-pack client on minimal server = creative-menu decode kicks (use the minimal Prism clients)
· WSL localhost doesn't forward to Windows MC - use WSL IP · GitHub raw rate-limits (429) - backoff ·
grep -c on minified html counts LINES (it's 1 line) · shell cwd resets between Bash calls - absolute
paths · em dashes STILL forbidden in player-facing strings.

## 7 · DEUCE - tight replies · he tests, you watch logs + fix · offer boards, he picks · no p2w ·
hidden content stays hidden · "check" = read the log(s - TWO worlds now: CF singleplayer
`/mnt/c/.../Greener Pastures Test/gp-logs/latest.log` + server `~/gp-qa-server/gp-logs/latest.log`) ·
💀 = affectionate · deploy needs game closed for CLIENTS (server side anytime via restart).

## 8 · DOCS: QA_PENDING (queue) · QA_RESULTS (BUG-001..021) · REVIEW_FINDINGS (closed) · SHOWCASE ·
LISTING · CHANGELOG (current!) · CREDITS-PMD · RITUALS · memory dir.
