# Egg Plumbing / Egg-Collector v2 — Design & Feasibility

Source of the idea: in-game chat with **TropicanaCookies** (screenshot 599). They're building a
"Swamp Platform" breeding setup and want EggOracle to be a *suite* ("comes with some plumbing
and new blocks"), not just the calculator. The asks:

- A **filter block** — "pull eggs given a filter," route them.
- **Auto-cull** — toss non-shinies, poor-IV eggs, etc., automatically.
- **Full automation** — keepers one way, junk to disposal; hopper/pipe-style "plumbing."

This is a smarter evolution of the existing `shiny-egg-collector` (which only vacuums shinies):
add *configurable criteria* (IVs, nature, species…), *routing* (keep vs trash), and a filter UI.

---

## The two feasibility questions — both now ANSWERED

### Q1. Can we read an egg's IVs / shiny / quality off the item?  ✅ YES — fully.

Decompiled `Cobbreeding-fabric-2.2.1.jar` (package `ludichat.cobbreeding`, Kotlin). Findings:

**The egg stores the hatchling as data components** (`components/CobbreedingComponents`):
`NAME`, `TIMER`, `SECOND`, `EGG_INFO`, **`POKEMON_PROPERTIES`** (the important one), `VERSION`.
`POKEMON_PROPERTIES` is a (optionally encrypted) string holding the hatchling's full
`PokemonProperties`.

**IVs are determined at BREED time and baked into the egg** — not rolled at hatch.
`BreedingUtilities.chooseEgg()` builds the `PokemonProperties` and calls:
- `setIvs( calcStats(parents) )` — `calcStats` = `IVs.createRandomIVs()` + parent IV inheritance
  (`IVs.get/set`) + **Destiny Knot** (`CobblemonItems.DESTINY_KNOT`) + **Power items**
  (`powerItemToIV`). Real Gen-mechanics inheritance.
- `setShiny( calcShiny(parents) )`, `setNature(...)`, `setAbility(...)`, `setForm(...)`,
  `setMoves(...)`, `setPokeball(...)`.

So every egg already "knows" its hatchling's exact IVs, shininess, nature, ability, etc.

**Public read API (no NBT parsing, decryption handled for us):**
```java
// ludichat.cobbreeding.EggUtilities
PokemonProperties props = EggUtilities.extractProperties(stack);   // null/empty if not an egg
// com.cobblemon.mod.common.api.pokemon.PokemonProperties exposes:
IVs     ivs    = props.getIvs();        // per-stat: ivs.get(Stats.HP) ... SPEED  (0..31)
Boolean shiny  = props.getShiny();
String  nature = props.getNature();
Gender  gender = props.getGender();
String  ability= props.getAbility();
String  species= props.getSpecies();
String  form   = props.getForm();
// also: evs, moves, pokeball, originalTrainer/-Type, minPerfectIVs, aspects, heldItem...
```
`extractProperties` calls `decrypt` internally, so **encryption is a non-issue** as long as
Cobbreeding is loaded (it always is on this instance). Shedmon also runs `eggEncryptionEnabled=false`.

> To call this API from our mod we either (a) add Cobbreeding+Cobblemon as compile deps, or
> (b) reflect into `EggUtilities.extractProperties` / `PokemonProperties.getIvs()` (no build deps).
> For a quick probe, reflection is easiest; for the real mod, proper deps are cleaner.

### Q2. Where can this run — Shedmon or only a world I admin?  ↔ It's a fork.

- **Reading egg data is client-side-capable.** When you open a container, the server syncs the
  egg ItemStacks (with components) to your client, so a **client-side** mod can read every egg's
  IVs/shiny/nature in any open inventory. → **Works on Shedmon, no server mod needed.**
- **A physical auto-sorting *block*** (pulls from storage, routes, disposes on its own) needs
  **server-side** code (block entity tick + inventory transfer). → Only on a world you admin, or
  if Shedmon installs the mod server-side. The old `shiny-egg-collector` is server-side for this
  reason and thus singleplayer/admin-only.
- **Disposal caveat (client-side path):** a client mod can *identify* junk eggs freely, but to
  actually *toss* them on a server it must use normal player actions (slot-clicks / drop / move to
  a trash chest) — i.e., automating clicks. Reading is unambiguously fine; auto-tossing edges into
  "automation," which some servers' rules may restrict. **Confirm Shedmon's stance before shipping
  auto-disposal.** (Highlight-for-manual-toss is always safe.)

---

## Architecture options

**A. Client-side "Egg Culler" (Shedmon-compatible) — recommended first.**
Extend the highlighter concept: in any open container, read each egg via `extractProperties`,
score it against a filter, and **color-code** (shiny=gold, keeper=light blue, cull=red) + show a
quality count in the title. Optional one-keypress "drop all flagged" (pending Shedmon policy).
Pros: runs on Shedmon today, low risk, immediately useful for sorting real pastures.
Cons: not a physical "block"; disposal is assisted, not autonomous.

**B. Server-side "Egg Filter Block" (admin/own-world) — the full vision.**
A block with a filter config GUI that pulls eggs from adjacent storage (Transfer API), keeps
matches, routes rejects to a trash/output side. True automation, hopper-chainable ("plumbing").
Pros: fully automatic, matches Tropicana's mental model. Cons: server-side → not on Shedmon
unless admins install it.

**C. Hybrid.** Ship A now for Shedmon use; build B for worlds you admin / as a server offering.
Shared core: the `extractProperties → filter` logic is identical; only the "act on it" layer differs.

---

## Filter criteria we can support (all from `PokemonProperties`)
- **Shiny** (yes/no) — the headline filter.
- **IVs:** total ≥ N, per-stat thresholds (e.g. `SPE ≥ 30`), "≥ K perfect (31) IVs", flawless (6×31).
- **Nature**, **Ability** (incl. hidden via aspects), **Gender**, **Species/Form**.
- **Egg moves** present, **Pokéball**, **OT / OT-type** (Masuda detection).

## Status / open items
- [x] Q1 IV+shiny readability — **confirmed via decompile**.
- [x] Q2 client-vs-server reality — **mapped** (fork above).
- [x] **Fork chosen: A** (client-side culler), folded into the EggOracle mod.
- [x] **Culler v1 built** — `com.eggoracle.cull.*` (+ `mixin/HandledScreenAccessor`), reads via
      reflection into `EggUtilities.extractProperties`. Tints gold/blue/red + IV totals + a summary
      line; toggle with **C** in a container. Build green; jar staged on Desktop.
- [ ] **Live confirmation:** deploy & open a bank on Shedmon — verify the log shows
      "Cobbreeding egg API found" and IV totals look right. (The culler IS the empirical probe.)
- [ ] Tune default thresholds (keepMinIvTotal=120, keepMinPerfect=3) after seeing real eggs;
      add per-stat filters + a config UI tab in the EggOracle screen.
- [ ] Shedmon policy on client-side auto-*disposal* (the future "toss all flagged" action).
- [ ] Phase 2: server-side filter *block* for worlds you admin (reuses the same `classify()` core).

## Reference
- Decompiled source extracted to `research/cobbreeding/` (gitignore-worthy; it's their jar).
- Key classes: `EggUtilities` (read API), `BreedingUtilities` (breed/IV mechanic),
  `components/CobbreedingComponents` (component types), `PokemonEgg` (the item).
