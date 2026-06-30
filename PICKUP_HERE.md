# ▶️ Pickup Here — Session Handoff

_**Greener Pastures** — public-release Cobblemon "A Data Science Mod", Fabric 1.21.1, Java 21, MIT. Logic-first
+ headless-tested. Read this first. `glow PICKUP_HERE.md`. Memory: `greener-pastures-project`,
`rituals-gacha-project`, `batch-qa-workflow`, `testing-and-logic-first`, `observability-first-logging`._

## ⚡ STATE — 2026-06-29 (Daemon buffs v2 · breeding-meta batch · F2 notifications · F3 goal tracker — all done)
**204 headless tests green** (`./gradlew test`). **Committed on `main`; a QA-deploy pass is NEXT** (Deuce: "1 and
then we qa after this" → F3 wiring done, now build+deploy Q1–Q37). QA is batched
(Deuce's call, [[batch-qa-workflow]]). Tree clean. Deuce is on **remote-control (phone)** → standing rule:
**functionality-first, NO custom UI** (defer all GUIs).

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

### 👾 BACKLOG — community feature requests (Deuce's #minecraft-chat thread; design refined 2026-06-29)
1. **Throw an egg → release a wild mon** (Wevic's chicken-egg idea). **Approach (Deuce + me):** NOT hijacking the
   vanilla chicken egg (would tangle real chicken eggs) → **our own thrown-egg projectile** that arcs like a chicken
   egg + carries the egg's species/IVs/shiny in NBT; right-click a Cobblemon egg → lob it → on impact spawn the wild
   mon (consume the egg). ⚠ verify the Cobbreeding egg item's current right-click behavior first (don't stomp hatching).
2. **MissingNo — custom cycling-glitch species.** Deuce's vision: a real custom Cobblemon species whose **rendered
   model cycles through fully-evolved mons every few sec** (5s Charizard → 5s Zoroark → 5s Entei…) — the "can't decide
   what it is" glitch. Spawns **WILD on an egg-throw at 1/8192**, with **1/8192 shiny** (shiny MissingNo ≈ 1-in-67M — an
   absurd chase the mass-breeding makes reachable; "fills the BioBank endlessly"). **Boost via our augment/tether system**
   — add `MISSINGNO_CHANCE` + `MISSINGNO_SHINY` functions (fed tether amplifies + drains Data, exactly like Shiny/IV
   Floor — one constant each, zero new architecture). ⚠ **RESEARCH NEEDED before estimating:** Cobblemon custom-species
   API + whether the **model-cycling** is feasible (swap an entity's rendered model on a timer via species/aspects) —
   fallback = a static corrupted/scrambled MissingNo texture.
3. **Ball cosmetics for eggs** (Deuce's "5,000 eggs → cherish ball"). Effect already exists (the `BALL` augment, Q32).
   Needs: the **economy layer** (spend eggs/Data → unlock a ball) + a **UI selector** (Deuce wants it like the
   visual-scripting GUI — publish-phase). Open Qs: pay in eggs or Data? unlock-once or per-batch?
_(Tin's "mass farms are unethical" + Deuce's "that's the whole point" = the ghost-pasture mission statement.)_

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
