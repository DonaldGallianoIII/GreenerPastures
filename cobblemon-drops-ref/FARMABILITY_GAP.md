# Farmability Gap Analysis

**Goal under test:** *every survival-obtainable vanilla Minecraft item should be farmable through Pokemon pastures* — either dropped directly by some Pokemon, or craftable (incl. smelting/blasting/smoking/campfire/stonecutting/smithing) from things that are.

**Minecraft version:** 1.21.1  
**Drop data:** `all-species-drops.json` (1025 species, 2150 drop entries)  
**Item universe source:** the merged MC jar at `minecraft-merged.jar`.

---

## Headline numbers

| Metric | Count |
|---|---:|
| **Total vanilla items** (the universe) | **1333** |
| Items dropped directly by some Pokemon | 102 |
| Items reachable via crafting closure (dropped + craftable) | 447 |
| &nbsp;&nbsp;&nbsp;of which: craftable-only (not dropped) | 345 |
| **Truly unfarmable** (can't be dropped *or* crafted from drops) | **886** |
| &nbsp;&nbsp;&nbsp;of those: creative / technical / admin (excluded from goal) | 111 |
| &nbsp;&nbsp;&nbsp;of those: **real farmable candidates (the actual TODO)** | **775** |

So: of 1333 vanilla items, only **447 (33%)** are currently reachable through pastures. **775** survival items have *no* farm path and should — that is the work this mod exists to close. Only **111** of the unreachable items are genuinely out of scope (creative/technical).

---

## Method

1. **Item universe.** The merged jar is *obfuscated* (classes are `a.class`, `aa.class`, ...), so `net/minecraft/item/Items.class` and `Blocks.class` could not be decompiled by name (CFR was available but there is no de-mapped jar in the loom cache; only `intermediary` mappings exist). Instead I used two **mapping-independent** asset sources whose filenames/keys are the real `minecraft:` ids:
   - `assets/minecraft/models/item/<id>.json` — every registered Item has exactly one item model (1754 files).
   - `assets/minecraft/lang/en_us.json` — `item.minecraft.<id>` / `block.minecraft.<id>` translation keys.

   The universe = **item-model ids that also have a lang entry** (`model ∩ lang`). Requiring a lang entry strips the ~421 render-only template models that are *not* items (`template_*`, `clock_00`..`clock_63`, `compass_NN`, `recovery_compass_NN`, `light_NN`, `*_pulling_N`, `*_trim` armor overlays, `generated`, `handheld`, `broken_elytra`, etc.). I deliberately did **not** add back the 26 lang-only ids that lack a model — every one was verified to be a stale translation key for a *renamed* item (`*_pottery_shard`→`*_pottery_sherd`, `scute`→`turtle_scute`, bare `sign`→`oak_sign`, `smithing_template`, `lodestone_compass`) and is referenced in **zero** recipes/tags/loot tables. Result: **1333** items — squarely in the expected ~1300-1400 range.

2. **Dropped set.** Every `entry.item` in `all-species-drops.json` starting with `minecraft:` → **103 distinct ids** (102 valid; `minecraft:eye_of_ender` is not a real 1.21.1 id — the registry id is `minecraft:ender_eye` — so it does not map into the universe).

3. **Recipe-closure fixpoint.** Parsed all 1291 `data/minecraft/recipe/*.json`. Starting from the dropped set, repeatedly add any item whose recipe's ingredients are *all* already obtainable; `#tag` ingredients resolve via `data/minecraft/tags/item/*` (recursively) and are satisfied if **any** member is obtainable. Iterated to a fixpoint (4 rounds). Valid craft edges: `crafting_shaped`, `crafting_shapeless`, `smelting`, `blasting`, `smoking`, `campfire_cooking`, `stonecutting`, `smithing_transform`.

   **Approximations (stated plainly):**
   - `smithing_trim` produces no *new* item id (only applies a trim) → not a craft edge.
   - `crafting_special_*` recipes are hardcoded with no JSON ingredients. Most only **recolor/clone an already-obtainable base** (`armordye`, `mapcloning`, `shulkerboxcoloring`, `bookcloning`, `bannerduplicate`, `repairitem`, `tippedarrow`, `suspiciousstew`, `firework_star[_fade]`, `shielddecoration`). The two that introduce an otherwise-unreachable item were hardcoded by their known ingredients: `firework_rocket` (paper + gunpowder) and `decorated_pot` (bricks). The rest were ignored (they cannot un-gate anything new).
   - Recipes are modeled item→item only; non-item inputs (water, dye-color block states, etc.) are not modeled.

---

## (A) Direct-drop gap

`universe − dropped` = **1231** vanilla items that **no Pokemon drops directly**. This is a weak metric (most of these are *meant* to be crafted), so the real answer is the closure result below. For reference, the **102** items that are dropped today:

<details><summary>The 102 directly-dropped items</summary>

`acacia_log`, `amethyst_shard`, `apple`, `armadillo_scute`, `bamboo`, `basalt`, `beef`, `blaze_powder`, `blue_ice`, `blue_wool`, `bone`, `bone_block`, `bone_meal`, `bread`, `brown_mushroom`, `brown_wool`, `cactus`, `cake`, `calcite`, `candle`, `carrot`, `charcoal`, `chicken`, `clay_ball`, `coal`, `cod`, `cooked_chicken`, `dark_oak_sapling`, `dead_bush`, `diamond`, `diamond_sword`, `dirt`, `dragon_breath`, `echo_shard`, `egg`, `emerald`, `ender_pearl`, `eye_of_ender`, `feather`, `flint`, `glow_ink_sac`, `gold_nugget`, `golden_helmet`, `gravel`, `gunpowder`, `heart_of_the_sea`, `honey_bottle`, `honeycomb`, `ink_sac`, `iron_helmet`, `iron_ingot`, `iron_nugget`, `iron_sword`, `jack_o_lantern`, `kelp`, `leather`, `light_blue_wool`, `lily_pad`, `magma_cream`, `melon_seeds`, `mud`, `mutton`, `name_tag`, `nautilus_shell`, `nether_star`, `oak_sapling`, `ochre_froglight`, `pearlescent_froglight`, `phantom_membrane`, `poppy`, `porkchop`, `potato`, `prismarine_crystals`, `prismarine_shard`, `pufferfish`, `pumpkin_seeds`, `rabbit`, `rabbit_foot`, `rabbit_hide`, `raw_copper`, `raw_iron`, `red_mushroom`, `redstone`, `rose_bush`, `rotten_flesh`, `salmon`, `sand`, `slime_ball`, `snowball`, `spider_eye`, `stick`, `stone`, `stone_axe`, `string`, `sugar`, `sunflower`, `sweet_berries`, `terracotta`, `turtle_scute`, `verdant_froglight`, `vine`, `wheat_seeds`, `white_wool`

</details>

---

## (B) Recipe-closure result — the truly unfarmable set

After closure, **447** items are obtainable. The remaining **886** items can be neither dropped nor crafted from drops. Splitting those by whether they belong to the goal at all:

- **111** are creative / technical / admin → **excluded** (Section C.1).
- **775** are real survival items that *should* be farmable but aren't → **the TODO** (Section C.2).

### Why so many? The three structural choke-points

Three missing 'keystone' drops cascade into hundreds of unreachable items:

1. **No `cobblestone` (and no `blackstone` / `cobbled_deepslate`).** `stone` *is* dropped, but you cannot turn stone into cobblestone. Cobblestone gates `#stone_tool_materials` and `#stone_crafting_materials`, so this single gap blocks **furnace, stone tools, cobblestone/mossy-cobblestone stairs+slabs+walls, levers, droppers/dispensers, pistons, brewing stand, and more.**
2. **Only `acacia_log` among woods.** No oak/spruce/birch/jungle/dark_oak/mangrove/cherry/bamboo/crimson/warped logs are dropped, so every plank-derived item for those 10 wood sets is unreachable (planks, stairs, slabs, doors, trapdoors, fences, gates, signs, boats, bookshelves that need specific planks, etc.). Acacia itself is mostly fine.
3. **No raw stone-cutter feedstock for the deepslate/tuff/blackstone/quartz/copper-block families**, and **no `sugar_cane`** (only refined `sugar`), which blocks `paper` → and thus maps, books, bookshelves, enchanting-table, firework rockets, etc.

---

## (C) Categorized gaps

### C.1 — Creative / technical / admin (EXCLUDE from the goal)

**111** items. Genuinely not survival-farmable items.

**Spawn eggs (80)** — creative-only. Examples: `allay_spawn_egg`, `armadillo_spawn_egg`, `axolotl_spawn_egg`, `bat_spawn_egg`, `bee_spawn_egg`, `blaze_spawn_egg` … (all 80 `*_spawn_egg`s).

**Other creative / technical (31):**

`air`, `barrier`, `bedrock`, `budding_amethyst`, `chain_command_block`, `command_block`, `debug_stick`, `dirt_path`, `end_portal_frame`, `farmland`, `infested_chiseled_stone_bricks`, `infested_cobblestone`, `infested_cracked_stone_bricks`, `infested_deepslate`, `infested_mossy_stone_bricks`, `infested_stone`, `infested_stone_bricks`, `jigsaw`, `knowledge_book`, `large_amethyst_bud`, `light`, `medium_amethyst_bud`, `petrified_oak_slab`, `reinforced_deepslate`, `repeating_command_block`, `small_amethyst_bud`, `spawner`, `structure_block`, `structure_void`, `trial_spawner`, `vault`

Notes on judgment calls: `infested_*` (silverfish host blocks) and the partial `*_amethyst_bud` stages (only the full `amethyst_cluster` drops shards) are treated as creative/technical. `air`, `farmland`, `dirt_path`, `spawner`, `trial_spawner`, `vault` are registry/placement technicalities with no survival item recipe. `budding_amethyst`, `bedrock`, `barrier`, `reinforced_deepslate`, `end_portal_frame`, `petrified_oak_slab`, the command blocks, `structure_block/void`, `jigsaw`, `light`, `debug_stick`, `knowledge_book` are all creative/admin.

### C.2 — Real farmable candidates (THE TODO)

**775** survival items that should be farmable but currently have no path. Listed in full, by theme.

#### Ores / raw materials / ingots / mineral blocks (29)

`amethyst_cluster`, `ancient_debris`, `coal_ore`, `copper_ore`, `deepslate_coal_ore`, `deepslate_copper_ore`, `deepslate_diamond_ore`, `deepslate_emerald_ore`, `deepslate_gold_ore`, `deepslate_iron_ore`, `deepslate_lapis_ore`, `deepslate_redstone_ore`, `diamond_ore`, `emerald_ore`, `gold_ore`, `iron_ore`, `lapis_block`, `lapis_lazuli`, `lapis_ore`, `nether_gold_ore`, `nether_quartz_ore`, `netherite_block`, `netherite_ingot`, `netherite_scrap`, `quartz`, `quartz_block`, `raw_gold`, `raw_gold_block`, `redstone_ore`

#### Wood, plants & nature (268)

Dominated by the **10 non-acacia wood sets** plus flowers (`dandelion`, tulips, `allium`, `cornflower`, `lily_of_the_valley`, `wither_rose`, …), mushrooms/coral, `paper`/`sugar_cane`, melon/pumpkin, chorus, sculk-free nature blocks, and the entity-in-`*_bucket` catches.

`acacia_hanging_sign`, `acacia_leaves`, `acacia_sapling`, `allium`, `axolotl_bucket`, `azalea`, `azalea_leaves`, `azure_bluet`, `bamboo_hanging_sign`, `big_dripleaf`, `birch_boat`, `birch_button`, `birch_chest_boat`, `birch_door`, `birch_fence`, `birch_fence_gate`, `birch_hanging_sign`, `birch_leaves`, `birch_log`, `birch_planks`, `birch_pressure_plate`, `birch_sapling`, `birch_sign`, `birch_slab`, `birch_stairs`, `birch_trapdoor`, `birch_wood`, `blue_orchid`, `brain_coral`, `brain_coral_block`, `brain_coral_fan`, `brown_mushroom_block`, `bubble_coral`, `bubble_coral_block`, `bubble_coral_fan`, `carved_pumpkin`, `cherry_boat`, `cherry_button`, `cherry_chest_boat`, `cherry_door`, `cherry_fence`, `cherry_fence_gate`, `cherry_hanging_sign`, `cherry_leaves`, `cherry_log`, `cherry_planks`, `cherry_pressure_plate`, `cherry_sapling`, `cherry_sign`, `cherry_slab`, `cherry_stairs`, `cherry_trapdoor`, `cherry_wood`, `chorus_flower`, `chorus_plant`, `cod_bucket`, `cornflower`, `crimson_button`, `crimson_door`, `crimson_fence`, `crimson_fence_gate`, `crimson_fungus`, `crimson_hanging_sign`, `crimson_hyphae`, `crimson_nylium`, `crimson_planks`, `crimson_pressure_plate`, `crimson_roots`, `crimson_sign`, `crimson_slab`, `crimson_stairs`, `crimson_stem`, `crimson_trapdoor`, `dandelion`, `dark_oak_boat`, `dark_oak_button`, `dark_oak_chest_boat`, `dark_oak_door`, `dark_oak_fence`, `dark_oak_fence_gate`, `dark_oak_hanging_sign`, `dark_oak_leaves`, `dark_oak_log`, `dark_oak_planks`, `dark_oak_pressure_plate`, `dark_oak_sign`, `dark_oak_slab`, `dark_oak_stairs`, `dark_oak_trapdoor`, `dark_oak_wood`, `dead_brain_coral`, `dead_brain_coral_block`, `dead_brain_coral_fan`, `dead_bubble_coral`, `dead_bubble_coral_block`, `dead_bubble_coral_fan`, `dead_fire_coral`, `dead_fire_coral_block`, `dead_fire_coral_fan`, `dead_horn_coral`, `dead_horn_coral_block`, `dead_horn_coral_fan`, `dead_tube_coral`, `dead_tube_coral_block`, `dead_tube_coral_fan`, `fern`, `fire_coral`, `fire_coral_block`, `fire_coral_fan`, `flowering_azalea`, `flowering_azalea_leaves`, `glow_lichen`, `grass_block`, `hanging_roots`, `horn_coral`, `horn_coral_block`, `horn_coral_fan`, `jungle_boat`, `jungle_button`, `jungle_chest_boat`, `jungle_door`, `jungle_fence`, `jungle_fence_gate`, `jungle_hanging_sign`, `jungle_leaves`, `jungle_log`, `jungle_planks`, `jungle_pressure_plate`, `jungle_sapling`, `jungle_sign`, `jungle_slab`, `jungle_stairs`, `jungle_trapdoor`, `jungle_wood`, `large_fern`, `lilac`, `lily_of_the_valley`, `mangrove_boat`, `mangrove_button`, `mangrove_chest_boat`, `mangrove_door`, `mangrove_fence`, `mangrove_fence_gate`, `mangrove_hanging_sign`, `mangrove_leaves`, `mangrove_log`, `mangrove_planks`, `mangrove_pressure_plate`, `mangrove_propagule`, `mangrove_roots`, `mangrove_sign`, `mangrove_slab`, `mangrove_stairs`, `mangrove_trapdoor`, `mangrove_wood`, `moss_block`, `moss_carpet`, `muddy_mangrove_roots`, `mushroom_stem`, `mycelium`, `nether_sprouts`, `nether_wart_block`, `oak_boat`, `oak_button`, `oak_chest_boat`, `oak_door`, `oak_fence`, `oak_fence_gate`, `oak_hanging_sign`, `oak_leaves`, `oak_log`, `oak_planks`, `oak_pressure_plate`, `oak_sign`, `oak_slab`, `oak_stairs`, `oak_trapdoor`, `oak_wood`, `orange_tulip`, `oxeye_daisy`, `paper`, `peony`, `pink_petals`, `pink_tulip`, `pitcher_plant`, `pitcher_pod`, `podzol`, `pufferfish_bucket`, `pumpkin`, `red_mushroom_block`, `red_tulip`, `rooted_dirt`, `salmon_bucket`, `sea_pickle`, `seagrass`, `short_grass`, `shroomlight`, `small_dripleaf`, `spore_blossom`, `spruce_boat`, `spruce_button`, `spruce_chest_boat`, `spruce_door`, `spruce_fence`, `spruce_fence_gate`, `spruce_hanging_sign`, `spruce_leaves`, `spruce_log`, `spruce_planks`, `spruce_pressure_plate`, `spruce_sapling`, `spruce_sign`, `spruce_slab`, `spruce_stairs`, `spruce_trapdoor`, `spruce_wood`, `stripped_acacia_log`, `stripped_acacia_wood`, `stripped_bamboo_block`, `stripped_birch_log`, `stripped_birch_wood`, `stripped_cherry_log`, `stripped_cherry_wood`, `stripped_crimson_hyphae`, `stripped_crimson_stem`, `stripped_dark_oak_log`, `stripped_dark_oak_wood`, `stripped_jungle_log`, `stripped_jungle_wood`, `stripped_mangrove_log`, `stripped_mangrove_wood`, `stripped_oak_log`, `stripped_oak_wood`, `stripped_spruce_log`, `stripped_spruce_wood`, `stripped_warped_hyphae`, `stripped_warped_stem`, `sugar_cane`, `tadpole_bucket`, `tall_grass`, `torchflower`, `torchflower_seeds`, `tropical_fish_bucket`, `tube_coral`, `tube_coral_block`, `tube_coral_fan`, `twisting_vines`, `warped_button`, `warped_door`, `warped_fence`, `warped_fence_gate`, `warped_fungus`, `warped_fungus_on_a_stick`, `warped_hanging_sign`, `warped_hyphae`, `warped_nylium`, `warped_planks`, `warped_pressure_plate`, `warped_roots`, `warped_sign`, `warped_slab`, `warped_stairs`, `warped_stem`, `warped_trapdoor`, `warped_wart_block`, `weeping_vines`, `white_tulip`, `wither_rose`

#### Food (17)

`beetroot`, `beetroot_seeds`, `beetroot_soup`, `chorus_fruit`, `cookie`, `enchanted_golden_apple`, `glistering_melon_slice`, `glow_berries`, `hay_block`, `melon`, `melon_slice`, `milk_bucket`, `popped_chorus_fruit`, `pumpkin_pie`, `suspicious_stew`, `tropical_fish`, `wheat`

#### Mob-drop materials (incl. heads, eggs, horse armor) (21)

`blaze_rod`, `cobweb`, `creeper_head`, `diamond_horse_armor`, `dragon_head`, `experience_bottle`, `frogspawn`, `ghast_tear`, `goat_horn`, `golden_horse_armor`, `iron_horse_armor`, `piglin_head`, `player_head`, `poisonous_potato`, `saddle`, `shulker_shell`, `skeleton_skull`, `sniffer_egg`, `turtle_egg`, `wither_skeleton_skull`, `zombie_head`

#### Redstone / tech / functional blocks (25)

`bell`, `command_block_minecart`, `comparator`, `copper_bulb`, `crafter`, `daylight_detector`, `dispenser`, `dropper`, `exposed_copper_bulb`, `furnace_minecart`, `lectern`, `lever`, `lodestone`, `observer`, `oxidized_copper_bulb`, `piston`, `redstone_lamp`, `respawn_anchor`, `sticky_piston`, `target`, `waxed_copper_bulb`, `waxed_exposed_copper_bulb`, `waxed_oxidized_copper_bulb`, `waxed_weathered_copper_bulb`, `weathered_copper_bulb`

#### Dyes (7)

`blue_dye`, `brown_dye`, `cocoa_beans`, `cyan_dye`, `light_blue_dye`, `magenta_dye`, `purple_dye`

#### Brewing (8)

`brewing_stand`, `glowstone_dust`, `lingering_potion`, `nether_wart`, `potion`, `spectral_arrow`, `splash_potion`, `tipped_arrow`

#### Rare / boss-tier (farmable in this mod's spirit via rare legendary drops) (20)

`beacon`, `breeze_rod`, `calibrated_sculk_sensor`, `dragon_egg`, `elytra`, `end_crystal`, `heavy_core`, `mace`, `netherite_upgrade_smithing_template`, `ominous_bottle`, `ominous_trial_key`, `sculk`, `sculk_catalyst`, `sculk_sensor`, `sculk_shrieker`, `sculk_vein`, `totem_of_undying`, `trial_key`, `trident`, `wind_charge`

#### Building & decorative blocks (311)

The largest bucket — almost entirely **stone-family and copper-family derivatives blocked by the cobblestone/feedstock gap** (cobblestone/mossy/andesite/diorite/granite/tuff/deepslate/blackstone/nether-brick/end-stone/red-sandstone/quartz/purpur variants and their slabs/stairs/walls/polished/chiseled/cut forms), plus the full **copper oxidation+wax matrix**, **concrete & concrete powder**, **stained glass & panes**, **glazed terracotta**, **shulker boxes** of every color, **dyed wool/carpet/bed/candle/banner** that need an unfarmed dye, plus `furnace`/`blast_furnace`/`smoker`/`bookshelf`/`enchanting_table`/`ender_chest`/`book`/`map`/`bundle`/chainmail & netherite armor/stone & netherite tools.

`andesite`, `andesite_slab`, `andesite_stairs`, `andesite_wall`, `bee_nest`, `black_concrete`, `black_shulker_box`, `blackstone`, `blackstone_slab`, `blackstone_stairs`, `blackstone_wall`, `blast_furnace`, `blue_candle`, `blue_concrete`, `blue_concrete_powder`, `blue_glazed_terracotta`, `blue_shulker_box`, `blue_stained_glass`, `blue_stained_glass_pane`, `blue_terracotta`, `book`, `bookshelf`, `brown_candle`, `brown_concrete`, `brown_concrete_powder`, `brown_glazed_terracotta`, `brown_shulker_box`, `brown_stained_glass`, `brown_stained_glass_pane`, `brown_terracotta`, `bundle`, `cartography_table`, `chainmail_boots`, `chainmail_chestplate`, `chainmail_helmet`, `chainmail_leggings`, `chipped_anvil`, `chiseled_deepslate`, `chiseled_nether_bricks`, `chiseled_polished_blackstone`, `chiseled_quartz_block`, `chiseled_red_sandstone`, `chiseled_tuff`, `chiseled_tuff_bricks`, `cobbled_deepslate`, `cobbled_deepslate_slab`, `cobbled_deepslate_stairs`, `cobbled_deepslate_wall`, `cobblestone`, `cobblestone_slab`, `cobblestone_stairs`, `cobblestone_wall`, `cracked_deepslate_bricks`, `cracked_deepslate_tiles`, `cracked_nether_bricks`, `cracked_polished_blackstone_bricks`, `crying_obsidian`, `cut_red_sandstone`, `cut_red_sandstone_slab`, `cyan_banner`, `cyan_bed`, `cyan_candle`, `cyan_carpet`, `cyan_concrete`, `cyan_concrete_powder`, `cyan_glazed_terracotta`, `cyan_shulker_box`, `cyan_stained_glass`, `cyan_stained_glass_pane`, `cyan_terracotta`, `cyan_wool`, `damaged_anvil`, `deepslate`, `deepslate_brick_slab`, `deepslate_brick_stairs`, `deepslate_brick_wall`, `deepslate_bricks`, `deepslate_tile_slab`, `deepslate_tile_stairs`, `deepslate_tile_wall`, `deepslate_tiles`, `diorite`, `diorite_slab`, `diorite_stairs`, `diorite_wall`, `dripstone_block`, `enchanted_book`, `enchanting_table`, `end_rod`, `end_stone`, `end_stone_brick_slab`, `end_stone_brick_stairs`, `end_stone_brick_wall`, `end_stone_bricks`, `ender_chest`, `exposed_chiseled_copper`, `exposed_copper`, `exposed_copper_door`, `exposed_copper_grate`, `exposed_copper_trapdoor`, `exposed_cut_copper`, `exposed_cut_copper_slab`, `exposed_cut_copper_stairs`, `filled_map`, `firework_rocket`, `firework_star`, `furnace`, `gilded_blackstone`, `glowstone`, `granite`, `granite_slab`, `granite_stairs`, `granite_wall`, `gray_concrete`, `gray_shulker_box`, `green_concrete`, `green_shulker_box`, `ice`, `lava_bucket`, `light_blue_candle`, `light_blue_concrete`, `light_blue_concrete_powder`, `light_blue_glazed_terracotta`, `light_blue_shulker_box`, `light_blue_stained_glass`, `light_blue_stained_glass_pane`, `light_blue_terracotta`, `light_gray_concrete`, `light_gray_shulker_box`, `lime_concrete`, `lime_shulker_box`, `magenta_banner`, `magenta_bed`, `magenta_candle`, `magenta_carpet`, `magenta_concrete`, `magenta_concrete_powder`, `magenta_glazed_terracotta`, `magenta_shulker_box`, `magenta_stained_glass`, `magenta_stained_glass_pane`, `magenta_terracotta`, `magenta_wool`, `map`, `mossy_cobblestone`, `mossy_cobblestone_slab`, `mossy_cobblestone_stairs`, `mossy_cobblestone_wall`, `mud_brick_slab`, `mud_brick_stairs`, `mud_brick_wall`, `mud_bricks`, `nether_brick`, `nether_brick_fence`, `nether_brick_slab`, `nether_brick_stairs`, `nether_brick_wall`, `nether_bricks`, `netherite_axe`, `netherite_boots`, `netherite_chestplate`, `netherite_helmet`, `netherite_hoe`, `netherite_leggings`, `netherite_pickaxe`, `netherite_shovel`, `netherite_sword`, `netherrack`, `obsidian`, `orange_concrete`, `orange_shulker_box`, `oxidized_chiseled_copper`, `oxidized_copper`, `oxidized_copper_door`, `oxidized_copper_grate`, `oxidized_copper_trapdoor`, `oxidized_cut_copper`, `oxidized_cut_copper_slab`, `oxidized_cut_copper_stairs`, `packed_ice`, `packed_mud`, `pink_concrete`, `pink_shulker_box`, `pointed_dripstone`, `polished_andesite`, `polished_andesite_slab`, `polished_andesite_stairs`, `polished_blackstone`, `polished_blackstone_brick_slab`, `polished_blackstone_brick_stairs`, `polished_blackstone_brick_wall`, `polished_blackstone_bricks`, `polished_blackstone_button`, `polished_blackstone_pressure_plate`, `polished_blackstone_slab`, `polished_blackstone_stairs`, `polished_blackstone_wall`, `polished_deepslate`, `polished_deepslate_slab`, `polished_deepslate_stairs`, `polished_deepslate_wall`, `polished_diorite`, `polished_diorite_slab`, `polished_diorite_stairs`, `polished_granite`, `polished_granite_slab`, `polished_granite_stairs`, `polished_tuff`, `polished_tuff_slab`, `polished_tuff_stairs`, `polished_tuff_wall`, `powder_snow_bucket`, `purple_banner`, `purple_bed`, `purple_candle`, `purple_carpet`, `purple_concrete`, `purple_concrete_powder`, `purple_glazed_terracotta`, `purple_shulker_box`, `purple_stained_glass`, `purple_stained_glass_pane`, `purple_terracotta`, `purple_wool`, `purpur_block`, `purpur_pillar`, `purpur_slab`, `purpur_stairs`, `quartz_bricks`, `quartz_pillar`, `quartz_slab`, `quartz_stairs`, `red_concrete`, `red_nether_brick_slab`, `red_nether_brick_stairs`, `red_nether_brick_wall`, `red_nether_bricks`, `red_sand`, `red_sandstone`, `red_sandstone_slab`, `red_sandstone_stairs`, `red_sandstone_wall`, `red_shulker_box`, `shulker_box`, `smoker`, `smooth_quartz`, `smooth_quartz_slab`, `smooth_quartz_stairs`, `smooth_red_sandstone`, `smooth_red_sandstone_slab`, `smooth_red_sandstone_stairs`, `soul_campfire`, `soul_lantern`, `soul_sand`, `soul_soil`, `soul_torch`, `sponge`, `stone_hoe`, `stone_pickaxe`, `stone_shovel`, `stone_sword`, `suspicious_gravel`, `suspicious_sand`, `tuff`, `tuff_brick_slab`, `tuff_brick_stairs`, `tuff_brick_wall`, `tuff_bricks`, `tuff_slab`, `tuff_stairs`, `tuff_wall`, `water_bucket`, `waxed_exposed_chiseled_copper`, `waxed_exposed_copper`, `waxed_exposed_copper_door`, `waxed_exposed_copper_grate`, `waxed_exposed_copper_trapdoor`, `waxed_exposed_cut_copper`, `waxed_exposed_cut_copper_slab`, `waxed_exposed_cut_copper_stairs`, `waxed_oxidized_chiseled_copper`, `waxed_oxidized_copper`, `waxed_oxidized_copper_door`, `waxed_oxidized_copper_grate`, `waxed_oxidized_copper_trapdoor`, `waxed_oxidized_cut_copper`, `waxed_oxidized_cut_copper_slab`, `waxed_oxidized_cut_copper_stairs`, `waxed_weathered_chiseled_copper`, `waxed_weathered_copper`, `waxed_weathered_copper_door`, `waxed_weathered_copper_grate`, `waxed_weathered_copper_trapdoor`, `waxed_weathered_cut_copper`, `waxed_weathered_cut_copper_slab`, `waxed_weathered_cut_copper_stairs`, `weathered_chiseled_copper`, `weathered_copper`, `weathered_copper_door`, `weathered_copper_grate`, `weathered_copper_trapdoor`, `weathered_cut_copper`, `weathered_cut_copper_slab`, `weathered_cut_copper_stairs`, `wet_sponge`, `white_concrete`, `white_shulker_box`, `writable_book`, `written_book`, `yellow_concrete`, `yellow_shulker_box`

#### Music discs (20)

`disc_fragment_5`, `music_disc_11`, `music_disc_13`, `music_disc_5`, `music_disc_blocks`, `music_disc_cat`, `music_disc_chirp`, `music_disc_creator`, `music_disc_creator_music_box`, `music_disc_far`, `music_disc_mall`, `music_disc_mellohi`, `music_disc_otherside`, `music_disc_pigstep`, `music_disc_precipice`, `music_disc_relic`, `music_disc_stal`, `music_disc_strad`, `music_disc_wait`, `music_disc_ward`

#### Pottery sherds (23)

`angler_pottery_sherd`, `archer_pottery_sherd`, `arms_up_pottery_sherd`, `blade_pottery_sherd`, `brewer_pottery_sherd`, `burn_pottery_sherd`, `danger_pottery_sherd`, `explorer_pottery_sherd`, `flow_pottery_sherd`, `friend_pottery_sherd`, `guster_pottery_sherd`, `heart_pottery_sherd`, `heartbreak_pottery_sherd`, `howl_pottery_sherd`, `miner_pottery_sherd`, `mourner_pottery_sherd`, `plenty_pottery_sherd`, `prize_pottery_sherd`, `scrape_pottery_sherd`, `sheaf_pottery_sherd`, `shelter_pottery_sherd`, `skull_pottery_sherd`, `snort_pottery_sherd`

#### Smithing armor-trim templates (18)

`bolt_armor_trim_smithing_template`, `coast_armor_trim_smithing_template`, `dune_armor_trim_smithing_template`, `eye_armor_trim_smithing_template`, `flow_armor_trim_smithing_template`, `host_armor_trim_smithing_template`, `raiser_armor_trim_smithing_template`, `rib_armor_trim_smithing_template`, `sentry_armor_trim_smithing_template`, `shaper_armor_trim_smithing_template`, `silence_armor_trim_smithing_template`, `snout_armor_trim_smithing_template`, `spire_armor_trim_smithing_template`, `tide_armor_trim_smithing_template`, `vex_armor_trim_smithing_template`, `ward_armor_trim_smithing_template`, `wayfinder_armor_trim_smithing_template`, `wild_armor_trim_smithing_template`

#### Banner patterns (8)

`creeper_banner_pattern`, `flow_banner_pattern`, `flower_banner_pattern`, `globe_banner_pattern`, `guster_banner_pattern`, `mojang_banner_pattern`, `piglin_banner_pattern`, `skull_banner_pattern`

---

## Most notable / surprising gaps (the short list)

Common survival items a player would *expect* to farm, that currently have **no** path:

- **`cobblestone`** — the keystone. Drags down furnace, all stone tools, droppers/dispensers/pistons/observers, levers, brewing stand, and every cobblestone/mossy building block.
- **`wheat`** — only `wheat_seeds` drop, and seeds don't craft into wheat; blocks `hay_block`, `bread`-adjacent foods, `cookie`.
- **`paper`** — only refined `sugar` drops, never `sugar_cane`; blocks maps, books, bookshelves, enchanting table, firework rockets.
- **`oak_log` & all 10 non-acacia woods** — only `acacia_log` drops; the entire oak/spruce/birch/jungle/dark_oak/mangrove/cherry/bamboo/crimson/warped product trees are unreachable.
- **Ores in block form** (`iron_ore`, `gold_ore`, `copper_ore`, `coal_ore`, `diamond_ore`, `redstone_ore`, `lapis_ore`, `emerald_ore`, all `deepslate_*_ore`, `nether_quartz_ore`, `nether_gold_ore`, `ancient_debris`) — the refined drops exist (iron/diamond/etc.) but the *ore blocks* themselves don't.
- **`quartz` / `lapis_lazuli` / `glowstone_dust` / `nether_wart`** — common crafting/brewing mats with no drop.
- **`blaze_rod`, `ghast_tear`, `shulker_shell`** — staple mob materials absent (note: `blaze_powder`, `magma_cream`, `ender_pearl` *do* drop).
- **`saddle`, `name_tag`(✓ drops), horse armor (iron/gold/diamond)** — utility loot; saddle & horse armor have no path.
- **All mob heads** (`zombie_head`, `creeper_head`, `skeleton_skull`, `wither_skeleton_skull`, `piglin_head`, `dragon_head`).
- **`milk_bucket`** (no cow-equivalent), **`glow_berries`, `melon`, `beetroot`** food gaps.
- **`trident`, `elytra`, `totem_of_undying`, `dragon_egg`, `mace`/`heavy_core`, `beacon`** — boss/rare tier; in this mod's spirit these are legendary-drop candidates.
- **All 20 music discs, 23 pottery sherds, 18 armor-trim templates, 8 banner patterns** — exploration/collectible loot with no farm path.

> Already covered (sanity check — these *are* reachable, so **not** gaps): `iron_ingot`/`raw_iron`, `raw_copper`→`copper_ingot`, `gold_nugget`→`gold_ingot`, `diamond`, `emerald`, `coal`, `redstone`, `leather`, `string`, `gunpowder`, `glass`, `bucket`, `stick`, `iron_pickaxe`, `nether_star`, `heart_of_the_sea`, `echo_shard`, `dragon_breath`, `nautilus_shell`, `prismarine_*`.

---

*Generated from `all-species-drops.json` + `minecraft-merged.jar` (1.21.1). Closure & categorization scripts in the analysis scratchpad.*
