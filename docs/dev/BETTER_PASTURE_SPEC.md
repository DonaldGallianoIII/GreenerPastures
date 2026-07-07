# Better Pasture — multi-pair breeding spec

_Build spec, 2026-06-23. Goal (Deuce): a pasture where you define breeding **pairs** (color-flag / boolean group, 2 mons per pair), **each pair lays its own egg** every cycle, and the egg storage scales to **5 × (number of pairs)**. Cuts pasture count drastically. Lives in (or beside) **PastureKeeper**. **Heaviest + most fragile mod in the suite — deep Cobbreeding surgery, hard-coupled to Cobbreeding 2.2.1.**_

## Cobbreeding internals (decompiled, the foundation)
- `ludichat.cobbreeding.PastureBreedingData = { long time, net.minecraft.class_2371<ItemStack> eggs (DefaultedList), int requiredTicks }`. The egg list length = config `pastureInventorySize` (fixed).
- Breeding lives in Cobbreeding's mixin `ludichat.cobbreeding.mixin.PokemonPastureBlockEntityMixin`, `@Inject(at=HEAD, method="TICKER$lambda$0")`:
  - Runs every `Cobblemon.config.pastureBlockUpdateTicks`.
  - `pokemon = BreedingUtilities.getPokemon(tetheredPokemon)` → `possibleEggs = BreedingUtilities.getPossibleEggs(pokemon)` (a `Collection<Map.Entry<FormData, List<Pair<Pokemon,Pokemon>>>>` — ALL compatible pairs grouped by resulting form).
  - If `state[cobbreeding$BREEDING_ACTIVATED]` && possibleEggs non-empty && an egg slot is empty: catch-up loop — while `elapsed >= requiredTicks`, call `cobbreeding$addEgg(data, possibleEggs)` then re-roll `requiredTicks = random(min..maxBreedingTimeInTicks)`, stopping when egg slots fill.
  - `cobbreeding$addEgg`: `eggData = BreedingUtilities.chooseEgg(possibleEggs)` (**ONE** random egg) → builds the egg ItemStack → `cobbreeding$set(data.getEggs(), eggItem)` into an empty slot.
  - State props it sets: `cobbreeding$BREEDING_ACTIVATED`, `cobbreeding$HAS_EGG`.
- Egg collection out: Cobbreeding config `allowHoppersToPullFromPastureBlock` → hoppers can pull eggs.

## Required changes
1. **Per-pair emission.** Replace the one-random-egg-per-cycle with **one egg per defined pair per cycle**. Each cycle, for each defined pair P, create P's egg (reuse `BreedingUtilities` egg-build: calcStats/calcNature/calcEggMoves/calcAbility/calcBall/calcShiny/calcFeatures on the pair) and place it in a slot.
2. **Dynamic egg storage = 5 × pairs.** The `eggs` DefaultedList must be sized `5 * pairCount` instead of `pastureInventorySize`. Needs intercepting where `PastureBreedingData.eggs` is created/resized.
3. **Pair definition.** How the user marks pairs:
   - **v1 (recommended, NO custom GUI): slot-adjacency** — pair `tetheredPokemon[2k]` with `[2k+1]`. User arranges species+Ditto adjacent in the existing pasture GUI. Zero UI work.
   - **v2 (the "color flag" UX): per-mon group flag** — store a group id/color on each Tethering; 2 mons sharing a group = a pair. Needs: per-mon flag storage (NBT on Tethering or a side map) + a GUI to assign it (mixin Cobblemon's pasture screen — the heavy/risky part) + networking.

## Implementation approaches (decide at build time)
- **(A) Own ticker hook (preferred if feasible):** `@Inject(at=TAIL, method="TICKER$lambda$0", remap=false)` in OUR mixin; read Cobbreeding's `PastureBreedingData` for this pasture and emit the extra per-pair eggs ourselves. **Blocker to resolve first:** how Cobbreeding stores `PastureBreedingData` per-pasture (a `@Unique` mixin field on the BE? a static map keyed by BlockPos?) — decompile `cobbreeding$...` fields/`loadAdditional`/`saveAdditional` to find the accessor. If it's private/@Unique, may need an accessor mixin or reflection.
- **(B) Reimplement breeding, disable Cobbreeding's:** cancel Cobbreeding's emission and run our own loop. Most control, most duplication, most fragile.
- **(C) Redirect `chooseEgg`/`addEgg`:** likely insufficient (addEgg is called once per cycle; can't multiply output without touching the loop).

## Risks / caveats
- **Hard-coupled to Cobbreeding 2.2.1** — guard against version drift; re-verify on Cobbreeding updates.
- Egg-inventory resize interacts with Cobbreeding's NBT save/load (`saveAdditional`/`loadAdditional` mixins) and its pasture GUI egg display — must stay consistent or eggs/GUI break.
- The color-flag GUI (v2) is a custom Cobblemon pasture-screen mixin — the riskiest piece; do v1 (slot-adjacency) first and validate the engine before the UI.

## Recommended build order
1. **First, free:** dev lowers `minBreedingTimeInTicks`/`maxBreedingTimeInTicks` (floor 20t) — fewer/faster pastures, zero code. May make this mod unnecessary.
2. Resolve approach-(A) blocker (how Cobbreeding stores `PastureBreedingData`).
3. Build the **per-pair engine with slot-adjacency pairing + 5×pairs storage** (v1). Test: N pairs → N eggs/cycle, storage holds them.
4. Add the **color-flag GUI** (v2) only if slot-adjacency control isn't enough.
