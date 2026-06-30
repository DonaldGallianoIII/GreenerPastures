# ▶️ Pickup Here — Session Handoff

_**Greener Pastures** — public-release Cobblemon "A Data Science Mod", Fabric 1.21.1, Java 21, MIT. Logic-first
+ headless-tested. Read this first. `glow PICKUP_HERE.md`. Memory: `greener-pastures-project`,
`rituals-gacha-project`, `batch-qa-workflow`, `testing-and-logic-first`, `observability-first-logging`._

## ⚡ STATE — 2026-06-30 (QA pass DONE + **ALL backlog bugs fixed AND in-game-verified**; next = web-dev UI stack)
**230 headless tests green** (+26 across the batch). **All committed on `main`, tree clean.**
This session Deuce ran the **full QA pass at his computer** → the **entire 15-buff Daemon suite verified end-to-end
from the live log** (Fortune / Auto-Smelt / Vein / XP / Potion / Attributes / Lure / Luck across all 4 mixin seams +
tier-scaling + the drain economy); the rest **trust-confirmed** on his call. `QA_PENDING.md` COMPLETE; findings in
**`QA_RESULTS.md`** (BUG-001…006, with a batch-status banner up top).

**🐛 BUG-FIX BATCH — DONE + ✅ ALL QA-VERIFIED in-game (`035ff6b`):** **BUG-001** drop-rate scales per Kernel tier ·
**BUG-002** EV per-stat allocation (`/gp augment ev <6>`) · **BUG-006** `BreedingCompat` core + dead-pair log ·
**BUG-003** Ghost Pasture increment-2 re-materialise (un-hide rings mons around the pasture, no dupes) · **BUG-004**
Daemon **compile-your-own** redesign (`DaemonLoadout` + `BuffResolver.resolveLoadout` + ON/OFF enchant-glint toggle +
whole-inventory grant + `/gp daemon` no-UI path; glint/inventory/drain-only/OFF/broke/validation all pass). Only
**BUG-005** stays open by design (a UI ask → web-dev pass). **The entire backlog is closed.**

**▶️ ON RESUME → the WEB-DEV UI STACK.** Backlog code is clear; the phase Deuce named next is **"pull in the web dev
stack"** to build every deferred UI: **BUG-005** node detail + zoom · **BUG-006** graph wire-time feedback · **BUG-002**
EV allocation screen · **BUG-004** Compiler loadout GUI · the **analytics dashboards** (#6). Direction + the
recommended first move are in **`PORTING_WEB_UI.md`** — Deuce wants to **develop the UI as a real web app *with Claude
Code*, then move it into MC** (Option B localhost bridge = the most natural "build-in-web-stays-web"; owo-ui for
in-world; leaning away from MCEF-as-default but **will package Chromium if that workflow needs it**). ⚠️ **Confirm which
surface to start** (B dashboard PoC vs. C owo-ui pilot) — open Qs are at the bottom of `PORTING_WEB_UI.md`.

**🚚 DEPLOY STATE:** ✅ **DEPLOYED 2026-06-30 — jar md5 `035ff6b`** (5 fixes + BUG-003 un-hide NPE fix + placement refine;
chain `a93af8e`→`edf05bf`→`4da7999`→`035ff6b`) in *Greener Pastures Test*/mods. **In-game results so far:** **BUG-004
Feather Falling ✅ verified from the live log** (`/gp daemon` compile + ON glint + inventory-grant + drain-only-installed —
`buff tick buffs:1 paid:1,1,1,0` is the 0.75/s fractional carry). **BUG-003 un-hide:** ✅ **respawn works** (NPE
root-caused + fixed — `makeSuitableY` returns null on the solid pasture block; `suitableSpawn` mirrors Cobblemon's
search; decompile confirmed `checkPokemon` ignores `entityId` so respawns persist). Re-test showed mons **clumped one
side** → **placement refined** to ring them N/E/S/W around the pasture, grounded — ✅ **re-confirmed in-game on `035ff6b`** — mons ring the pasture, grounded, no dupes on repeat toggles; **the entire backlog is now in-game-verified**
(toggle a ghosted pasture OFF → mons should stand right next to it on all sides). Mixin **loads clean ✅**.

_(historical 2026-06-29 handoff below)_

## ⚡ STATE — 2026-06-29 (HUGE session: buffs v2 · breeding-meta · F2 notify · F3 goal tracker · ghost pasture · community-wave research)
**204 headless tests green** (`./gradlew test`). **All committed on `main`, tree clean.** Deuce on
**remote-control (phone)** through a chaotic IRL day (pup got a Cushing's dx — managed/ok; contractors at the house)
→ standing rules: **functionality-first, NO custom UI**; **batch-QA** ([[batch-qa-workflow]]).

**🚚 DEPLOY STATE:** v2 features were deployed ONCE for QA (jar md5 `43679bc`, in the *Greener Pastures Test*
instance). **Everything since — ghost-pasture inc1 + all the docs — is NOT redeployed.** ⚠ The **ghost-pasture mixin
must deploy SEPARATELY**, not bundled into Deuce's v2 QA jar: it's a new Cobblemon `@Redirect` I can't verify
headless, and a required mixin that fails to resolve would break the WHOLE mod load. Have Deuce confirm the log
loads clean before trusting it.

**▶️ ON RESUME — open threads (nothing forced; Deuce compacted here, mid-chaos):**
1. **QA backlog Q1–Q38** — Deuce runs it down when at his computer (needs a full MC restart). No-UI test affordances:
   the `/gp augment` + `/gp goal` commands (cheatsheet atop `QA_PENDING.md`).
2. **Ghost Pasture increment 2** — re-materialise discarded roamers on un-suppress (details in the Ghost Pasture
   section below). Deploy + QA inc1 (Q38) first.
3. **Community feature wave** — research DONE + clean; **ready to build pending Deuce's GO + 2 decisions** (community
   section below + `COMMUNITY_FEATURES.md`).

**Most recent work (this session):** finished the **Daemon buff system** (15 buffs — see the DONE-task section) →
then the **v2 feature wave** Deuce surfaced. Wrote the full spec **`FEATURES_V2.md`** (`glow` it). **Completed F1 —
the whole breeding-meta augment batch:** **Nature** (`6a7df50`) · **Ball** (`5425318`) · **Hidden Ability**
(`950c8ad`) · **Egg Moves** (`4a06bac`). Each writes the trait onto the egg `PokemonProperties` pre-encrypt (same
seam as IV/EV), fail-safe + non-destructive; all 4 Cobblemon APIs verified against the jar. Also did the `EggShape`
params refactor. QA rows Q31–Q34.

### ▶️ ACTIVE — v2 feature wave (Deuce: "do all of it + keep adding to QA"; spec = `FEATURES_V2.md`)
Build order **F1 breeding-meta ✅ → F2 Notifications ✅ → F3 Goal tracker ✅ (track-only)** — all shipped. Re-sync
before wave-2 (hopper interop, dashboards #6, economy, guide). **F3 v1 done** (`/gp goal` — the mod's first command;
`goal/` package: `BreedingGoal`+`GoalProgress` pure cores, `GoalStore` per-player, `GoalTracker` folds each laid egg
into the pasture owner's progress + pings on reached; `BredEgg` enriched with species/ivTotal/perfectIvs). QA Q37.
**Non-destructive** — auto-cull is the deferred follow-on (off-target non-shiny → Renderer→Data, shiny 4-guard, opt-in).
Deployed once (md5 `43679bc`) for QA, then kept building. (Follow-ons after QA: F3 auto-cull + persistence, F2
pulls-ready ping, the breeding-meta Compiler-UI install UX, wave-2 features. All GUIs still deferred.)

### 🌫️ ACTIVE — Ghost Pasture (admin-critical lag fix; the old "no-wander" was BROKEN)
A server admin called per-pasture spawn-suppression critical. **The old PastureKeeper no-wander never worked** — it
hooked Cobblemon's `togglePastureOn`, which only flips the block's ON *visual* (QA confirmed it does nothing). The
real spawn is the single `World.spawnEntity` in Cobblemon's `tether()`. **Deuce approved this method (no per-tick
despawn):** (1) `@Redirect` that `spawnEntity` for suppressed pastures → tether data recorded, no entity spawns;
(2) one-time despawn-on-toggle with `RemovalReason.DISCARDED` + re-assert `tetheringId` (verified: `PokemonEntity.remove`
only untethers when `shouldDestroy()`, so re-asserting keeps the tether; DISCARDED = gone-for-good, no reload);
(3) flag persisted on `PastureData.suppressed`. **Increment 1 DONE + builds** (`PastureKeeper` rework + the new
`PokemonPastureBlockEntityMixin` `@Redirect` + persistence) — QA **Q38**, **NOT yet redeployed**. **Increment 2 (next):**
un-suppress must **re-materialise** the discarded roamers (replicate Cobblemon's tether-spawn from the data) — right now
un-suppress only re-allows future spawns. Verify in-game: the `@Redirect` resolves (no mixin load error) + suppressed
mons stay tethered (breeding keeps producing).

### 👾 ACTIVE — community feature wave (Deuce's #minecraft-chat thread · **full spec = `COMMUNITY_FEATURES.md`**)
4 LOCKED features: **C1 ball cosmetics** (spend eggs/Data → unlock a breeding ball; the `BALL` augment already does
the effect, Q32) · **C2 egg-throw** (sneak-RC an egg → a `ThrownItemEntity` carries the stack → impact spawns the
wild mon) · **C3 MissingNo** ⭐ the centerpiece (1/8192 crack + 1/8192 shiny on egg-throw → a wild **glitch mon that
cycles random fully-evolved models every ~5s**; a `MISSINGNO_CHANCE`/`MISSINGNO_SHINY` augment+tether; + the
**item-dup Easter egg** — MissingNo conjures matter, the on-theme keystone) · **C4 cake** (eggs→cake recipe, trivial).

**✅ BOTH research passes DONE + clean** (commit `7147159`):
- **C2 ready:** egg = `ludichat.cobbreeding.PokemonEgg`; trigger = Fabric `UseItemCallback` on sneak-RC (don't override
  Cobblemon; hatching is player-inv-gated so no mid-air hatch); carry the whole stack on a `ThrownItemEntity` subclass;
  impact → `EggUtilities.extractProperties` → spawn wild.
- **C3 cycling = TIER A / CLEAN — pure server-side, NO client mixin/renderer.** Use Cobblemon's **illusion system**
  (Zoroark's mechanic): `entity.getEffects().setMockEffect(new IllusionEffect(PokemonProperties.parse("species=<x>
  shiny=<f>"), scale))` on a ~100-tick timer cycling `PokemonSpecies.getImplemented()` (no-evolutions). Real mon keeps
  identity. Custom species optional + **data-only** (zero Java).
- **Spawn API (shared C2+C3, verified):** `PokemonProperties.Companion.parse("<sp> level=N shiny=<f>"," ","=").create()`
  → `pokemon.sendOut(serverWorld, vec3dPos, illusionOrNull, null)`.

**Ready to build pending Deuce's GO + 2 decisions:** (a) **item-dup** payoff = faithful slot-6(+128) vs themed
eggs/Data + the admin config shape; (b) **ball cost** = eggs vs Data, unlock-once vs per-batch. **Build arc if
green-lit:** C2 throw → C3 glitch mon → 1/8192 odds + the `MISSINGNO_CHANCE`/`SHINY` augment → then item-dup + ball
economy per the decisions; C4 cake anytime. _(Tin's "unethical farms" + Deuce's "that's the whole point" = the
ghost-pasture mission statement.)_

## ✅ DONE TASK — Daemon global "root" buffs  _(15 BUFFS LIVE — QA-pending; enchant set COMPLETE)_
**Deuce's two design calls (locked):** buff **tier = the held Daemon's Mk level** (I/II/III); Data drain =
**tier-scaled & summed** (`Σ tier × costPerSec`/sec). All committed (`5ba97e8`→HEAD), 167 tests, nothing
deployed. **All 15 delivered buffs are non-destructive** (effects/attributes/read-interception — none rewrite
gear or persist NBT, so no dupe surface). QA rows **Q23–Q30**. **Only undelivered catalog enchant = Unbreaking**
(Deuce deferred it — no entity-scoped seam). **Looting = live, default-on** (Deuce opted in; the one combat buff).
**DELIVERED (held + fed Daemon, billed only for what's in `DaemonBuffs.SUPPORTED`):**
- cores (`5ba97e8`): `BuffId` catalog (11 ENCHANT / 2 EFFECT / 5 HOOK — the catalog IS the worker-not-fighter
  allow-list), `BuffSetting`/`BuffConfig` (lazy-Gson, `config/greenerpastures/buffs.json`), `BuffResolver`
  (level→tier, per-buff cap, summed drain, per-`BuffId` "deliverable" filter), `BuffSystem`.
- **Haste + Saturation** (EFFECT, `0b4684d`) — `DaemonBuffs` per-sec settle loop bills Data (fractional carry,
  broke→none), applies status effects. `daemon_level` component + **creative sneak-RC cycles Mk I→II→III** (QA
  affordance; survival recipe = publish-phase). `DaemonItem.levelOf()`. `lastPaid` cache.
- **Item Magnet** (HOOK, `a796175`) — per-tick pull, radius 4/6/8.
- **Fortune** (ENCHANT, `51bb11d`) — the marquee, "beyond vanilla max" (Mk III = Fortune VI). The verified-clean
  way: read-only `getLevel` `@Inject` gated by a loot-scoped ThreadLocal set in `Block.getDroppedStacks`
  (`BlockDropBoostMixin` + `EnchantmentLevelMixin` + `DaemonEnchantBoost`). Never writes the stack. See
  `ENCHANT_BOOST.md` (the rejected component-edit approach + why).
- **Auto-Smelt** (HOOK, `58612c5`) — smelts mined drops; same block-drop mixin window, composes with Fortune.
- **XP Boost** (HOOK, `725bce0`) — `XpBoostMixin` +25%/tier on `addExperience`.
- **Vein-Miner** (HOOK, `28e9b83`, hardened) — `DaemonVeinMine` via `PlayerBlockBreakEvents.AFTER`; **heavily
  fenced** (ores `c:ores` + logs only, same-block flood-fill, cap tier×32≤96, suitable-tool, re-entrancy guard).
  **Whole-vein compliant:** every block routes through `Block.getDroppedStacks(…, player, tool)` (the same path
  a normal break uses), so Fortune + auto-smelt AND the tool's own enchants (Silk Touch etc.) apply to *every*
  block, not just the first; per-block try/catch + boost-window cleanup so one odd block can't abort it. Data
  rental = the cost (no tool durability v1).
- **Potion Duration+** (HOOK, `bd7d252`) — `PotionDurationMixin` +50%/tier on a **utility allowlist only**
  (NightVision/WaterBreathing/FireRes/Conduit/Dolphin/SlowFall/Luck) → PvP-neutral.
- **Attribute enchants** (ENCHANT, `f8743a2`) — **Respiration / Swift Sneak / Feather Falling** delivered as
  transient `EntityAttributeModifier`s (NO mixin) reconciled in the `DaemonBuffs` settle loop. Pure
  `AttributeBuff` table (tested) → `DaemonAttributeBuffs` (binds `GENERIC_OXYGEN_BONUS`/`PLAYER_SNEAKING_SPEED`/
  `GENERIC_FALL_DAMAGE_MULTIPLIER`, all verified consumed in the 1.21.1 jar). Granted **flat** + stack on top of
  real gear (beyond vanilla max, like Fortune); temporary (never saved); `DELIVERED` folded into `SUPPORTED` so
  the bill can't drift from what's applied. Feather Falling floored so it can never heal on a fall.
- **Value-effect enchants** (ENCHANT, `02e1501`) — **Lure / Luck of the Sea / Frost Walker** via entity-scoped
  read interception of `EnchantmentHelper` (`getFishingTimeReduction` +5s/tier · `getFishingLuckBonus` +tier ·
  `getEquipmentLevel` +tier for frost_walker, granted from nothing). `DaemonValueBoost` + `EnchantmentValueBoostMixin`
  (3 `@Inject RETURN`). Same read-only safety as Fortune (no stack write). These seams **carry the entity**, so
  no ThreadLocal needed — gated directly on `paidBuffs`. Server-side only.
- **Looting** (ENCHANT, this commit — Deuce opted in, **default-on**) — the one combat-adjacent buff; +tier mob-loot
  via the SAME `getEquipmentLevel` seam (`EnchantedCountIncreaseLootFunction` → `getEquipmentLevel(LOOTING, killer)`,
  verified). 2-line enable: `Enchantments.LOOTING → BuffId.LOOTING` in `DaemonValueBoost.EQUIP` + `BuffId.LOOTING`
  in `DaemonBuffs.SUPPORTED`. (No effect on Deuce's no-mob server; it's for public servers.)

### ✅ TASK #30 COMPLETE — enchant set finished
Spec'd in `ENCHANT_BOOST.md`. **All decided:** attribute tier (Respiration/Swift Sneak/Feather Falling) + value-effect
tier (Lure/LotS/Frost Walker) + **Looting (in, default-on)**. **Skipped/deferred (closed decisions):** Efficiency
(Haste covers mining), Soul Speed (soul-block-gated), **Unbreaking (Deuce deferred — `getItemDamage` carries no entity
to scope it; only a hacky ThreadLocal would work)**. The buff system is feature-complete pending QA (Q23–Q30).
Next real work is elsewhere (analytics dashboards #6, or whatever Deuce points at) — NOT more buffs.

_(original spec below — still the source of truth for the buff list)_

Deuce: *"handle all the daemon global buffs now."* **Spec is SETTLED in `~/pokemonthink/AUGMENTS_AND_BUFFS.md`**
(read it). The `DaemonItem` (`economy/DaemonItem.java`) currently only shows the Data balance on right-click — add the
buffs it grants **while held + fed** (rented via Data: lose fuel → lose buff; **config-gated; "worker-not-fighter" —
QOL/farming only, zero combat**, so the mod stays PvP-neutral).

**Enchant buff — SETTLED, +1/+2/+3 beyond vanilla max (tier-scaled):**
- *Included (ride to +3):* Efficiency · Fortune · Luck of the Sea · Lure · Unbreaking · Looting (gathering) +
  Respiration · Soul Speed · Swift Sneak · Frost Walker · Feather Falling (QOL/movement).
- *Excluded (all combat):* Sharpness/Smite/Bane · Power/Punch/Flame · crossbow · Knockback/Fire Aspect/Sweeping ·
  Protection family · Thorns · trident.
- *Auto-skip (single-level/capped, tooltip should say why):* Silk Touch · Mending · Aqua Affinity · Infinity · Flame ·
  Channeling · Depth Strider.
- Fortune/Luck/Looting **individually config-cappable** (shop-economy servers).

**Other buffs (worker-not-fighter):** auto-smelt-on-mine · vein-mine/batch-break (see `vein-miner-fix/`) · item magnet ·
haste/efficiency · XP boost · potion-duration+ (⚠ exclude combat potions) · saturation/no-hunger. **CUT:** any
chunk-loading buff (pastures are never loaded).

**Suggested build order (logic-first):** (1) `BuffConfig` (per-buff enable + tier + Data cost) + a pure resolver, in a
new `buff/` package, unit-tested — mirror the `ritual/RitualConfig` pattern (lazy-Gson holder so the cores stay
test-runnable; fail-safe load; master + per-buff toggles). (2) MC adapters: status-effect buffs (haste/saturation/
potion-dur) applied while the Daemon is held on a server tick + Data drain; event-hook buffs (auto-smelt, vein-mine,
magnet, XP); the enchant +N boost (the hard one — research how to add beyond-vanilla enchant levels in 1.21). (3) GpLog
lines + QA rows. **Open design Qs:** does buff tier come from the held Daemon, a buff-tether, or config? what's the
Data drain/tick per buff? (Deuce to confirm — ask before wiring the cost model.)

## 🗺️ Systems map (all built + tested; MC adapters QA-pending Q1–Q22)
- **Breeding:** `MultiPairBreeder` (Fabric tick, no mixin) + `CobbreedingBridge` (egg-gen via `getPossibleEggs`/
  `chooseEgg`; bounded shiny proc; **IV Floor + EV** shape `eggData` before encrypt). `PastureData`/`PastureRegistry`
  (persistent, per dim+pos). Suppresses Cobbreeding's native ticker so only configured pairs lay.
- **Augments:** `Augments` component = `{function→level}` map. `AugmentFunction` (7: shiny/speed/iv_floor/ev/enrichment/
  drop_rate/drop_yield — **all have live effects**). `AugmentType` = the 7 **craftable** Compiler augments
  (`augment_<fn>` items, generic over function). `Compiler` block/menu installs them onto a Kernel.
- **Soul Tethers (rented amplification):** `EffectiveAugments` (base × tether). `TetherRuntime.resolveFor(base,
  tethers, balance, FUNCTIONS)` = the **per-consumer drain split** (disjoint sets, billed once): **breeder** drains
  shiny/speed/iv_floor/ev · **Harvester** drop_rate/drop_yield · **Renderer** enrichment. Fed→amplify+drain Data /
  starved→free base. Operator = explicit locked-boolean `PastureClaim` (who pays).
- **Dark economy:** `Renderer` block culls non-keeper eggs → **Data** (SACRED shiny 4-guarded). `DataStore` (per-player,
  persistent). `DaemonItem` (shows balance; **buffs = this task**). `BASE_DATA_PER_EGG=2` (balance pin).
- **Drops (Harvester block):** staples = each tethered mon's Cobblemon `getDrops` (faithful), gated by **3%/mon/min
  proc (LEVER 1, +Drop Rate)** → **amount budget (LEVER 2, +Drop Yield)**. Ground-free → own 9×3 chest. `DropsBridge`,
  `CompositionReader`.
- **Rituals + type-drops (`ritual/` pkg, config `config/greenerpastures/rituals.json`):** Tier-1 **type-drops**
  (mon type → staple item) + Tier-2 **gacha rituals** (pasture composition of types + signature species → banks
  **pulls** → `Gacha` rolls base% + **pity** → rare item). Live in the Harvester (`customDrops`), auto-pull interim,
  pity persisted in block NBT. **Rituals accrue 1/`rarityFactor`× (default 3) the staple proc and scale with the SAME
  Drop Rate/Yield augments.** Recipes are tunable config **placeholders — DEFERRED** (Deuce finalizes later; e.g. his
  enchanted-golden-apple idea = legendary Grass + legendary Fairy + a gold-dropper, parked in `RITUALS.md`).

## 🛠️ Conventions & gotchas
- **Build/test:** `cd greener-pastures && JAVA_HOME=/home/donaldgalliano/jdks/jdk-21.0.11+10 ./gradlew test` (~6–10s,
  plain JUnit 5, NO MC boot). `./gradlew build` for the jar (JRE-only box → must use the downloaded JDK).
- **Logic-first** ([[testing-and-logic-first]]): MC-free core (own pkg) → unit test → thin MC adapter → UI last. Tests
  must run without Gson/MC on the test runtime → **lazily load Gson via a holder class** (see `RitualConfig`).
- **Config-driven + admin-toggleable** is the house style now (rituals): JSON in `config/greenerpastures/`, master +
  per-item toggles, fail-safe load (missing→write defaults, corrupt→fall back, never crash).
- **Observability** ([[observability-first-logging]]): every feature logs JSONL via `GpLog.{d,i,w}(tag, event, k,v…)` →
  `~/gp-logs/latest.log` (off-thread, never-crash).
- **Commit-not-deploy** ([[batch-qa-workflow]]): stack tested increments, commit each to `main`, QA in dedicated bulk
  sessions, deploy only when asked. Commit trailers: `Co-Authored-By: Claude…` + `Claude-Session:…`. Every MC/adapter
  change → a row in `QA_PENDING.md` (pure-logic cores are trusted via tests, no row).
- **Decompile** ([[jar-decompile-workflow]]): `javap`/CFR (`~/cfr.jar`) on the **remapped** jars under
  `greener-pastures/.gradle/loom-cache/remapped_mods/…` (yarn names match code). Cobblemon type API verified:
  `Pokemon.getTypes()→ElementalType.getShowdownId()`; IVs/EVs via `Stats.Companion.getPERMANENT()`, `ivs.set(stat,31)`.
- **yarn 1.21.1:** `net.minecraft.component.ComponentType` (not Mojmap). Cobblemon/Cobbreeding = `modCompileOnly`.
- **When UI eventually comes** ([[viewport-ui-principle]]): plain Screens never call `super.render()` (blur bug) /
  use `GpButton` not `ButtonWidget`; viewport-relative + fit-to-content; never overload left-click for pan+action.
- **Deploy (only when asked):** copy `build/libs/greenerpastures-0.1.0.jar` → `/mnt/c/Users/deuce/curseforge/minecraft/
  Instances/Greener Pastures Test/mods/` + **full MC restart**.

## 📁 Key paths
- Mod: `greener-pastures/` → `build/libs/greenerpastures-0.1.0.jar`
- This repo's design/state: `RITUALS.md` (drops/rituals/gacha) · `DESIGN_GAP.md` (design↔build map) · `QA_PENDING.md`
  (Q1–Q22) · `BUG_HUNT.md` · `cobblemon-drops-ref/` (`FARMABILITY_GAP.md`, `sim_drops.py`, `sim_rituals.py`,
  `all-species-drops.json`).
- **Design source of truth:** `~/pokemonthink/` — `AUGMENTS_AND_BUFFS.md` (**buff spec — the active task**),
  `DAEMON_AND_TETHERS.md` (tether/Daemon mechanics), `ITEM_TAXONOMY.md`, `STATE_AND_PLAN.md`.
- Test instance / deploy target: `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/`.
