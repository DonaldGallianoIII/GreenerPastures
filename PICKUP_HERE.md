# 🎯 PICKUP — QA SESSION LIVE (2026-07-04)

> **LIVE STATE: jar `60f95467` IS DEPLOYED** to the CurseForge instance and **Deuce is QA-testing it with me
> right now** — six stacked dev batches, first launch of all of them. Follow the logs, verify each Q-row as he
> works, fix reds immediately (build green → deploy ONLY on his explicit quit-to-desktop confirm — WSL `cp`
> over a RUNNING jar corrupts it hours later, see [[mod-deploy-workflow]]).
>
> ⚠️ **FIRST CHECK: his instance JVM args must include `-Dgreenerpastures.qa=true`** (CurseForge → instance →
> Additional Java Arguments). Without it: no `/gp breed|harvest|data|daemon|augment`, log at INFO (his tail
> workflow breaks). With it: those commands + DEBUG logging. `session_start` in gp-logs shows `minLevel`.
> `/gp goal` + `/gp perf` exist either way.

## The QA queue — Q39–Q77 in `QA_PENDING.md` (`glow QA_PENDING.md`)
- **Q39–Q49 features**: pasture health strip + ⚠ tab badges · EV allocator + nature/ball pickers (Augmenter
  PICK…/EDIT) · Kernel LOADOUT chips · Inbox tab + note logging.
- **Q50–Q55 perf**: NOTHING REGRESSED (Q50, run it first) · idle-off (no console traffic while closed;
  `-Dgreenerpastures.devbridge=true` restores the dev-browser) · change-gated pushes (status_push only on real
  change) · `/gp perf` table + `/gp perf flame` → `gp-logs/perf-flame.html` (screenshot for the listing!) ·
  catch-up still exact · cross-dim cache keys.
- **Q56–Q61 release**: QA flag behavior · **GPU costs now REAL** (Augmenter: quality 2◈/throughput 1◈;
  Compiler: 2◈/buff-tier; re-pick free, no refunds, chat refusals) · Field Guide (first-join gift + Guide tab
  via nav) · creative tab · LISTING.md shot plan.
- **Q62–Q67 content**: **13 recipes** (kernel BLOCK ladder: 8 copper blocks+redstone block → kernel-in-8-blocks
  upgrades → netherite = 4 diamond blocks corners + 4 netherite ingots cardinals → GREENER = 4 netherite blocks
  corners + 4 emerald blocks cardinals; GPU eats a kilobyte disk; daemon eats a megabyte disk) · **data disks**
  (Dashboard 💾 write card, right-click read returns the blank; ladder 8/1k/16k/262k/4.2M — rocket is NOT
  currency) · **type-drops live** (fire-types = blaze rods!) · **Ultra Compressed Snack, double-additive**:
  Deuce's canonical test [chilan+ega+starf]+[3 starf]+[3 starf]+[3 chilan] → lore `×4 snacks · 3 effects · 11
  stacks · 1 compressed away` = 4 chilan + 6 starf + 1 ega.
- **Q68–Q72 rituals v2**: HIDDEN recipes (tab teases "2 hidden") · **Feast of the Blade** = Kartana + Xerneas +
  8 Meowth → e-gapples (2%/pull, soft 60, hard 120) · discovery pop (once-ever chat + Inbox + tab reveal) ·
  pity persists per pasture · spoils pool is separate from Harvester storage (RITUAL_PULL, capacity-safe) ·
  pre-v2 rituals.json auto-regenerates; missing default rituals auto-merge.
- **Q73 kernel perks**: egg speed ×1.1→×1.6 by tier (stacks with Speed augment; 2.5-min floor FINAL, signed
  off) · Greener drop +5.00% (sweep log shows proc_pct 8.00).
- **Q74–Q76 corruption**: Illicit Data Disk = PoE Vaal orb via Augmenter ⛧ CORRUPT (30 blessed / 25 wild
  [drop×2 or +1 pair] / 25 nothing / 20 bricked [wipe or tier−1]; corrupted = locked forever) · **Black
  Market ritual** = Koffing ×4 + Ekans ×4 + Meowth → the disk (2.5%, soft 50, hard 100) · Renderer breadcrumb
  1/2000 voids → disk into ritual spoils + Inbox whisper.
- **Q77 snack shiny math (decompile-verified, no code)**: merged shiny value = Σ(starf 4 each, ega 9 each),
  P(shiny/spawn) ≈ (value+1)/8193. Canonical snack = 33 → ~1/241. Max legal (6 starf + 6 ega) = 78 → ~1/104.
  E-gapples only come from Feast → the two systems chain.

## Today's LOCKED tuning (don't relitigate)
- Kernel table FINAL: pairs 2/3/4/5/6/8 · egg speed ×1.1/×1.2/×1.3/×1.4/×1.5/×1.6 · drop +0.5/1.0/1.5/2.0/2.5/**5.0**%.
- 2.5-min breeder floor FINAL (noted in code + CHANGELOG "Design decisions").
- GPU: quality augment 2◈ · throughput 1◈ · buff tier 2◈. Disk ladder + GPU-eats-kilobyte + daemon-eats-megabyte.
- Ultra snack: double-additive (≤6 copies per seasoning = 2 pot-cooks, ≤9 distinct, flavours ≤2× strongest input).
- Corruption table 30/25/25/20, baked. No config for ANY rate (anti-p2w, standing rule).
- Escape valve known+accepted: ladder-upgrading a corrupted kernel crafts a clean higher tier (loses corruption perks).
- Mining Damage buff (block_break_speed +33/67/100%): III + Haste III + Eff V = instant deepslate; gathering-priced.

## Open levers (Deuce owes numbers; don't build until he picks)
- **Data onboarding trio**: (a) Daemon recipe megabyte→kilobyte disk? (b) egg render value 10→20-25? (c) starter Data?
- **Crystal multiplier**: HIS `config/cobbreeding/main.json` (currently 1.0 = off; 2.0 makes the ladder rung real).
  NB his cobbreeding min/maxBreedingTimeInTicks=600 is a TESTING value; real default ~9-12 min.
- Publish tail (#7): icon art, QA screenshots per LISTING.md 📸 plan, version → 1.0.0-beta.1, Modrinth/CF.

## His world + environment (don't re-derive)
- World `New Worlddasdasdadsa`, spawn `-400,-336`; pastures: spawn farm `-394,69,-290` / `-400,69,-288` /
  `-398,69,-288`, west `-902,69,-297` + `-923,63,-453`, nether `-165,68,-19`. renderDistance 36 (576-block
  load radius) — nether hop = reliable chunk unload for catch-up tests.
- Logs: `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/gp-logs/latest.log` (JSONL).
  Hot events: sweep/proc/brood/ritual hit·pulls·discovered/corrupt kernel/disk write·read/augment_apply(gpu)/
  compile_set(gpu)/pull·pull_full/first_join_gift/illicit_breadcrumb/gap_applied/note_push·dismiss.
- Build: `cd greener-pastures-ui && npm run build` then `JAVA_HOME=/home/donaldgalliano/jdks/jdk-21.0.11+10
  greener-pastures/gradlew -p greener-pastures build` (sandbox off; READ REAL GRADLE OUTPUT — grep pipes have
  eaten failures twice). 270 tests green at deploy. Deploy = cp to instance mods + md5 + testzip.
- Cobblemon internals: decompile via python zipfile + `~/cfr.jar` (snack/seasoning/shiny findings all verified
  against Cobblemon-fabric-1.7.3+1.21.1.jar this way).

## Deuce's operating style
Tight replies; he tests personally, steers priorities, tunes balance by feel (give him tables + worked
examples); deploy ONLY on explicit out-of-game confirm; observability-first (logs before theories); no
pay-to-win surfaces EVER; hidden content stays hidden (no recipe hints in UI); he loves loop-closures between
systems (Feast e-gapples → snack seasonings was a hit).
