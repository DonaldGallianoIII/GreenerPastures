# Adversarial Review - consolidated triage (2026-07-05, six Opus reviewers, all findings code-verified)

Full agent reports summarized + deduped. Status column tracks fixes.

## CRITICAL - all FIXED in jar 8aeb396e (same-day)
| # | finding | fix |
|---|---------|-----|
| C1 | SACRED-RULE HOLE: a failed egg decrypt produced a zero-filled card (shiny=false, IVs 0) that IV filters happily VOIDED - shinies included. API drift = whole broods rendered silently. | `ivsKnown` now rides EggCard; GraphEval keeps any unreadable egg. |
| C2 | Dedicated servers disable the star-name shiny fallback, so one mis-read shiny was voidable. | Covered by C1 (read failure = unreadable = kept). |
| C3 | BLESSED corruption overwrote installed augments with level I - could DOWNGRADE a level-II (today's leveling regression). | Gift is now never-worse: picks an improvable augment, upgrades I to II beyond cap; all-maxed = fizzle. |
| C4 | MissingNo tethered into a pasture froze (ticker scans parties only) and, frozen on Ditto, became an immortal universal breeding parent. | Tether refused at source (mixin, with message) + breeder pair-filter excludes flagged mons. |

## MAJOR - ALL FIXED 2026-07-05 round 2 (jar 665e9e84) with Deuce's design calls
Deuce's decisions: TIER III augments exist and CORRUPTION IS THE ONLY DOOR (BLESSED climbs
I->II->III at 2x base values, IV floor capped 5); WILD on a Greener pushes an installed augment
one tier past the mortal ceiling instead of the impossible +1 pair.
- SpanGate: banks only as the GLOBAL MIN of the satisfying clique (exactly 1 bank per clique; disjoint pairs still independent). Test updated.
- Catch-up amplification: paid PER BROOD; wallet runs dry mid-burst -> rest runs starved. No more free shiny batches.
- Pause exploit: loaded-but-not-breeding pastures advance lastBreedTick - toggling can't bank fake catch-up.
- autoPull=false: IGNORED with a load-time warn until the manual-pull screen ships.
- Repel payload now SURVIVES re-compression (input snacks' REPEL_TYPES merge like cans).
- Overdrive: cancel-first compute/commit split - a throw can never double-drive vanilla; only executed spawns are paid.
- Perf: GraphEval parsed-JSON LRU (catch-up = 1 parse not thousands) · BioBank flatten 5s floor per viewer · GpRepelInfluence species->types memo.
- Pity now VISIBLE: learned ritual cards show "pity N/hard" (max across owner pastures; ledger for spanning).

## PREVIOUS TRIAGE (original findings, kept for history)
- **SpanGate banks at (N-1)x with 3+ mutually-satisfying pastures** (SpanGate only dedupes pairs). Fix: bank only when self is the GLOBAL min of the satisfying clique.
- **Free amplified catch-up broods**: tether affordability checked for ONE cycle, then a lump all-or-nothing debit fails and the whole amplified batch is free. Fix: per-brood debit or cap amplified broods to affordable count.
- **Pause exploit**: toggling breeding off (chunk loaded) banks catch-up; re-enable = ~288-brood dump. Fix: advance lastBreedTick while skipped-but-loaded.
- **WILD +1 pair is DEAD on a Greener** (bucket cap 8 + 16-mon roster makes pair 9 impossible). Design call: different Greener jackpot (e.g. guaranteed double-drop mod)?
- **autoPull:false = all rituals silently dead** (no manual-pull UI exists). Fix: remove/ignore the knob until the UI ships.
- **Re-compressing a repel snack silently destroys its repel payload** (ultra craft never reads REPEL_TYPES off input snacks). Fix: merge input snacks' payloads like cans.
- **Overdrive fail-soft is mislabeled**: credits spent + spawns fired BEFORE ci.cancel - a mid-loop throw double-drives with vanilla. Fix: compute-then-commit ordering.
- **Catch-up reload spike** (likely our 76/129ms breeder.scan watch item): up to ~2,300 full egg builds + per-egg graph JSON re-parse in ONE tick. Fix: parse graph once per pasture per tick + spread catch-up broods across ticks.
- **pushBiobank re-flattens/resends the whole bank ~1x/s to every open console during active breeding.** Fix: per-tab pushes or coarser rev-gate.
- **GpRepelInfluence resolves species+form per spawn CANDIDATE uncached.** Fix: small species->types memo.

## UX BATCH - SHIPPED 2026-07-05 round 2 (items below marked [x] done, [ ] deferred)
[x] wand/Field Guide stale copy · [x] first-join chat line + FIRST-ever open lands on Guide ·
[x] auto-wired BioBank sink on first parent (the tray-stall trap) · [x] queue-full Inbox note ·
[x] "Inventory [E]" header · [x] Guide gains Key Recipes + Snack Science + shared-account note ·
[x] pity chips · [x] MissingnoCard hidden at 0 · [x] PC-aware summon message · [x] GPU/Illicit
never consumed from offhand · [x] battle refusal messages BOTH sides · [x] 7th berry refuses ·
[x] BRICKED degrade names the augment loss · [x] written-disk death warning ·
ROUND 3 (same day - Deuce: "I want to do them now"): [x] ghost pastures pruned from the tab the
moment their record dies · [x] Speed on a floored kernel now REFUSES for free ("GPU saved") ·
[x] dashboard scope labels honest · [x] verb: pull everywhere for withdrawals · [x] dead deposit
hint removed · [x] orphaned ritual loot explains itself · [x] jukebox Inbox line honest about
pools · [x] odometer saturation · [x] span snapshot prune · [x] breadcrumb isolated (no triple
outcome) · [x] phantom doc refs. Still deferred (accepted): Cobblenav re-place notice (Deuce's
world only), 1s buff grace window, mixin target brittleness (pack pins Cobblemon 1.7.3).
CLOSED (Deuce, 2026-07-05): WILD variant-0 IS the intended jackpot - it doubles whatever drop
rate the kernel carries, born base included, and slaps the mod on even if none was installed.
Working as designed; no code change. Review cycle fully closed - zero open findings.

## ORIGINAL UX NOTES (history)
- STALE COPY: "right-click with the wand" (Pastures empty state) and "right-click the Field Guide item" (Guide/Specimens footer) - items that no longer exist. EMBARRASSING, trivial fix.
- First 30 seconds: silent gift (no chat line), lands on empty BioBank jargon, Guide tab buried last. Fix: gift chat line + first-open lands on Guide.
- Node graph: no default sink wired -> eggs stall in tray with no in-UI warning; egglog hint appears only after the fact. Fix: auto-wire pair->BioBank on line creation (or a loud unwired warning).
- Breeding-stopped (queue full) is a log line only - no Inbox/chat. Fix: Inbox note.
- E key hides the inventory overlay with no hint to restore. Dead "shift-click -> storage" hint text for a deposit path that no longer exists.
- No recipe breadcrumbs for Kernel/Daemon/GPU anywhere in UI. Guide doesn't cover snacks/repels at all despite claiming "everything".
- Ritual pity/banked pulls invisible (the entire APEX safety net); no "composition broke" signal; locked cards lack a "how rituals work" line.
- Dashboard mixes three time-scopes under "this session". MissingnoCard is zalgo noise at 0 lifetime (hide until first Data?).
- Summon says "joins you" when it overflowed to PC. GPU/Illicit consumed from OFFHAND. MissingNo battle-refusal invisible to the PvP opponent (and = PvP dodge). Tether inert without LINK, no feedback. Speed-on-floored-kernel wastes GPU silently. BRICKED degrade doesn't mention augment loss. Disk = droppable Data with no death warning. 7th berry silently wasted; empty can kills ultra craft silently. Cobblenav needs re-place for old repel snacks - never surfaced. Verb soup: pull/withdraw/take/ARCHIVE. Ghost pastures linger in Pastures tab. Two Notebooks share one pool (surprise). TargetCards appear unexplained at 2nd kernel.

## MINOR (post-launch fine)
- Span snapshot maps never prune dead pasture keys (bounded by resetSession).
- Orphaned ledger loot (removed item ids) unwithdrawable without message; malformed id can throw in handler.
- Pool-hit Inbox line reports only the LAST disc rolled.
- EggIngest double-outcome if the breadcrumb block throws (egg + Data + void log).
- BioBank fromNbt drops over-cap eggs on hand-edited saves (warn only).
- lifetimeEarned lacks saturation guard (theoretical wrap).
- 1s free-buff window after going broke (bounded, acceptable).
- WILD var-0 writes a drop mod onto kernels that had none (confirm intended).
- Phantom doc refs: "RepelFold", "PokeSnackRepelMixin" in comments.
- Mixin synthetic-lambda target = load-crash on Cobblemon recompile (accepted; pack pins 1.7.3).

## CLEARED (verified sound)
Dupe surface (all withdrawal paths) - gacha math + pity - config fail-safety - double-click summon race - odometer exploit - Specimen dupe-order + removed-species disks - form-key semantics - music disc ids - stale-while-revalidate - world-change cache hygiene - refusal-message consistency - L/shift/R conventions - vein-mine gating - offline drain - BioBank un-halt on next brood.
