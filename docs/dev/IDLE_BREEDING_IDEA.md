# 💡 Idea — Breeding as an Incremental / Idle Game

_Pegged 2026-06-24 for later (Deuce). Greener Pastures' breeding becomes a self-reinforcing progression loop: eggs are currency, you compress them into upgrades that make breeding better, repeat. `glow IDLE_BREEDING_IDEA.md`._

## The seed (Deuce's idea, verbatim-ish)
Make breeding its own mini, gamified, **incremental/idle** thing:
- **Eggs + shiny eggs are a crafting material / currency** — e.g. "compressed shiny Charmander" lol.
- Breed **9 shiny eggs → compress into a block.**
- That block → an **upgrade**: e.g. *all Fire-types in that pasture now have 5× shiny chance.*
- An **incremental idle-game component** layered on the breeding. (Numbers TBD — "idk yet.")

## Why it's a strong direction
- Turns breeding from a static feature into a **progression system** with goals + a dopamine loop → real long-term retention.
- **Nobody else does "breeding as an idle game"** in Cobblemon → a true differentiator.
- Synergizes perfectly with the **"A Data Science Mod"** brand: the analytics dashboards literally *become* the idle-game stats/curves screen (eggs/hr, shiny rate climbing, total compressed).
- Reuses what we already built: the **wand upgrade slots** are the natural delivery mechanism, the **tiers** scale it, the **analytics core** measures it.

## The core loop (the incremental engine)
> breed eggs → **compress** (9→1) → **craft an upgrade** → breed faster / shinier → more eggs → bigger compressions → …

The classic exponential idle curve. The "idle" is MC-native: pastures auto-breed in loaded chunks; you set up + reinvest, leave, come back to a stockpile to compress. (True offline simulation is harder — loaded-chunk breeding is the v1 "idle".)

## Mechanics (riffing — all tunable)
1. **Eggs as material.** Regular + shiny eggs are inputs; shiny eggs = the premium currency.
2. **Compression (the prestige-y crunch).** 9 shiny eggs of a species → **Compressed Shiny &lt;Species&gt;** block. Layered: 9 blocks → a super-block, etc. (tiered "trophies").
3. **Compressed blocks → upgrades.** Slot a compressed block (or craft it into a slottable upgrade) into the **wand's functional slots**: *Compressed Shiny Charmander → +shiny for Fire-types in that pasture.* Generalizes to type bonuses (Fire/Water/…), species bonuses, yield, speed.
4. **Specialization.** Each pasture becomes a tuned "shiny factory" for a type/species via its slotted compressed-egg upgrades → encourages many specialized pastures (which Better Pasture's consolidation + wand pairing already support).
5. **Prestige (late, optional).** A reset that trades progress for a permanent global multiplier — the classic idle hook. Maybe a "Greener" prestige currency.

## How it plugs into what already exists
- **Wand upgrade slots** = where compressed-egg upgrades go — a new functional-upgrade category beside Shiny/Speed/Yield. The slot system is already built.
- **Pasture Upgrade tiers** scale pairs + slots; compressed upgrades scale shiny/type bonuses → two orthogonal progression axes.
- **Analytics** = the idle dashboard. Shiny-rate-over-time, eggs/hr, total compressed, per-type stats. The data-science core *is* the idle stats screen.
- **CobbreedingBridge** already builds eggs; a shiny multiplier per pasture/type (from slotted upgrades) would hook the egg-gen (a `calcShiny` multiplier).

## Open questions (to think on)
- **Shiny multiplier balance:** does 5× stack? cap? Shiny inflation is a real concern for curated packs (Cobbleverse) → needs config + caps. Spiciest question.
- **Granularity:** type-wide (Fire) vs species (Charmander) bonuses — or both at different tiers?
- **Cost curve:** 9→1 ratio; blocks per upgrade; growth rate (too grindy vs too fast).
- **Idle depth:** loaded-chunk auto-breed (v1) vs offline simulation (v2).
- **Always-on vs a mode?** Probably always-on but config-gateable.
- **Does shiny-boosting clash with the "respectable/curated" goal?** → config + reasonable defaults (consistent with the public-mod mandate).

## Rough first slice (when we build it)
1. Compression recipe: 9 shiny &lt;species&gt; eggs → Compressed Shiny &lt;species&gt; block.
2. One compressed upgrade type (e.g. Fire shiny boost) slottable in the wand → CobbreedingBridge applies a shiny multiplier for that type in that pasture.
3. Analytics events for compress + boosted-shiny → dashboard shows the curve.
4. Config: multiplier value + cap.
5. Then layer: compression tiers, more types, prestige.

## ⚙️ Engine facts that steer upgrade design (verified in Cobbreeding 2.2.1 source — `BreedingUtilities`)

### Held items are ALREADY respected — do NOT reimplement
Our multi-pair breeder delegates egg-gen to Cobbreeding's own `chooseEgg` (the exact method its native pasture ticker calls). Inside `chooseEgg` (BreedingUtilities:272–276) it runs `calcStats`/`calcNature`/`calcShiny`/`calcEggMoves`/`calcAbility` on the **tethered parents' held items**, so we get for free:
- **Destiny Knot** (4 inherited IVs vs 2), all **6 Power items** (→ their stat IV), **Everstone** (nature), **Mirror Herb** (egg moves), **Light Ball**, **Masuda** shiny.
Driven by items **held by the parent Pokémon in the pasture**, not by anything in our wand. ⇒ the wand's functional/compressed-egg slots are for the **NEW** layer only (pasture-wide type/species shiny+yield) — never re-implement canon held items.
- ⚠️ **STILL TO CONFIRM IN-GAME (Deuce):** that a Destiny-Knot'd parent yields a 4-IV egg *through our multi-pair path* (should be byte-identical).

### Shiny stacking MUST be additive, not multiplicative (Deuce's call)
Cobbreeding `calcShiny` (BreedingUtilities:849): base denominator = `Cobblemon.config.shinyRate` (e.g. 8192), then **divided** by server-configured multipliers in `Cobbreeding.config.shinyMethod` (a map) — `always`, `crystal` (per shiny parent), `masuda` (different OT). Final chance = 1/(reduced denominator). Server boosts already stack **multiplicatively**, so a boosted server can sit at a very high rate before we touch anything.
→ **Our shiny upgrade must not multiply on top.** Design: **post-process the egg in our `assembleEgg`** — let Cobbreeding compute shiny normally (fully respects server config); if the egg is **not** already shiny, do an **independent bonus roll** at `bonusRolls / baseShinyRate` (Shiny-Charm-style extra rolls, anchored to the **base** rate, ignoring server multipliers) and set shiny if it hits.
- This is a **union of independent events** → mathematically bounded ≤ 1, can **never explode**. On a hyper-boosted server our bonus is negligible (safe); on a vanilla server it's meaningful.
- **Piggyback:** read `Cobblemon.config.shinyRate` for the base; we inherently respect `shinyMethod` by only boosting eggs Cobbreeding left non-shiny.
- **UI wording:** describe upgrades as **"+N shiny rolls"** / "+base-rate", NOT "×N" (avoid the multiplicative expectation).
- Add a **config cap** (max bonus rolls / max added probability) for Cobbleverse safety.

## 🎰 CHOSEN shiny mechanic — proc-based reroll (bounded, scale-safe)
> **✅ IMPLEMENTED & DEPLOYED 2026-06-25 (Task #14 slice A).** Lives in `CobbreedingBridge.maybeProcShiny` + `effectiveShinyOdds`; the proc value is an **augment** (`greenerpastures:augments` data component, field `shiny` = proc %) carried by the slotted upgrade and read via `PastureData.shinyProcChance()` — **separate from the tier** (tier = pairs+slots; shiny is a paid augment). Reroll uses the **effective** rate; `nextDouble()` so fractional procs are exact. Until the Compiler GUI ships (slice B), author by hand: `/give @s greenerpastures:breeding_upgrade_copper[greenerpastures:augments={shiny:40}]`. **NOT yet confirmed in-game** (built while Deuce was away). The grid below stays the spec the Compiler will map tier→% from.

Deuce's design, supersedes the flat "+rolls" sketch above (it's the same union-of-independent-events principle, but framed as a **proc** so it's satisfying *and* hard-capped).

Per egg, **after** Cobbreeding's own shiny check:
```
if (!egg.shiny && rng < procChance)  →  +1 reroll at the EFFECTIVE server rate
```
- **Strength = ×(1 + procChance)** on the shiny rate, per egg. Because pastures are independent and each egg sees only its own pasture's upgrade, the **aggregate across 100+ pastures is also just ×(1+proc)** — never ×(1+proc)ᴺ. It is mathematically incapable of exploding. A maxed mega-farm makes at most `proc%` more shinies than an un-upgraded one.
- **Reroll uses the EFFECTIVE rate** (honors server `always`/`crystal`/`masuda`) so it rewards boosted-server grinders, still capped at ×(1+proc). (Costs ~15 lines replicating `calcShiny` from public config, version-guarded.)
- **Guardrail:** one shiny upgrade per pasture; tiers **replace** in place (no slot-stacking), so proc stays ≤ the tier cap.
- Implementation: post-process `eggData.shiny` in `CobbreedingBridge.assembleEgg` before encrypt; reroll prob via `nextDouble()` (no int-cast → fractional procs exact). Cobbreeding's own `(int)shinyOdds` floor is untouched (only affects its portion).

### Tier grid — 10% steps (calibrated: diamond ≈ 0.4× a ×2 Masuda)
| Tier | Surround (8× blocks) | Proc | Shiny ×mult | vs ×2 Masuda | vs ×6 Masuda | per 100k eggs (vanilla, base 12.2) |
|---|---|---|---|---|---|---|
| Copper | copper | 10% | ×1.10 | 0.10× | 0.02× | 13.4 |
| Iron | iron | 20% | ×1.20 | 0.20× | 0.04× | 14.6 |
| Gold | gold | 30% | ×1.30 | 0.30× | 0.06× | 15.9 |
| Diamond | diamond | 40% | ×1.40 | **0.40×** | 0.08× | 17.1 |
| Netherite | netherite | 50% | ×1.50 | 0.50× | 0.10× | 18.3 |
| Emerald | emerald | 60% | ×1.60 | 0.60× | 0.12× | 19.5 |
| Custom | compressed-shiny-egg blocks | 80–100% | ×1.8–2.0 | 0.8–1.0× | 0.16–0.20× | 22–24 |

- "0.4× Masuda" holds against a **×2 Masuda** (Deuce's server-style value); against canonical **×6 Masuda** it's ~0.08×. Tune `proc%` per tier to taste — the grid is linear in proc.
- **Not useless for shiny hunting** (vs PokeSnacks / Starf berry / EGA loading etc.): those are the *wild/hunt* vector; this is the **breeding** vector — a bounded ×1.1–×2 on a mass-breeding farm is a real, stacking-but-capped payoff.

## 🔨 CHOSEN upgrade crafting — surround-craft (pure vanilla shaped recipes)
A **blank slot upgrade** always sits in the **center** of the 3×3; the **8 surrounding blocks** decide what it becomes (first craft = its function) or upgrades into (each tier-up).
- Tier ladder: **copper → iron → gold → diamond → netherite → emerald → [custom]**, custom tiers surrounded by **compressed-shiny-egg blocks** (this is how the crafting ladder plugs into the incremental loop — vanilla metals gate early tiers, bred eggs gate the endgame).
- Each transition is **one vanilla shaped recipe** (`["YYY","YXY","YYY"]`, Y = tier block, X = current upgrade) → **no custom recipe code, fully datapack-overridable.** Cost is steep by design (8 blocks = 72 ingots/gems per step).
- v1: a **separate item per (function × tier)** (matches the existing per-tier `BreedingUpgradeItem` pattern); refactor to a data-component item + small custom recipe only if the function×tier matrix balloons.
- Base "blank slot upgrade" crafting recipe = **TBD (Deuce)**; the function-select surround ingredient (blank → shiny vs speed vs yield) = TBD.

### Open decisions (locked = ✔)
- Reroll rate: **effective ✔ (implemented 6/25)** — gives boosted grinders a target; falls back to base rate if config unreadable, +∞ (no bonus) if a server multiplier is 0.
- Proc cap / where the ladder tops out (custom tiers 80–100%?).
- Function-select surround ingredient (what makes blank → *shiny*).
- Separate functional-upgrade ladder (…emerald→custom) vs unifying with the pasture-upgrade ladder (copper→…→greener).

## Links
Builds on the unified mod (see memory `greener-pastures-project`) + the wand upgrade-slot system, the analytics core, and Better Pasture multi-pair. Cross-ref `MOD_IDEAS.md`.
