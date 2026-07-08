# 🎯 PICKUP_HERE - rewritten 2026-07-07 ~17:00 - PUBLISH DAY. IT SHIPPED.

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
