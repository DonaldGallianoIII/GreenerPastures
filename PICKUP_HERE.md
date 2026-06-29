# ▶️ Pickup Here — Session Handoff

_**Greener Pastures** — public-release Cobblemon "A Data Science Mod", Fabric 1.21.1, Java 21, MIT. Logic-first
+ headless-tested. Read this first. `glow PICKUP_HERE.md`. Memory: `greener-pastures-project`,
`rituals-gacha-project`, `batch-qa-workflow`, `testing-and-logic-first`, `observability-first-logging`._

## ⚡ STATE — 2026-06-29 (Daemon buffs v2 · breeding-meta batch · F2 notifications — all done)
**189 headless tests green** (`./gradlew test`). **All committed on `main`, NOTHING deployed** — QA is batched
(Deuce's call, [[batch-qa-workflow]]). Tree clean. Deuce is on **remote-control (phone)** → standing rule:
**functionality-first, NO custom UI** (defer all GUIs).

**Most recent work (this session):** finished the **Daemon buff system** (15 buffs — see the DONE-task section) →
then the **v2 feature wave** Deuce surfaced. Wrote the full spec **`FEATURES_V2.md`** (`glow` it). **Completed F1 —
the whole breeding-meta augment batch:** **Nature** (`6a7df50`) · **Ball** (`5425318`) · **Hidden Ability**
(`950c8ad`) · **Egg Moves** (`4a06bac`). Each writes the trait onto the egg `PokemonProperties` pre-encrypt (same
seam as IV/EV), fail-safe + non-destructive; all 4 Cobblemon APIs verified against the jar. Also did the `EggShape`
params refactor. QA rows Q31–Q34.

### ▶️ ACTIVE — v2 feature wave (Deuce: "do all of it + keep adding to QA"; spec = `FEATURES_V2.md`)
Build order **F1 breeding-meta ✅ → F2 Notifications ✅ → F3 Goal tracker (cores ✅, WIRING NEXT)**, then re-sync
before wave-2 (hopper interop, dashboards #6, economy, guide). **F2 shipped** shiny + Data-milestone pings
(`notify/`; QA Q35–Q36). **F3 — Deuce chose TRACK-ONLY v1** (non-destructive, no auto-cull): pure cores done +
tested (`goal/BreedingGoal` + `GoalProgress`, `c3a385a`). **Next = the MC wiring** (a focused increment, all
integration points verified — see `FEATURES_V2.md` F3): the mod's **first `/gp goal` command** (no command system
yet → `CommandRegistrationCallback`), an in-memory `GoalStore` (per-UUID goal+progress), egg observation attributed
to `PastureData.owner` (prefer: add species/ivTotal/perfectIvs to `BredEgg` + enrich `egg_laid`, then a `GoalTracker`
observes the stream like `Notifier`), and a "goal reached" ping reusing `notify/`. Auto-cull stays the deferred
follow-on. **All GUIs + the breeding-meta install UX remain deferred to the Compiler-UI wave.**

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
