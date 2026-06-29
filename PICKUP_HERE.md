# ▶️ Pickup Here — Session Handoff

_**Greener Pastures** — public-release Cobblemon "A Data Science Mod", Fabric 1.21.1, Java 21, MIT. Logic-first
+ headless-tested. Read this first. `glow PICKUP_HERE.md`. Memory: `greener-pastures-project`,
`rituals-gacha-project`, `batch-qa-workflow`, `testing-and-logic-first`, `observability-first-logging`._

## ⚡ STATE — 2026-06-29 (end of a big drops/rituals/augments session)
**148 headless tests green** (`./gradlew test`). **All committed on `main`, NOTHING deployed** — QA is batched
(Deuce's call, [[batch-qa-workflow]]). Latest commit `3cf0cb2`. Tree clean. Deuce is on **remote-control (phone,
at the doctor's office)** → standing rule: **functionality-first, NO custom UI** (defer all GUIs).

**Built this session (9 commits, 116→148 tests):** Drop Yield augment · Harvester tether-amp drop rate/yield ·
Renderer tether-amp enrichment · **craftable augment items** (all 7 functions Compiler-installable) · **IV Floor +
EV** breeding effects (egg IVs/EVs shaped pre-encrypt, verified to survive Cobbreeding hatch) · **rituals + type-drops
gacha system** (engine → runtime → config → 3× rarity → drop-augment scaling → `sim_rituals.py`).

## ▶️ ACTIVE TASK — Daemon global "root" buffs  _(IN PROGRESS — 4 commits, 161 tests)_
**Deuce's two design calls (locked):** buff **tier = the held Daemon's Mk level** (I/II/III); Data drain =
**tier-scaled & summed** (`Σ tier × costPerSec`/sec).
**Built + committed so far (all QA-pending = Q23):**
- `buff/` pure cores (`5ba97e8`): `BuffId` catalog (11 ENCHANT / 2 EFFECT / 5 HOOK — the catalog IS the
  worker-not-fighter allow-list, no combat/binary enchant present), `BuffSetting`, `BuffConfig` (lazy-Gson,
  `config/greenerpastures/buffs.json`), `BuffResolver` (level→tier, per-buff cap, summed drain), `BuffSystem`.
- EFFECT adapter (`0b4684d`): `DaemonBuffs` server-tick loop → held+fed Daemon grants **Haste + Saturation**,
  bills Data/sec (fractional carry, broke→no buffs). `daemon_level` int component + creative sneak-RC cycles
  Mk I→II→III (QA affordance; survival upgrade recipe = publish-phase). `DaemonItem.levelOf()`.
- Magnet HOOK (`a796175`): item magnet (radius 4/6/8 by tier); resolver filter is now **per-`BuffId`**
  (`SUPPORTED` set) so categories ship piecemeal without over-billing. `lastPaid` cache drives per-tick hooks.
**NEXT (in order):** (1) **ENCHANT boost** — the marquee (Fortune VI etc.); fully researched + spec'd in
`ENCHANT_BOOST.md` (edit the held stack's enchant component +tier, tick-bracketed; verified vs 1.21.1). One
timing Q (does equipment-sync run inside the tick window?) is being confirmed by a bg agent before the mixin is
written. ⚠ highest dupe/desync risk → needs a careful live QA. (2) remaining hooks: **auto-smelt, vein-mine,
XP boost, potion-duration** (all mixin/event-hook, untestable headless → build as a QA-pending batch).
Add each ENCHANT/HOOK `BuffId` to `DaemonBuffs.SUPPORTED` as its adapter lands.

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
