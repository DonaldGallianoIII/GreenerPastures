# 🎯 PICKUP_HERE - rewritten 2026-07-07 ~01:20 (Deuce went to bed; morning = maybe PUBLISH day)

> **READ THIS WHOLE FILE FIRST** after /clear or compaction. Deuce's last words: "maybe tomorrow we
> publish? he says as the massive scope creep looms above the bed like a sleep paralysis demon."
> Translation: HOLD THE LINE ON FEATURES. The arcade is DONE. Tomorrow = QA pass + art + publish
> tail. Do not accept a seventh cabinet before beta ships (gently).

## 0 · LIVE STATE RIGHT NOW
- **Jar `85f61335` deployed IDENTICALLY to all 4 places**: CF instance mods/, BOTH Prism instances
  (`/mnt/c/Users/deuce/AppData/Roaming/PrismLauncher/instances/GP-Masuda-QA{,-2}/.minecraft/mods/`),
  WSL server `~/gp-qa-server/mods/`. Always deploy to ALL FOUR + md5-verify. Clients only when
  tasklist.exe shows no javaw (ps in WSL CANNOT see Windows javaw!).
- **Dedicated QA server RUNNING**, port 25565 (WSL IP 172.17.176.195 / Tailscale 100.95.66.88).
  Logs: `~/gp-qa-server/gp-logs/latest.log` + `server-console.log`. Restart: kill pid on 25565 →
  wait port free → `cd ~/gp-qa-server && nohup /home/donaldgalliano/jdks/jdk-21.0.11+10/bin/java
  -Xmx4G -jar server.jar nogui > server-console.log 2>&1 & disown`. **TRAP**: my restart one-liners
  left ZOMBIE watcher-shells all session (they wedge relaunches + my own Bash timeout once killed
  the live server). Purged 2026-07-07 ~00:05. Keep restarts as SEPARATE short commands; wait for
  the port with run_in_background, never a foreground until-loop.
- **Monitor** (persistent) on gp-logs: all arcade events (clear/bust/cashout/tl_*/td_* incl mercy/
  sl_spin/shop_buy/hr_buy missing? NO - hr_buy IS in filter? CHECK: filter covers discovered,
  summon, clear, cashout, bust, cap_hit, tl_found, tl_lost, shop_buy, td_new, td_hit, td_miss,
  td_cash, td_top, td_refund, td_mercy, td_mercy_won, td_mercy_lost, sl_spin - **hr_buy, vibe_*,
  tag_* are NOT in the current filter** → re-arm with them added after compaction.
- Git clean through `03df747`. **366 tests green.** Author = **DonaldGalliano** everywhere
  (Deuce222XX is in-game only). Ko-fi live: ko-fi.com/donaldgallianoiii (About card + fabric.mod.json).

## 1 · THE GAME CORNER IS COMPLETE - SIX CABINETS + TWO SHOPS (all server-authoritative, all Coins)
1. **DAEMON FLIP** (Voltorb Flip, PMD portraits, 7 levels persisted, bust drops a level)
2. **TREELINE** (sweep trees, decoy arrows, PAY_PER_SWEEP **3** after Deuce's "cut a zero + half it" - max 30)
3. **TOP DECK v2** (fan of 20 wearing random EMOTIONS → 1 survivor among strangers → 5 flips →
   2x/6x/20x ladder → **Mercy**: free memory check, refunds wager only; td_refund on disconnect)
4. **SLOTS** (pairs **1.5x floored** as of tonight, two-Voltorb 3x paid for it → RTP exactly
   **977/1024 = 95.4%**, enumeration test re-pinned at bet 2; reel-display race FIXED - reels can
   no longer show a face they didn't land)
5. **VIBE CHECK** (free 12-card deck, 4 sour, pot doubles to 128 max, live odds panel, auto-cash on
   8th smile; faucet by design, tuned small)
6. **QUICK CLAW v2** (27 wanderers w/ stride variance + CONSTANT decoy-sprinter traffic 2-6s so the
   target blends in; decoy runners never wear the wanted species = any poster-match IS her; server
   judges click-packet ARRIVAL on nanoTime - unspoofable; 15→3 by reaction, pre-cog pays 0)
- **Prize Counter**: 70 wares, real item textures (model-JSON resolver; Cobblemon nests textures!),
  15-min rotation + refresh-on-every-buy (rolls persisted), double-click race guard, Mystery Egg
  1200 (2 perfect IVs + HA), **charcoal_stick** not vanilla charcoal.
- **HIGH ROLLER ROOM** (Q104, untested): fixed gold shelf - Master Ball 30k · Prime Egg 8k (4x31+HA)
  · **Legend Specimen Disk 100k** (random implemented legendary/mythical Lv.50 minted onto real
  specimen media via mintLegendMon; tooltip shows species; tradeable = server currency, on purpose).
  Prices = my middle board; Deuce may retune after feeling post-nerf earn rates (~600-1200/hr).
- Arcade art: **507 emotion portraits + 23 walk sheets, 45 artists**, ALL verified fan-made
  per-emotion/per-animation (CHUNSOFT rule; Pikachu/Ditto/Eevee/Zapdos REJECTED as rips; Morpeko
  hangry form + Malamar + Zeraora verified-legal for the future). CC-BY-NC note: some newer collab
  entries are NC - fine for a free mod; revisit if Deuce enrolls in Modrinth payouts.
- **SCRAPPED tonight**: the rhythm game (Deuce played his own recorder once: "this is not going to
  be fun after a single playthrough"). WAV charter + recorder live in scratchpad/rhythm if ever revived.

## 2 · MORNING QUEUE (in order)
1. **Arcade QA sweep** - what's verified vs not:
   - ✅ live-verified: VF clear/bust/clamp/cashout; Treeline payout curve + nerf + tl_lost taunt;
     shop debits + rolls-refresh + real item art (Deuce screenshot); TOP DECK v2 full loop incl
     Mercy WON refund (Cramorant, 200 back) + td_refund on disconnect; slots paytable math + reel
     display fix confirmed by Deuce.
   - ☐ NOT yet eyeballed: **Q102 VIBE CHECK** (whole cabinet), **Q103 QUICK CLAW v2** (traffic feel
     + fairness + tag_click log), **Q104 HIGH ROLLER** (all three wares; legend disk release!),
     slots jackpot flash (1/512, optional), Mystery Egg hatch check (2x31+HA), 15-min window turn.
2. **Old stragglers**: Q82 Summit ritual · BUG-019 Lucky Egg no-glow · BUG-020 disc chips ·
   Q61 📸 (LISTING wants 8-10 shots now - cabinets are prime material).
3. **Launch tail**: icon 128² → screenshots → **version bump 1.0.0-beta.1 LAST** (gradle.properties;
   renames jar!) → Modrinth publish per LISTING.md checklist (slug greener-pastures, dono field =
   the Ko-fi, dependency tags). SHOWCASE/LISTING/CHANGELOG refreshed tonight by doc agent + count
   sync (366 tests... files say 363 - bump if touched anyway).
4. If Deuce pitches cabinet #7: point at the sleep paralysis demon and smile.

## 3 · TONIGHT'S BUG LEDGER (all fixed + deployed)
BUG-022 slots reel displayed a face it never landed (anim race; landed-face ref) · QUICK CLAW
mount-at-destination x2 (nobody moved; Amblers + wait-phase runner mount) · house-deck label clip ·
uneven cabinet cards (250x96 uniform) · vanilla-charcoal-gate (charcoal_stick) · icon resolver flat
paths (Cobblemon nests: follow models/item/*.json layer0) · Treeline printer (60→3) · pairs-1.5x
naive flip would've been player-favored 103.6% (two-Volt 5→3 funds it).

## 4 · TRAPS (all previously bitten, all still armed)
cwd resets between Bash calls BUT persists within a call - npm run build in wrong cwd ships STALE
HTML silently (bitten TWICE tonight; always cd greener-pastures-ui && verify output path) · gradle
"up-to-date" after failed npm = stale jar · Windows javaw invisible to WSL ps (use tasklist.exe) ·
zombie restart watchers (see §0) · jar-over-running-game · em dashes forbidden · § literals fine in
NotebookNet · Edit tool needs exact match - python scripts with count asserts are the reliable path ·
GitHub raw 429s (backoff) · client packet-shape changes need BOTH sides swapped together.

## 5 · DEUCE - tight replies · he tests, you watch logs + fix · offer boards with FULL descriptions
(never bare Q-numbers) · no p2w, coins never convert to Data · hidden content stays hidden ·
publishing as DonaldGalliano · "check" = read BOTH logs (CF singleplayer + server) · 💀 = affectionate
· deploy clients only when MC closed · balance directives come as jokes ("cut a zero lmfao") and
they are REAL directives.

## 6 · DOCS: QA_PENDING (Q97-Q104 live board) · QA_RESULTS (BUG-001..021; add 022 row when touched) ·
SHOWCASE/LISTING (agent-refreshed for 6 cabinets tonight; copies in Deuce's Desktop/DiscordTempSetup) ·
CHANGELOG (6-cabinet era) · CREDITS-PMD (45 artists) · scratchpad/pmd (generators: gen_carddeck2,
gen_crowd, fetch_emotions, fetch_walks - regenerate js+java TOGETHER, never hand-edit).
