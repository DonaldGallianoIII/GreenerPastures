# Cobblemon Drops — Reference (for modifying drops)

_Extracted from Cobblemon 1.7.3+1.21.1 on 2026-06-25 for the Greener Pastures dark-economy design._

## Where the drop DATA lives (the source of truth)
- **Cobblemon jar:** `/home/donaldgalliano/pokemon-prediction/greener-pastures/.gradle/loom-cache/remapped_mods/net_fabricmc_yarn_1_21_1_1_21_1_build_3_v2/unspecified/Cobblemon-fabric-1.7.3+1.21.1/f7c25955176b/Cobblemon-fabric-1.7.3+1.21.1-f7c25955176b.jar`
- **In-jar path:** `data/cobblemon/species/<generation1..9>/<pokemon>.json` — **1025 species, 929 with non-empty drops.** Each species JSON has a top-level `"drops"` block.

## Files in this folder
- **`all-species-drops.json`** — every species `name → drops` block, all in one file (the whole landscape to plan against).
- **`species-samples/*.json`** — 10 full species files (the real on-disk format Cobblemon ships).
- **`distinct-drop-items.txt`** — the 230 distinct item ids used across all vanilla-Cobblemon drop tables.

## The `drops` JSON format
```json
"drops": {
  "amount": 3,                                                  // # of entries that roll per drop event
  "entries": [
    { "item": "minecraft:feather", "quantityRange": "0-1" },   // qty range
    { "item": "minecraft:chicken" },                            // default qty 1, 100% (no percentage given)
    { "item": "cobblemon:razz_berry", "percentage": 2.5 }       // 2.5% chance
  ]
}
```
Entry fields (from `ItemDropEntry`): `item` (Identifier) · `quantity` (int, default 1) **or** `quantityRange` ("min-max") · `percentage` (float, default 100) · `dropMethod` (enum). Other entry types exist: `CommandDropEntry` (runs a command), `EvolutionItemDropEntry`.

## Two ways to MODIFY drops
1. **Datapack override (NO code)** — drop a species JSON at the same path in a datapack/our resources: `data/cobblemon/species/<gen>/<pokemon>.json` with your own `drops`. Cobblemon loads species data from datapacks, so this overrides the table. Best for "retune what mons drop." For our mod, ship them under `greener-pastures/src/main/resources/data/cobblemon/species/...`.
2. **Code (runtime) — for the dark economy "render"** — roll drops headlessly (no entity, no world item-spawns):
   - `Species.getDrops()` → `com.cobblemon.mod.common.api.drop.DropTable`
   - `DropTable.getDrops(kotlin.ranges.IntRange amount, com.cobblemon.mod.common.pokemon.Pokemon p)` → `List<DropEntry>` — **rolls the table** (use `DropTable.getAmount()` for the range). ⚠️ The other method, `DropTable.drop(LivingEntity, ServerWorld, Vec3d, ...)`, SPAWNS world item-entities — don't use it (those despawn / are lost when away).
   - For each `ItemDropEntry`: `getItem()` (Identifier) + `getQuantity()`/`getQuantityRange()` + `getPercentage()` → resolve `Registries.ITEM.get(id)` → `ItemStack` → store in `PastureData` → withdraw via the Notebook.
   - API package: `com.cobblemon.mod.common.api.drop.{DropTable, DropEntry, ItemDropEntry, CommandDropEntry, EvolutionItemDropEntry, ItemDropMethod}` — all in the jar above.

## Regenerate / pull more from the jar
```python
import zipfile, json
JAR = ".../Cobblemon-fabric-1.7.3+1.21.1-f7c25955176b.jar"   # path above
z = zipfile.ZipFile(JAR)
for n in z.namelist():
    if "/species/" in n and n.endswith(".json"):
        d = json.loads(z.read(n)); print(d.get("name"), d.get("drops"))
```
