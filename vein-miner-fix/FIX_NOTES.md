# ore-vein-miner — Fortune fix

**Mod:** Ore Vein Miner (`mr_ore_veinminer` v1.2) — a datapack-in-a-jar (namespace `svm`).
**Symptom:** vein mining works, but Fortune gives no bonus drops.
**Date:** 2026-06-23

## Root cause
`data/svm/function/start_mining.mcfunction` recorded the held tool's enchant into the score `svm.silk` (1/2/3 = Fortune level, 10 = Silk Touch). The three Fortune lines ran **`run return fail`** instead of setting the level, so `svm.silk` was **never** set to 1–3 anywhere in the datapack. The bonus-drop branch in `execute_mine.mcfunction` only fires when `svm.silk` is 1–3 → it never ran. (The `return fail` also aborted vein-mining outright when holding a Fortune pickaxe.)

## The change (one file only)
`start_mining.mcfunction`:
- Moved `scoreboard players set @s svm.silk 0` **above** the enchant checks (so it doesn't clobber the level).
- Changed the 3 Fortune lines from `run return fail` → `run scoreboard players set @s svm.silk 1|2|3`, mirroring the (working) Silk Touch line.

**Nothing else touched** — same NBT enchant format, same `pack_format`, same everything. Only `start_mining.mcfunction` differs from the original jar; all other entries are byte-identical.

## Deploy
Remove the original `ore-vein-miner-1.2.jar` and drop in `ore-vein-miner-1.2-fortunefix.jar` (same mod id, so don't run both). Full client/server restart (Fabric loads jars at launch).

## Test
Crouch + mine an ore vein with: a plain pick (single drops), a Fortune III pick (multiplied drops), a Silk Touch pick (block itself). Watch drop counts look sane and no ore blocks are left behind.
