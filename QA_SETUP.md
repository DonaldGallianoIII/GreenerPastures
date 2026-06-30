# 🧰 QA Setup Kit — paste-ready MC commands (jar md5 `edf05bf`, MC 1.21.1)

_Companion to `QA_PENDING.md` (the checklist). All mod IDs verified against the deployed jar. Best run in a
**creative + cheats** world, standing in an **open flat area** (a superflat creative world is ideal — the `/fill`
ore fields need room). Enchant syntax is 1.21.1 component format._

> **Load check first.** After a full restart, tell me "in" and I'll scan the instance log to confirm the mod
> loaded clean (no mixin error = the ghost-pasture `@Redirect` resolved). Don't trust any test until that's green.

---

## 0 · Give yourself the whole toolbox
```
/give @s greenerpastures:daemon
/give @s greenerpastures:renderer
/give @s greenerpastures:harvester
/give @s greenerpastures:biobank
/give @s greenerpastures:compiler
/give @s greenerpastures:pasture_wand
/give @s greenerpastures:soul_tether 6
/give @s greenerpastures:breeding_upgrade_copper
/give @s greenerpastures:breeding_upgrade_greener
/give @s greenerpastures:augment_shiny
/give @s greenerpastures:augment_iv_floor
/give @s greenerpastures:augment_ev
/give @s greenerpastures:augment_speed
/give @s greenerpastures:augment_enrichment
/give @s greenerpastures:augment_drop_rate
/give @s greenerpastures:augment_drop_yield
/give @s cobblemon:pasture 4
```

## 1 · Data — feed the Daemon (needed for ALL buffs Q23–Q30, and Q36)
The Daemon buffs only run while **fed** (Data > 0). Normally you earn Data from a Renderer culling eggs; this new
debug command mints it directly:
```
/gp data set 100000      ← set balance
/gp data                 ← check it
/gp data add 50000       ← top up (it drains while buffs run)
/gp data set 0           ← starve it (to test "buffs stop when broke")
```

---

## Cluster A · Ghost Pasture (Q38) — the headline admin fix
```
/pokegive @s pidgey
/pokegive @s rattata
/pokegive @s ditto
```
1. Place a `cobblemon:pasture`, right-click it, assign those mons → they spawn + **roam**.
2. **Sneak + empty-hand right-click** the pasture → chat says **"ghost pasture ON…"** → roamers **vanish instantly**.
3. Confirm: pasture still **lays eggs** (data intact), mons still listed in its GUI, F3 entity count dropped.
4. Save+quit, reload → still ghost, mons stay gone.
5. **🆕 BUG-003 un-hide (first in-game test of the fix):** **sneak + empty-hand right-click AGAIN** → chat **"ghost pasture OFF…"** → the tethered mons **re-materialise** (respawned from stored data, re-linked to the *existing* tethers — no new tethers, no dupes). Watch `~/gp-logs/latest.log` for `keeper ghost_off spawned:N`. Toggle a few times → no duplicate roamers pile up.

---

## Cluster B · Daemon buffs (Q23–Q30) — **🆕 compile-your-own model (BUG-004)**
The Daemon no longer grants the whole suite just by holding it. You **compile** the buffs you want onto it,
**right-click to toggle it ON** (it gains the enchant glint), and it works from **anywhere in your inventory** while
ON + fed (Data > 0). You're billed for **only the buffs you installed**.

### B0 · The BUG-004 core checks (do these FIRST)
```
/gp daemon set feather_falling 3   ← a cheap, single-buff Daemon
/gp daemon list                    ← shows: [OFF] · Feather Falling +3 · its Data/s
/gp daemon on                      ← (or just right-click the Daemon) → glint appears, "Daemon ON"
```
- **Glint toggle:** right-click the Daemon → it turns shiny (ON) / dull (OFF); actionbar confirms. `/gp daemon on`/`off` do the same.
- **Drain = only-installed:** with just Feather Falling, `/gp data` ticks down **slowly** (~0.75/s at +3) — NOT the old ~15.75/s whole-suite rate. That smaller bill *is* the fix.
- **Works from inventory:** move the ON Daemon out of your hand into your pack/hotbar (NOT in hand) → Feather Falling still works (fall ~10 blocks → reduced damage). Toggle OFF → reverts.

### B1 · Load the full test bench (for Q24–Q30)
Compile everything, then toggle ON — now every buff below is live while the Daemon just sits in your inventory:
```
/gp daemon set fortune 3
/gp daemon set auto_smelt 3
/gp daemon set vein_mine 3
/gp daemon set xp_boost 3
/gp daemon set potion_duration 3
/gp daemon set looting 3
/gp daemon set lure 3
/gp daemon set luck_of_the_sea 3
/gp daemon set frost_walker 2
/gp daemon set respiration 3
/gp daemon set swift_sneak 3
/gp daemon set feather_falling 3
/gp daemon set haste 3
/gp daemon set saturation 3
/gp daemon set magnet 3
/gp daemon list      ← review the whole loadout + total Data/s
/gp daemon on
```
Keep Data topped up (`/gp data add 50000`) — the full loadout drains faster than one buff. Then run Q24–Q30 below;
each test is unchanged **except** the buff just needs to be **in the loadout + the Daemon ON in your inventory** (not held).

### Q24 Fortune · Q25 Auto-Smelt — mining walls
```
/give @s minecraft:diamond_pickaxe[minecraft:enchantments={"minecraft:fortune":3}]
/give @s minecraft:diamond_pickaxe          ← plain, for the "no Fortune = no boost" check
/fill ~2 ~ ~2 ~9 ~4 ~2 minecraft:diamond_ore
/fill ~2 ~ ~3 ~9 ~4 ~3 minecraft:iron_ore
/fill ~2 ~ ~4 ~9 ~4 ~4 minecraft:gold_ore
/fill ~2 ~ ~5 ~9 ~4 ~5 minecraft:copper_ore
/fill ~2 ~ ~6 ~9 ~4 ~6 minecraft:redstone_ore
```
Fortune: mine the diamond/redstone wall with the Fortune pick + fed Daemon → drops jump (Mk III ≈ Fortune VI).
Auto-smelt: mine iron/gold/copper → **ingots** drop, not raw. With Fortune too → multiple ingots.

### Q27 Vein-Miner — ⚠ safety FIRST
```
/give @s minecraft:diamond_pickaxe[minecraft:enchantments={"minecraft:silk_touch":1}]
/fill ~8 ~ ~8 ~10 ~3 ~10 minecraft:stone     ← CONTROL: mining one must break ONLY one
/fill ~8 ~ ~12 ~10 ~3 ~14 minecraft:dirt     ← CONTROL
/fill ~2 ~ ~8 ~6 ~4 ~12 minecraft:iron_ore   ← 125-block vein: pops as one, and tests the cap (~96 at Mk III)
/fill ~12 ~ ~8 ~12 ~6 ~8 minecraft:oak_log   ← 7-tall log pillar → chop bottom, whole thing falls
```
Safety: with a fed Daemon, mine the **stone/dirt** → only one block breaks (NOT veinable). Then mine the iron vein
→ whole vein pops (capped). Silk-touch pick → vein drops as ore blocks (real tool used per block).

### Q26 XP Boost — instant test
```
/xp add @s 100 points
```
With the Daemon ON in your inventory (XP Boost compiled) → actual XP gained should exceed 100 (+3 ≈ +75% → ~175).

### Q28 Potion Duration — utility extended, combat NOT
```
/give @s minecraft:potion[minecraft:potion_contents={potion:"minecraft:night_vision"}]
/give @s minecraft:potion[minecraft:potion_contents={potion:"minecraft:water_breathing"}]
/give @s minecraft:potion[minecraft:potion_contents={potion:"minecraft:fire_resistance"}]
/give @s minecraft:potion[minecraft:potion_contents={potion:"minecraft:strength"}]    ← CONTROL: must NOT extend
```

### Q29 Attribute buffs (Respiration / Swift Sneak / Feather Falling)
```
/give @s minecraft:diamond_helmet[minecraft:enchantments={"minecraft:respiration":3}]   ← optional, to stack on top
```
Respiration: dive bare-headed → air drains slow. Feather Falling: fall ~10 blocks → reduced damage (never heals).
Swift Sneak: crouch → faster than vanilla. Toggle the Daemon OFF / Data→0 → reverts instantly.

### Q30 Value-effect buffs (Lure / Luck of the Sea / Frost Walker / Looting)
```
/give @s minecraft:fishing_rod
/give @s minecraft:water_bucket          ← place water, walk over it → Frost Walker freezes underfoot
/give @s minecraft:diamond_sword[minecraft:enchantments={"minecraft:looting":3}]
/summon minecraft:cow ~ ~ ~3             ← Looting needs a mob (your live server has none — test here)
```
Lure: bites come faster. LotS: more treasure over many casts. Looting: kill the cow → more drops (Mk III ≈ Looting III).

---

## Cluster C · Breeding-meta augments (Q31–Q34)
Hold a **Kernel** (`breeding_upgrade_copper`), set the augment, then slot it into a managed pasture and breed:
```
/gp augment set nature 4      ← Adamant (Q31)
/gp augment set ball 4        ← master_ball (Q32)
/gp augment set ability 1     ← Hidden Ability ON (Q33)
/gp augment set egg_move 1    ← Egg Moves ON (Q34)
/gp augment list              ← inspect the held Kernel
/gp augment clear             ← strip all
```
A breeding pair with a known HA + egg moves:
```
/pokegive @s ditto
/pokegive @s gible            ← HA = Rough Skin; has notable egg moves
```
Breed → hatch an egg → check nature/ball/ability/moves on the hatchling.

---

## Cluster D · Notifications + Goal (Q35–Q37)
```
/gp goal set gible true 4     ← hunt: shiny gible, ≥4 perfect IVs (Q37)
/gp goal                      ← progress
/gp goal clear
```
- **Q35 shiny ping:** breed with a **Shiny augment** until a shiny egg is laid → chat ping + chime.
- **Q36 Data milestone:** `/gp data set 999000`, then render eggs to cross 1,000,000 → owner-only "💾 Data milestone" ping.

---

## Cluster E · Foundation rig (Q1–Q22)
The mature systems. Build the **breeding rig** once and most rows fall out of it:
1. `cobblemon:pasture` + a pair assigned (Ditto + a mon).
2. **`pasture_wand`** right-click the pasture → set the pairing bucket + slot a **Kernel** (`breeding_upgrade_copper`, or `_greener` for 8 pairs) → save. Eggs start appearing.
3. **`renderer`** block touching the pasture → culls non-keeper eggs → **Data** (⭐ shiny + perfect-IV eggs MUST stay).
4. **`harvester`** block touching the pasture → passive mon drops into its own chest.
5. **`compiler`** block → slot a Kernel + an `augment_*` item → Compile → writes the augment onto the Kernel.
6. **`biobank`** → right-click eggs to bank them, empty-hand = summary.

See `QA_PENDING.md` rows Q1–Q22 for each specific check.

---

## 📜 Watch the logs while you test
- **Feature events:** `tail -F ~/gp-logs/latest.log` (every buff/breed/render/goal line lands here).
- **Errors / mixin load:** the instance log at `…/Greener Pastures Test/logs/latest.log` — or just tell me and I'll scan it.

## ⚠️ If an enchant command errors
1.21.1 should accept `[minecraft:enchantments={"minecraft:fortune":3}]`. If your build rejects it, use the wrapped
form: `[minecraft:enchantments={levels:{"minecraft:fortune":3}}]`.
