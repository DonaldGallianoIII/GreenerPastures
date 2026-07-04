# 🎯 PICKUP_HERE — FULL ONBOARDING (written 2026-07-04, pre-/clear)

> **READ THIS WHOLE FILE FIRST.** Deuce /clear'd the session — you have ZERO conversation history. This file
> + the memory dir + the repo docs are everything. It is deliberately long; it replaces weeks of context.

---

## 0 · WHAT IS HAPPENING RIGHT NOW

- **Jar `60f95467` is DEPLOYED** to `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/mods/greenerpastures-0.1.0.jar` (md5-verified, zip-tested). It contains SIX stacked dev batches, none in-game-tested yet.
- **Deuce is starting a QA session with you**: he plays, you watch logs + verify rows + fix reds. The queue is
  **Q39–Q77 in `QA_PENDING.md`** (`glow QA_PENDING.md` — every row has repro steps + expected log lines).
- ⚠️ **HIS INSTANCE NEEDS `-Dgreenerpastures.qa=true` IN JVM ARGS** (CurseForge → instance → Additional Java
  Arguments) or his QA commands (`/gp breed|harvest|data|daemon|augment`) don't exist and the log runs INFO
  instead of DEBUG. `/gp goal` + `/gp perf` ship regardless. First log line `session_start` shows `minLevel`.
- Suggested QA opening: Q50 clean-load → tab sweep (2 new tabs: Rituals "🔒 2 hidden", Guide) → `/gp perf
  flame` (listing screenshot!) → then recipes/GPU/disks/rituals/corruption in any order he likes.

## 1 · THE PROJECT IN ONE PARAGRAPH

**Greener Pastures — "A Data Science Mod"** (Fabric 1.21.1, Java 21, MIT, mod id `greenerpastures`): a
public-release Cobblemon/Cobbreeding companion for Deuce (Deuce222XX). One item — the **Notebook** — is the
whole UI: a real React app rendered in-game via MCEF/Chromium, fed by a loopback WebSocket (DsBridge,
:25599). Players LINK pastures to it; a **Kernel** (6 tiers) slotted in a pasture breeds up to 8 pairs in
parallel and trickle-harvests each mon's drop table; every egg becomes DATA routed by a per-line visual node
graph (filters → **BioBank** keep, or → rendered into **Data** currency); Data feeds the **Daemon** (compiled
buff loadout w/ per-second drain) and **Soul Tethers** (rented augment amplifiers); **GPU** items pay for
augment/buff installs; **data disks** are Data's physical tradeable form; hidden **rituals** (gacha w/ pity)
and **type-drops** make a no-vanilla-mobs world farmable; the **Illicit Data Disk** is a PoE-style corruption
orb. Repo: `~/pokemon-prediction` (not a git remote — local only). Mod code `greener-pastures/`, React app
`greener-pastures-ui/` (vite singlefile → jar resources).

## 2 · WHAT'S IN THE DEPLOYED JAR (the six batches, newest last)

1. **Features**: pasture health strip (⚠ chips + Pastures-tab badges), EV allocator + Nature/Ball pickers +
   Ability/Egg-Move rows in the Augmenter, Kernel LOADOUT chips, Inbox tab (dismissible away-notes).
2. **Perf R3 + profiler**: console idle-off (ZERO traffic while closed), server change-gates (identical
   payloads never re-sent), save-amplification fixes, leak fixes, `GpProf` + `/gp perf` (ms table) +
   `/gp perf flame` (self-contained HTML flame graph in gp-logs/). 270 unit tests green.
3. **Release hardening**: GPU costs LIVE (Augmenter quality 2◈/throughput 1◈; Compiler 2◈/buff-tier; re-pick
   free; no refunds), Field Guide item+tab (first-join gift), QA-flag gating, creative tab, CHANGELOG+LISTING.
4. **Content**: 13 recipes (see §4), data disks write/read, rituals+type-drops wired onto the harvest tick,
   Ultra Compressed Snack.
5. **Rituals v2**: rituals are HIDDEN recipes (Steam-achievement style) — Rituals tab + per-player learned
   state + dedicated spoils pool. Ships: **Feast of the Blade** (Kartana+Xerneas+8 Meowth → e-gapples) and
   **Black Market** (4 Koffing+4 Ekans+Meowth → Illicit Data Disk).
6. **Kernel rework + corruption**: block-ladder recipes, per-tier egg-speed perk, Greener tuning, and the
   corruption orb (see §4/§5).

## 3 · ARCHITECTURE MAP (so you don't re-explore)

**Server (common) — `greener-pastures/src/main/java/com/greenerpastures/`:**
- `GreenerPastures.java` — init order + SERVER_STARTED session hygiene + DISCONNECT pruning + QA_MODE gate.
- `pasture/breeding/` — the engine: `MultiPairBreeder` (20-tick scan, per-pasture `nextBreedTick`, catch-up
  broods vs `lastBreedTick`, 2.5-min floor in `speedAdjustedInterval`), `CobbreedingBridge` (ALL Cobbreeding
  reflection: buildEggForPair/EggShape/rosterOf/shinyMethods/nextBreedingInterval), `BreedingTier` (pairs/
  slots/dropRate/speedFactor), `PastureData` (per-pasture record in `PastureRegistry` PersistentState: owner,
  upgrades inv, pairings, graphJson, eggQueue, lastHarvest/BreedTick, ritualState), `ShinyOdds`, `EvSpread`,
  `NatureCatalog`/`BallCatalog`, `compiler/AugmentType` (the 11 augments) + `compiler/KernelCorruption`.
- `notebook/` — `NotebookNet` (THE sync hub: all packet registration, onRequest push batch 1×/s per open
  console, per-player per-channel `lastPush` equality gates, pull/writeDisk/ritualPull/corruptKernel/
  applyAugment handlers, `pushPastureConfig(full|prefetch)`, health pass), `PastureHarvest` (20-tick scan,
  catch-up sweeps, calls DropsBridge + RitualHarvest), `EggIngest` (egg → graph route → BioBank/void+Data;
  1/2000 illicit breadcrumb), `GraphEval` (thread-aware routing; SACRED: shiny/unreadable always kept),
  `EggLog` (dashboard counters), `PastureHealth` (pure), `AugmentArg` (pure parser), `OfflineProgress`
  (online-gate for catch-up), `Inbox` (notify/), net/ = one packet class per channel (tuple-6 limit → JSON
  string packets for rituals/notifs/dashboard/goals/extra/augmeta).
- `ritual/` — pure cores: `RitualConfig` (defaults = the 2 hand-designed rituals + type-drop table; load
  merges missing default ids), `RitualBook/Ritual/Requirement/Composition` (speciesMinCounts = exact
  headcounts), `Gacha` (banked pulls + soft/hard pity), `RitualLedger` (PersistentState: learned/hits/loot
  per player), `UltraCompressedSnackRecipe` (SpecialCraftingRecipe, double-additive merge), `RitualSystem`.
- `drops/` — `DropsBridge` (faithful Cobblemon drop rolls), `RitualHarvest` (type-drops → storage; gacha →
  ledger + discovery pops), `CompositionReader` (pasture → types/species counts).
- `economy/` — `DataStore` (Data balance), `DataDiskItem` (read-on-use), `IllicitDiskItem`, `GpItems` (all
  item registration + creative tab), `TetherRuntime`/`EffectiveAugments`/`SoulTether` (amplification math),
  `DarkEconomy` (Renderer block→Data legacy path).
- `buff/` — `BuffId` catalog (16 live buffs incl NEW Mining Damage), `DaemonBuffs` (per-second grant/drain),
  `DaemonAttributeBuffs` + `AttributeBuff` (attribute-modifier delivery — Mining Damage = block_break_speed
  +33/67/100%), `BuffConfig` (buffs.json, merges new catalog entries).
- `core/` — `GpLog` (JSONL to gp-logs/latest.log, off-thread, QA_MODE flag lives here), `GpProf` (span
  profiler) + `PerfCommand`, `FieldGuideItem`/`FirstJoinGift`.

**Client — `client/GreenerPasturesClient` (receivers, openers, background pump gate) · `client/notebook/
NotebookBrowserScreen` (MCEF screen: pump(slices,budget) CEF starvation fix, consoleOpen flag = idle-off
gate, preload, curtain/awaitPasture overlays, native inventory overlay) · `NotebookState` (client cache:
apply* change-detection, dim|pos LRU caches, navigate()) · `notebook/bridge/DsBridge` (WS server; serializes
NotebookState → JSON channels ~5×/s WHILE CONSOLE OPEN, maps React actions → C2S packets; devbridge flag).**

**React — `greener-pastures-ui/src/App.jsx`** (single file ~1500 lines): TABS = biobank/harvester/pastures/
compiler/augmenter/dashboard/inbox/rituals/guide; `useChannel(name)` per channel; `send(channel, ACTION,
payload)`; pasture focus view (Threads + DaemonGraph node editor + health strip + LOADOUT chips); pickers =
draggable `dcfg` pop-ups; Dashboard has the 💾 Disks write card. Build: `cd greener-pastures-ui && npm run
build` → writes `greener-pastures/src/main/resources/assets/greenerpastures/html/index.html`.

## 4 · THE NUMBERS (all baked constants — NO CONFIG for any rate, anti-p2w standing rule)

**Kernels** (FINAL, signed off):
| tier | pairs | slots | egg speed | drop |
|---|---|---|---|---|
| copper | 2 | 2 | ×1.1 | +0.50% |
| iron | 3 | 3 | ×1.2 | +1.00% |
| gold | 4 | 4 | ×1.3 | +1.50% |
| diamond | 5 | 5 | ×1.4 | +2.00% |
| netherite | 6 | 6 | ×1.5 | +2.50% |
| **greener** | **8** | **8** | **×1.6** | **+5.00%** |

- Harvest: BASE_PROC 3%/mon/min + tier drop + droprate augment (+2%) × tether ≤×1.3. Breeder floor 3000
  ticks (2.5 min) FINAL. Speed augment ×1.5/×2/×3 multiplies with tier factor.
- **Recipes**: notebook = 7 copper+book+2 redstone+amethyst · guide = book+2 paper · blank disk = 2 iron
  nugget+quartz+redstone+paper · **GPU = 4 quartz+2 redstone+2 copper + KILOBYTE DISK** (=1,024 Data at
  craft) · tether = 3 amethyst+echo shard+string · **daemon = echo+2 amethyst+GPU + MEGABYTE DISK** (16,384)
  · copper kernel = 8 COPPER BLOCKS around redstone block · upgrades = kernel centered in 8 blocks of
  iron/gold/diamond · netherite = 4 diamond blocks corners + 4 netherite INGOTS cardinals · greener = 4
  netherite BLOCKS corners + 4 emerald BLOCKS cardinals · ultra snack = any poke snacks shapeless (special).
- **Disks**: byte 8 · kB 1,024 · MB 16,384 · GB 262,144 · TB 4,194,304. Write via Dashboard card (consumes
  blank + balance); right-click reads value back + returns a BLANK. Rocket disk is NOT currency (see below).
- **Ultra Compressed Snack — "double additive, just not more"**: per-seasoning cap 6 copies (=2 pot-cooks),
  ≤9 distinct seasonings, flavours sum capped at 2× strongest input. Canonical test (Deuce's own):
  [chilan+ega+starf]+[3 starf]+[3 starf]+[3 chilan] → `×4 snacks · 3 effects · 11 stacks · 1 compressed
  away` = 4 chilan + 6 starf + 1 ega. Shiny math (decompile-verified vs Cobblemon 1.7.3): Cobblemon merges
  same-type effects by SUMMING values; P(shiny/spawn) ≈ (Σvalue+1)/8193; starf=4, ega=9 → canonical snack
  ≈1/241, max legal (6 starf+6 ega) ≈1/104.
- **Rituals** (hidden until first assembled; learned per player forever): Feast of the Blade = kartana 1 +
  xerneas 1 + meowth 8 → 1 e-gapple @2%/pull, soft 60, hard 120 (ONLY e-gapple source). Black Market =
  koffing 4 + ekans 4 + meowth 1 → 1 Illicit Data Disk @2.5%, soft 50, hard 100. One pull banked per
  harvest sweep while composition holds; pity persists per pasture (PastureData.ritualState). Spoils land
  in the Rituals tab pool (RitualLedger), NEVER Harvester storage. Breadcrumb: 1/2000 rendered eggs → disk.
- **Corruption** (⛧ CORRUPT button in Augmenter, consumes 1 Illicit Disk): 30% BLESSED (random augment
  force-installed beyond slot cap) / 25% WILD (drop mod ×2 or +1 pair) / 25% NOTHING / 20% BRICKED (augments
  wiped or tier−1). Corrupted = component flag, locked forever (Augmenter refuses). Known accepted valve:
  ladder-upgrading a corrupted kernel crafts a clean higher tier.
- **Daemon buffs**: 16 live, tiers I-III, 2◈ GPU/tier install, drain 0.5 (gathering) / 0.25 (QOL) Data/s/tier.
  NEW: Mining Damage (block_break_speed +33/67/100%; III + Haste III + Eff V = instant deepslate).
- **Shiny breeding ladder** (his cobbreeding: base 1/8192, masuda ×4, crystal ×1.0=OFF): shiny augment =
  ×(1+proc), proc 30% (39% tether-fed). Greener 8-pair @10.5-min default base ≈ 100 eggs/hr → base 81h,
  +SpeedIII 43h, +shiny aug 33h, +masuda 7.7h, +crystal-if-2.0 3.8h/1.9h.

## 5 · OPEN LEVERS (Deuce owes numbers — DO NOT build until he picks)

1. **Data onboarding trio**: (a) Daemon recipe megabyte→kilobyte? (b) egg render value 10 → 20-25? (c)
   starter Data via guide? (He flagged onboarding is too slow; the 16,384-Data Daemon is the wall.)
2. **Crystal multiplier** — his `config/cobbreeding/main.json` (his file not ours). Currently 1.0 = the
   crystal ladder rung does nothing. NB his min/maxBreedingTimeInTicks=600 (30s) is a TESTING value;
   real default ≈ 9-12 min — grid math above assumes ~10.5 min.
3. **Publish tail (task #7)**: icon art, screenshots per `LISTING.md` 📸 plan, version → 1.0.0-beta.1,
   Modrinth/CF listing (draft ready in LISTING.md), CHANGELOG.md maintained.

## 6 · HIS WORLD + WORKFLOWS (don't re-derive)

- World `New Worlddasdasdadsa`, spawn -400,-336. Pastures: spawn farm -394,69,-290 / -400,69,-288 /
  -398,69,-288; west -902,69,-297 + -923,63,-453; nether -165,68,-19. renderDistance 36 → chunks stay
  loaded within 576 blocks; the NETHER HOP is the reliable unload lever for catch-up tests.
- **Logs**: `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/gp-logs/latest.log`
  (JSONL). Hot events: `sweep` (sweeps:N/proc_pct/stored/items) · `proc` · `brood` · `ritual`
  hit/pulls/discovered · `corrupt kernel` · `disk` write/read · `augment_apply` (gpu,repick) ·
  `compile_set` (gpu) · `pull`/`pull_full` · `first_join_gift` · `illicit_breadcrumb` · `gap_applied` ·
  `note_push/dismiss` · `status_push` (only on real change now).
- **Build**: `cd greener-pastures-ui && npm run build` (if React touched), then
  `JAVA_HOME=/home/donaldgalliano/jdks/jdk-21.0.11+10 greener-pastures/gradlew -p greener-pastures build`
  (dangerouslyDisableSandbox; RUN FROM REPO ROOT). **READ REAL GRADLE OUTPUT — grep pipes have swallowed
  failures twice.** 270 tests green at deploy. UI-only changes still need the gradle build to repackage.
- **Deploy** (ONLY on his explicit out-of-game confirm): cp jar → instance mods path above, then md5 both
  sides + `zipfile.testzip()`. NEVER while the game runs.
- **Cobblemon internals**: python zipfile extract + `java -jar ~/cfr.jar` (JRE-only box; JDK at
  ~/jdks/jdk-21.0.11+10). Everything snack/seasoning/shiny was verified this way against
  Cobblemon-fabric-1.7.3+1.21.1.jar.
- Commit trailer: `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` + Claude-Session link.

## 7 · KNOWN TRAPS (each has bitten us)

- **Jar corruption**: deploying over a RUNNING game corrupts the open jar handle → ZipException hours later.
- **Gradle greps**: a `| grep`'s exit 0 is not gradle's — check "BUILD SUCCESSFUL" + test XML counts.
- **Tuple-6**: PacketCodec.tuple caps at 6 fields → JSON-string packets are the established dodge.
- **insertStack**: NEVER trust it (mixin-hijackable; 3k ink sacs vanished once) — count capacity + place
  into slots manually (pull/ritualPull/writeDisk/consumeGpu all do this).
- **React TDZ**: a hook dep referencing a const declared later in the component = black screen. Deps must
  reference stable/earlier values (the `[doc.active]` lesson).
- **SP statics**: one JVM across world switches — every session store must clear on SERVER_STARTED (server)
  + DISCONNECT (client). Already wired; keep the pattern for new stores.
- **Preview-lying recipes**: SpecialCraftingRecipe.craft() runs for the PREVIEW too — random outcomes can't
  go in crafting results (that's why corruption is a console button, not a recipe).
- **His cobbreeding config is test-tuned** (30s breeding) — don't mistake it for defaults when doing math.

## 8 · DEUCE'S OPERATING STYLE

Tight replies. He tests personally; batch code with DEFERRED QA when he says so; he steers priorities —
offer the board, let him pick. Balance-tunes by feel: give him tables + worked examples with real numbers
(he loved the shiny-ladder + snack-math grids). Observability-first: every feature ships GpLog lines; read
logs before theorizing. NO pay-to-win surfaces ever (no rate configs). Hidden content stays hidden (no
recipe hints in UI). He loves systems that chain (Feast e-gapples → snack seasonings was a hit). Emoji-light
in his messages (💀🫵🏻 = affectionate roast). When he says "check" — read the log and verify.

## 9 · FILE MAP (docs that matter)

`QA_PENDING.md` (THE queue, Q39–Q77 + all older verified rows) · `CHANGELOG.md` (incl. locked design
decisions) · `LISTING.md` (Modrinth draft + 📸 shot plan) · `PERF_AUDIT.md` (3 rounds of perf work) ·
`RITUALS.md` / `NOTEBOOK_CONSOLE_SPEC.md` / `KERNEL_AUGMENTER_SPEC.md` / `MCEF_REACT_SPEC.md` (design
history) · `OBSERVABILITY.md` (GpLog rules) · memory dir has the standing rules (drop-rate anchor, deploy
workflow, viewport principle, testing-first, etc.).
