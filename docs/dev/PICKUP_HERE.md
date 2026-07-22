# 🎯 PICKUP_HERE — updated 2026-07-22 ~23:20: QA NIGHT LANDED - resume tomorrow's QA list below

> **⚡ SESSION 2026-07-21→22 (the big live-QA night). ALL DEPLOYED: jar `15b5e343` on all 4 installs,
> QA server BOUNCED + RESTARTED on it (port 25565; Deuce connects via WSL IP - `localhost` forwarding
> was broken, re-check `hostname -I` if it changes). 9 commits on dev tonight:**
> 1. **Compression Press** (personal ×1.05/100 eggs, stacking) + **server press** (communal 1000=+1%,
>    "more" mult) + **donation feed** (Inbox section, 24h window) - LIVE-VERIFIED: presses to ×1.15,
>    donate 100 froakie pool, `comp on` sweeps. BioBank cap 1024.
> 2. **The Loom** (tether bench: inscribe/wipe/rename) - LIVE-VERIFIED incl. re-inscribe refund math.
> 3. **TETHERS row** on pasture screen - LIVE-VERIFIED slotting.
> 4. **Egg fix**: shop eggs (Mystery+Prime) hatch BASE forms only (Deuce pulled a POLITOED).
> 5. **EV + IV Floor tether targets RETIRED** (EV had zero consumers since the spread rework).
> 6. **Additive tether model**: +tier LEVELS stacking PAST the augment cap (each level = ½ a level-I
>    install: shiny +15%/lv · drop +1.00%/lv · enrich +10%/lv · speed/yield/hatch +1lv; speed/hatch
>    ladders extended to 6 = tether-only territory). Deuce thinks in PoE increased/more terms.
> 7. **TETHER FX row** (before→after per mod + STARVED dim + "idle - install the matching augment" warn).
> 8. **Rent model**: per-SECOND rent (quality 0.5×tier/s · throughput 0.2×tier/s) on the TetherUpkeep
>    clock, ONLY linked+occupied+loaded; away windows **PRE-PAID by the consumers' catch-ups** - check
>    Data first, THEN buff; can't pay = zero debit + base-mod burst (`rent_catchup`/`rent_unpaid` logs).
>    Old per-cycle burn deleted. 406 tests green.
> 9. **Drops audit** → `cobblemon-drops-ref/UNDERREPRESENTED_DROPS.md`: gold = Meowth/Persian nuggets
>    ONLY in all of Cobblemon (Gholdengo drops no gold!), single-family bottlenecks (redstone=Electabuzz,
>    gunpowder=Voltorb...), zero-source list. 3-tier fix proposal written; **Deuce owes: which tiers +
>    gold flavor (Gholdengo-only vs steel trickle) + are diamond/emerald scarcities intentional.**
>
> **⚠ Caveats:** server stop was SIGTERM via TaskStop - no explicit "Stopping server" seen in log; if the
> QA world lost the last ~minutes before 22:47, that's why. His QA-era EV(×1)/IV(×1) tethers are now
> INERT - wipe at the Loom refunds 450 each. Old jar's Drop Yield III tether behavior changed under him
> mid-session (by design).
>
> ## 📋 TOMORROW'S QA LIST (new build - tether QA is a full RERUN, the model changed twice tonight)
> 1. **Persistence across the bounce FIRST**: Bulbasaur ×1.15 / Froakie ×1.05 chips survived? Slotted
>    Drop Yield III still in the pasture? Tether names survive? Donation feed EMPTY (correct - 24h
>    in-memory window).
> 2. **Egg fix**: buy several Mystery/Prime eggs → base forms only, never evolved.
> 3. **Loom v2**: EV + IV Floor gone from catalog; tier buttons show `+N%`/`+Nlv` + `rent /s`; wipe the
>    dead EV/IV tethers (+450 each).
> 4. **TETHER FX row**: idle warning on the yield tether (no yield augment installed) → install Drop
>    Yield augment at the Augmenter → FX flips to `yield +N → +N+3`; slot a Drop Rate tether on the
>    compression pasture → `proc_pct` jumps by +3.00% (tier III); speed tether past cap (lv 4+ cadence).
> 5. **Rent**: `tether/rent` JSONL events ~per second only while linked+occupied; empty pen / unlinked /
>    tether in inventory = NO rent; drain balance to 0 → FX dims STARVED + boosts stop + billing stops.
> 6. **Pre-paid catch-up**: tethered pasture, leave chunk ~10 min: (a) with Data → return → one
>    `rent_catchup` debit + amplified burst; (b) spend Data to ~0 first → return → `rent_unpaid`, ZERO
>    debited, burst at base mods.
> 7. **Compression proc evidence**: `/gp harvest interval 5`, pressed species tethered → `comp_x 1.05+`
>    proc lines (never observed tonight - math verified, visual pending). Donation feed UI check +
>    tier-up pink ▲ at 1000 (needs 10 donates) + two-account chip/feed refresh.
> 8. **Rename round-trip**: named tether slot→unslot keeps name; stack rename = all copies.
> 9. Backlog rides along: **Display Suite in-game smoke** (Exhibit Pen/Statue, spec §4) + statue FEEL
>    PASS; NeoForge fold-back; Tinderbeef beta.2 repro.
>
> **Also pending decisions:** drops tiers (above) · separate battle-display mod (Discord request:
> battleable display units + custom drops + patrol paths + selector permissions; name candidates
> Set Piece / Gauntlet / Menagerie / Field Museum - awaiting requester confirmation via Deuce).

# (older) PICKUP_HERE — updated 2026-07-14: BUILDING the Display Suite, right now, with Deuce

> **⚡ 2026-07-20 (same QA day, after the press): SOUL TETHERS WERE DEAD - now revived via the LOOM.**
> Deuce's live QA find: beta.1 shipped tethers with NO inscribe path (TetherInscription was never
> called) and NO reachable slot GUI (PastureMenu orphaned by the owo→React migration). Fix, his
> design: **Loom tab** (tether's own bench: pick inventory tether → function×tier catalog → pay Data,
> wipe refunds half; `INSCRIBE_TETHER=37`, `NotebookLoomS2C`, channel `loom`, stacked blanks split
> one-per-inscription = anti-dupe) + **TETHERS row on the pasture config screen** next to KERNEL
> (`NotebookPastureActionC2S.TETHER=4`, arg "idx:invSlot"/-1 return; blanks refused; extra JSON gains
> `slots`+`tethers`). Tooltips corrected. JSONL keys: `loom` inscribe/wipe/tether_slot/tether_return.
> 404 tests green, jar rebuilt + HTML verified. **QA:** inscribe (check Data math incl. wipe refund +
> stacked-blank split), slot into pasture, then confirm the amplified proc/burn actually bills
> (`tether drain` lines) - the runtime was always live, it just never had input until now.

> **⚡ SIDE-SESSION 2026-07-19 — COMPRESSION PRESS shipped on `dev` (coded + 397 tests green, NOT deployed).**
> Deuce's ask ("Compressed pokemon") landed as: **100 BioBank eggs of a species → permanent +5% drop rate
> for that species in your pastures, stacking forever** (his design, upgraded live from a one-shot ×3 to a
> repeatable ledger); BioBank cap **256 → 1024**/species. Implementation: `biobank/CompressionLedger`
> (MC-free core + 8 tests) + `CompressionStore` (per-UUID PersistentState) · `BioBankData.sacrifice`
> (all-or-nothing, worst-ΣIV-first, shinies NEVER eligible) · `DropsBridge` species-scale overload →
> `PastureHarvest` applies owner's multiplier per mon (`comp`/`comp_x` in the JSONL) · action
> `COMPRESS_EGGS=35` + `compression` map on the biobank channel · React: ⭓ press button per species row +
> confirm modal (current → next mult), Guide card updated, mock data/reducer covered. Jar built with fresh
> HTML (verified in-zip). **PLUS the SERVER press (same session):** donate pulls to a communal pool -
> 1000 pooled eggs/species = +1% for EVERYONE, a "more" multiplier (`personal × server`, Deuce's PoE
> framing). `CompressionLedger.server()` (1000/+1% constants) + server ledger in `CompressionStore`
> (rev counter folded into the biobank push gate so OTHER viewers see communal tier changes),
> `COMPRESS_SERVER=36`, modal shows both presses + pool progress. **Donation visibility (Deuce: no
> chat chirp):** tier-up broadcast REPLACED by `notify/DonationFeed` - a GLOBAL 24h rolling window
> (TTL prune + 200-entry RAM cap, clock-injected tests) rendered as its own non-dismissible section
> in the Inbox tab (tier-ups highlighted pink with donator name); rides the notifs channel's content
> gate so every viewer sees new donations on the 1s poll. 404 tests green. **NEXT: batch in-game QA**
> (press + donate a species, watch `compression press`/`donate` + `harvest proc comp_x` lines,
> two-account check that a donation refreshes the OTHER console's chip + Inbox feed) - rides the
> same QA batch as the Display Suite.

> **⚡ SIDE-SESSION 2026-07-15→16 (FarmHand/hydrogrid) — DEPLOYED LIVE, awaiting Deuce's field test.**
> Big Auto-Farm upgrade session in `hydrogrid/`, jar deployed to `Shedmon (3)/mods/` (zip-verified).
> What shipped in this build:
> - **Full-speed batching**: removed the 120ms rate limit; every valid target in reach is handled
>   every client tick; same-tick replant after breaks; scan band raised to +5 for tree canopies.
> - **Crop filter, key `/`**: ALL → VIVICHOKE ONLY → HEARTY GRAINS ONLY. Filters both reaping AND
>   which seed gets planted (fixes cross-planting on Deuce's mixed viv/hearty field). Berries,
>   honey, apricorns, vanilla crops only run on ALL.
> - **2x-reach experiment, key `;`**: Deuce suspects Shedmon doesn't enforce reach. Every break is
>   re-checked ~500ms later and logged (`verify` events: dist + stillGone). Verdict = read the log:
>   `stillGone:true` beyond ~5.5 dist ⇒ not enforced. Vanilla 1.21 default is enforced (4.5+1).
> - **Hearty grains**: decompiled Cobblemon 1.7.3 — break ONLY age-6 upper half (drops 2–3 grains,
>   base resets to age 3 + regrows; a harvested stalk half-standing is CORRECT). Fixed waterlogged
>   crops re-firing forever (break→water≠air; cooldown now unconditional). Deuce reported "not
>   harvesting properly" — root cause unconfirmed, diagnosis rides on the debug log below.
> - **Fortune**: auto-selects highest-Fortune hotbar tool for breaks. Loot-table truth: fortune
>   does NOTHING for vivichoke/hearty (flat drops); vanilla crops only.
> - **Honey (Deuce's rule: LEAVES ONLY, NEVER hives)**: saccharine leaves age 2 + hotbar glass
>   bottle → honey bottle, leaf refills. Skips waterlogged leaves (block refuses).
> - **Apricorns (folded INTO FarmHand, supersedes ApricornArborist-separate)**: age-3 any color →
>   right-click pop, regrows. Class-matched like berries.
> - **Debug JSONL** (observability rule): `Shedmon (3)/logs/hydrogrid-farm.jsonl` — farm/break/
>   verify/honey/apricorn/hearty events. Keys now: `,` run · `.` mode · `/` filter · `;` 2x reach.
>
> **NEXT for FarmHand:** Deuce runs a REAP pass (fortune pick + seeds + glass bottles on hotbar,
> `;` on, over the mixed field + under saccharine/apricorn trees) → then tail the JSONL for
> (1) the reach-enforcement verdict, (2) what hearty blocks the scanner actually saw. All source
> in `hydrogrid/` (not yet renamed farmhand), builds with home JDK+gradlew (memory:
> `shedscope-build-toolchain`). Uncommitted — commit when Deuce says.

> **LIVE TASK (read this first):** Deuce and I are actively building the **Display Suite** —
> two new blocks: **Exhibit Pen** (specimen-disk-fed pasture clone, roams, NO breeding) and
> **Specimen Statue** (disk → frozen positionable statue via BER, no entity). Full detailed spec:
> **`docs/dev/DISPLAY_SPEC.md`**. Branch: `dev` as always. Context: born from Deuce's shiny zoo
> (Jurassic-Park island) on Tinderbeef's server — see memory `tinderbeef-server-zoo`.
>
> **STATE (2026-07-14, spec §7):** steps 1–4 CODED + building green, 389 tests pass.
> - Logic cores + suites: `display/ExhibitRules`, `StatueTransform`, `RenderSpec` (20 new tests).
> - MC wiring: `DisplaySuite` registration (GP's FIRST placed blocks), `ExhibitPenBlock(+Entity)`
>   with sweep lifecycle (respawn/leash/discard), `StatueBlock(+Entity)` with pose sync,
>   `CobblemonProjector` (the ONE Cobblemon seam, lazy - NeoForge rule), `EntityNoSaveMixin`
>   (projections never serialize - the anti-dupe backstop), `client/display/StatueRenderer`
>   (FREEZE_FRAME dummy, cosmetics-only spec, light sampled above plinth).
> - Assets: vanilla-placeholder models (composter=pen, chiseled bricks=plinth), loot, recipes
>   (8 planks/smooth stone ring + blank data disk), lang.
> - **NEXT:** in-game smoke on the QA instance (spec §4 checklist: dupe hunt, ball/battle attack
>   surface, spawn suppression), then the statue-controls FEEL PASS with Deuce (v1 map: empty
>   click=rotate, sneak=scale, stick=nudge sawtooth, sneak-stick=axis, shears=eject) + §5 open
>   questions (names, recipes, poses). NeoForge fold-back still rides the same batch.
> Everything below this block is the previous (publish-day) state, kept for reference.

---

# (previous) PICKUP_HERE - rewritten 2026-07-07 ~17:00 - PUBLISH DAY. IT SHIPPED.

> **READ THIS WHOLE FILE FIRST** after /clear or compaction. (This file MOVED: it now lives at
> `docs/dev/PICKUP_HERE.md` - repo root is public-facing.) Today Greener Pastures went from
> "morning QA list" to **submitted to Modrinth, under review**. The work now is the LANDING, not
> the building. Do not build features. Deuce's first release is in a moderator's queue.

## 0 · LIVE STATE RIGHT NOW
- **Version `1.0.0-beta.1`** - tagged on GitHub, jar `fc1cb377` (`greenerpastures-1.0.0-beta.1.jar`)
  deployed IDENTICALLY to all 4 installs (CF instance, both Prism GP-Masuda-QA{,-2}, WSL server
  `~/gp-qa-server/mods/`) + server RUNNING on it (port 25565). **The jar FILENAME changed** - old
  deploy scripts that copy `greenerpastures-0.1.0.jar` are stale; always rm-old + cp-new by name.
- **CURSEFORGE: APPROVED + LIVE (2026-07-08)** - Deuce shipped it there himself overnight:
  `curseforge.com/minecraft/mc-mods/greener-pastures-cobblemon`. His server's pack builder pulls
  from CF; Cobbreeding is MODRINTH-ONLY by its devs' explicit warning (any CF upload = fake) -
  packagers add it as an external file. Server-announcement post with all dep links: delivered.
- **Modrinth: SUBMITTED, UNDER REVIEW** (submitted 2026-07-07 ~16:10). Expect a few hours to ~48h. Deuce
  filled the draft from `PUBLISH_KIT.md` + `MODRINTH_BODY.md` (working copies in
  `Desktop/GreenerPasturesScreenshots/` alongside all 11 shots, `GALLERY_CAPTIONS.md`,
  `gp_icon_512.png`, and the beta jar).
  - **OPEN QUESTION: the slug.** `greener-pastures` was TAKEN (parked draft). Suggested fallbacks
    were `greenerpastures` → `greener-pastures-cobblemon`. **Ask Deuce which one took**, then sync
    it into LISTING/PUBLISH_KIT/README when the page goes live.
  - If a moderator asks about the bundled art: PMD Sprite Collab, 100% fan-made verified
    per-emotion/per-animation against credits.txt, zero CHUNSOFT rips ship, 45 artists credited
    in CREDITS-PMD.md + in-jar pmd_credits.txt + the About card. Answer in the Moderation tab.
- **GitHub: PUBLIC** - `github.com/DonaldGallianoIII/GreenerPastures` (renamed from
  pokemon-prediction; old URLs redirect). MIT LICENSE at root; third-party jars (Cobbreeding,
  fortunefix) PURGED from history via git filter-repo before flipping public. README = full
  storefront: icon, pitch, 11-screenshot gallery (docs/media/), repo map, build steps.
- **Branch law (Deuce's order, non-negotiable): ALL work on `dev`; `main` = Deuce-verified states
  only** (docs-only changes may fast-forward main so the public page stays current). Currently
  main == dev.
- Monitor: the session's log monitor dies with the session. Re-arm post-compaction:
  `bash <scratchpad>/gp-monitor.sh` shape - tail -F gp-logs/latest.log, grep the full arcade event
  alternation + WARN/ERROR, with `queue_full` EXCLUDED (known-noisy under QA overrides).

## 1 · WHAT SHIPPED TODAY (the whole arc, morning → submit)
1. **Arcade QA swept green**: Q102 VIBE CHECK (shuffle proven honest over 25+ live rounds),
   Q103 QUICK CLAW v3, Q104 High Roller (all 3 wares bought live; Legend Disk minted **Latios**).
2. **Balance (Deuce directives)**: Daemon Flip pots ×1.5 (one 2→3 tile swap/level, difficulty
   identical; L7 clear = 7,776) · VIBE CHECK base pot 1→2 AND frowns 4→3 (ceiling 512) ·
   QUICK CLAW wanderers ×4 speed + sprinters ×3 (max 12 concurrent).
3. **Icons everywhere**: High Roller shelf art (walk `highroller` array) · Prime Egg = generic
   cobbreeding egg · block items via model parent-chain (froglight!) + side/all slot preference
   (logs show bark not rings) · Harvester grid + ritual unclaimed-spoils grid show real item art.
4. **Fixes**: BUG-022 slots reel verified live · **BUG-023 stats-clobber** (1-min prefetch
   overwrote full pasture rosters with zeros → false "can't breed"; prefetch now carries stats +
   UI never warns on unknown gender) · breeder AND harvest QA-override twin fixes (immediate
   effect + no catch-up re-pricing; the 115-brood/43k-sweep bursts) · `/gp coins add` (QA-mode
   only) · **pastures.json** operator config (maxCatchupHours, 0-168, both catch-ups).
5. **The audit saga (READ THIS LESSON)**: 26-agent Opus audit → 7 confirmed findings → I shipped
   4 fixes → the ownership gate collided with Deuce's two-account test + a sick CF fat-pack
   instance (FancyMenu cursor GL errors) + an account-window mixup → an hour of "everything is
   fucked" fog → **full revert** (byte-identical rollback proved it) → GitHub was born from the
   scare. Findings live as documentation in QA_RESULTS (BUG-024). **The operator claim is
   BILLING, not an ACL** - any future pasture lock is opt-in and co-designed with Deuce first.
   Also: 3 of 4 audit fixes were "patched one twin, forgot the other" - sweep sibling handlers.
6. **owo-ui RETIRED** (Deuce: "rip it away completely"): NotebookScreen (690 lines) deleted,
   owo-lib dropped from gradle + fabric.mod.json (one less user dependency); no-MCEF now shows
   `InstallMcefScreen` (vanilla screen: explanation + Modrinth link button). MCEF is THE console.
7. **Release**: 369 tests · version bump → clean build → Deuce smoke-tested → main merged +
   tagged `v1.0.0-beta.1` → Modrinth draft (description = MODRINTH_BODY, gallery = 11 shots incl.
   Deuce's locked-riddles Rituals shot, icon = **Deuce's own SVG** converted via cairosvg) →
   SUBMITTED. Docs (LISTING/SHOWCASE/CHANGELOG) all current with today's numbers.

## 2 · THE LANDING QUEUE (in order)
1. **Check Modrinth review status** each session (project page → status / Moderation tab /
   notifications). Answer moderator questions promptly (PMD art answer above).
2. **On APPROVAL**, in order: swap README's "Modrinth: under review" line for the real URL
   (+ LISTING/PUBLISH_KIT slug refs) → Deuce posts the **Cobblemon Discord thread** (title + body
   drafted verbatim in `PUBLISH_KIT.md` §6 - first-release blurb included; attach
   VisusalScriptingWooloo, GameCornerShop, BioBankFroakie, Rits; drop the Modrinth link in the
   marked slot) → pin the link in-thread → watch GitHub Issues (public now).
3. **Beta-feedback posture**: fixes ship fast on `dev`, main advances on Deuce's blessing,
   versions go beta.2, beta.3... (bump = gradle.properties, renames jar).
4. Later, unhurried: CurseForge + Cobbleverse ports (same kit) · the shelved backlog below.

## 3 · SHELVED (do not resurrect without Deuce)
Reverted audit findings (QA_RESULTS BUG-024 - opt-in pasture lock needs co-design) · SprintTag
scripted-client timing (accepted for beta: free cabinet, closed coins) · Q82 Summit ritual ·
BUG-019 Lucky Egg glow · BUG-020 disc chips · gp-dev-backlog memory (field-guide removal,
echo/amethyst farmability, repel items) · snack-repellent seasonings (Deuce owes ingredient pick).

## 4 · TRAPS (all previously bitten)
Deploy by NEW jar name (beta.1), all four + md5, clients only when tasklist.exe shows no javaw
(WSL ps can't see Windows) · npm build cwd resets BETWEEN Bash calls (stale-HTML ships silently)
· server restarts = separate short commands, background port-waits, NEVER foreground until-loops
(zombie watchers once killed the live server) · python-with-count-asserts over Edit for big files
· em dashes forbidden in player-facing strings · balance directives arrive as jokes and are REAL
· two MC windows open = verify WHICH account before diagnosing "data loss" (usercache.json +
"UUID of player" login lines are ground truth; today's lesson, learned twice) · the CF fat-pack
instance has FancyMenu/MCEF/cursor issues that are NOT our mod.

## 5 · DEUCE
First release EVER - he's proud and a little nervous; celebrate wins, keep replies tight ·
publishing as **DonaldGalliano** (Deuce222XX in-game; alt = Phishing4Feebas) · he tests, you
watch logs + fix · boards with FULL descriptions · no p2w, Coins never convert to Data · hidden
content stays hidden · the GitHub avatar is the dog in the tie-dye bandana. The README no longer
says so, but she supervised everything.
