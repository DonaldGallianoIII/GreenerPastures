# 🎁 Cobblemon Drops — Summary (tuning at a glance)

_From `all-species-drops.json` (Cobblemon 1.7.3+1.21.1). **1025 species** · **929 with drops** · **230 distinct items** · 2150 total drop entries. `glow DROPS_SUMMARY.md`._

## Item namespaces
- **`cobblemon:`** — 127 distinct items
- **`minecraft:`** — 103 distinct items

## How rare are drop entries? (chance buckets, by entry count)

| Chance | # entries |
|---|---|
| 100% guaranteed | 697 |
| 50–99% | 2 |
| 10–49% | 259 |
| 5–9% | 701 |
| 1–4% | 491 |

_Most entries are low-% (the 'rare materials'); a handful are guaranteed (the common mob-style drops)._

## `amount` (how many entries roll per drop event)

| amount | # species |
|---|---|
| 1 | 268 |
| 2 | 260 |
| 3 | 226 |
| 4 | 133 |
| 5 | 83 |
| 6 | 37 |
| 7 | 8 |
| 8 | 7 |
| 9 | 1 |
| 48 | 2 |

> NOTE: our Harvester currently rolls **every** entry by its own chance (ignores `amount`). If we want to match vanilla feel, cap drops at `amount` per harvest — a tuning knob.

## Top 30 most-common drop items (by # of species)

| Item | # species | chance | qty |
|---|---|---|---|
| `minecraft:feather` | 72 | 100% (guaranteed) | 0-1, 0-2, 0-3, 2-4 |
| `minecraft:chicken` | 67 | 100% (guaranteed) | 1 |
| `cobblemon:mystic_water` | 40 | 2.5%–100% | 1 |
| `cobblemon:miracle_seed` | 40 | 2.5%–100% | 1 |
| `cobblemon:pecha_berry` | 37 | 2.5%–10% | 1 |
| `cobblemon:charcoal_stick` | 36 | 2.5%–100% | 1 |
| `minecraft:bone` | 33 | 100% (guaranteed) | 0-1, 0-2, 0-3, 1, 1-2, 1-3 |
| `cobblemon:cheri_berry` | 31 | 2.5%–10% | 1 |
| `cobblemon:rawst_berry` | 31 | 2.5%–10% | 1 |
| `cobblemon:poison_barb` | 31 | 2.5%–10% | 1 |
| `cobblemon:persim_berry` | 31 | 2.5%–10% | 1 |
| `cobblemon:chesto_berry` | 31 | 2.5%–10% | 1 |
| `cobblemon:yache_berry` | 29 | 2.5%–10% | 1 |
| `cobblemon:aspear_berry` | 28 | 2.5%–10% | 1 |
| `minecraft:string` | 27 | 100% (guaranteed) | 0-1, 0-2, 0-3 |
| `cobblemon:oran_berry` | 27 | 2.5%–100% | 0-1, 0-2, 1 |
| `minecraft:rotten_flesh` | 26 | 100% (guaranteed) | 0-1, 0-2 |
| `cobblemon:kasib_berry` | 24 | 2.5%–10% | 1 |
| `cobblemon:passho_berry` | 24 | 2.5%–10% | 1 |
| `minecraft:bone_meal` | 23 | 5%–10% | 1 |
| `cobblemon:never_melt_ice` | 22 | 2.5%–100% | 1 |
| `minecraft:cod` | 22 | 100% (guaranteed) | 0-1, 0-2, 0-3, 1, 1-3 |
| `cobblemon:kebia_berry` | 22 | 2.5%–10% | 1 |
| `minecraft:phantom_membrane` | 22 | 2.5%–100% | 0-3, 1, 2-4, 3-5 |
| `minecraft:clay_ball` | 21 | 100% (guaranteed) | 0-1, 0-2, 0-3 |
| `minecraft:blaze_powder` | 21 | 2.5%–100% | 0-1, 0-2, 0-3, 1, 2-4 |
| `cobblemon:sharp_beak` | 21 | 2.5%–5% | 1 |
| `minecraft:ender_pearl` | 20 | 100% (guaranteed) | 0-1, 0-2, 0-3, 1 |
| `cobblemon:hard_stone` | 20 | 2.5%–100% | 1 |
| `cobblemon:wepear_berry` | 20 | 2.5%–10% | 1 |

## Rarest 30 items (fewest species — the 'specials')

| Item | # species | chance | from |
|---|---|---|---|
| `cobblemon:auspicious_armor` | 1 | 5% | Armarouge |
| `cobblemon:berry_juice` | 1 | 100% (guaranteed) | Shuckle |
| `cobblemon:dubious_disc` | 1 | 25% | Porygon-Z |
| `cobblemon:electric_seed` | 1 | 5% | Togedemaru |
| `cobblemon:eviolite` | 1 | 5% | Eevee |
| `cobblemon:flame_orb` | 1 | 5% | Heatmor |
| `cobblemon:ice_stone` | 1 | 25% | Glaceon |
| `cobblemon:life_orb` | 1 | 5% | Absol |
| `cobblemon:malicious_armor` | 1 | 5% | Ceruledge |
| `cobblemon:metal_powder` | 1 | 5% | Ditto |
| `cobblemon:misty_seed` | 1 | 5% | Comfey |
| `cobblemon:quick_powder` | 1 | 100% (guaranteed) | Ditto |
| `cobblemon:room_service` | 1 | 5% | Indeedee |
| `cobblemon:upgrade` | 1 | 25% | Porygon2 |
| `minecraft:acacia_log` | 1 | 100% (guaranteed) | Komala |
| `minecraft:basalt` | 1 | 100% (guaranteed) | Magcargo |
| `minecraft:blue_ice` | 1 | 100% (guaranteed) | Eiscue |
| `minecraft:cooked_chicken` | 1 | 100% (guaranteed) | Moltres |
| `minecraft:diamond_sword` | 1 | 100% (guaranteed) | Zacian |
| `minecraft:eye_of_ender` | 1 | 100% (guaranteed) | Deoxys |
| `minecraft:golden_helmet` | 1 | 5% | Falinks |
| `minecraft:iron_ingot` | 1 | 100% (guaranteed) | Duraludon |
| `minecraft:name_tag` | 1 | 5% | Klefki |
| `minecraft:nether_star` | 1 | 100% (guaranteed) | Groudon |
| `minecraft:oak_sapling` | 1 | 100% (guaranteed) | Turtwig |
| `minecraft:stone_axe` | 1 | 100% (guaranteed) | Kleavor |
| `minecraft:verdant_froglight` | 1 | 100% (guaranteed) | Politoed |
| `cobblemon:ability_shield` | 2 | 10%–100% | Aegislash, Zamazenta |
| `cobblemon:cleanse_tag` | 2 | 2.5%–5% | Chimecho, Chingling |
| `cobblemon:medicinal_leek` | 2 | 100% (guaranteed) | Farfetch’d, Sirfetch’d |
