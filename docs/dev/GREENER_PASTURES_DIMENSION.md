# 🌫️ Greener Pastures — the Liminal Dimension (PARKED — separate milestone)

> **Scope status: NOT v1.** This is a whole new system (worldgen + dimension + portal + horror). It's a **future feature update OR its own standalone horror mod** — explicitly *not* part of "upgraded pastures." Captured here so the design is preserved; do not build until the upgrade feature ships. Pegged 2026-06-25 (Deuce). See `IDLE_BREEDING_IDEA.md` for the upgrade system this would eventually feed.

## The concept
A custom **flat dimension** called *Greener Pastures* — but liminal/creepy, not paradise. You portal in onto an **endless flat plane of sickly pale-green grass at ~y200**, blanketed in fog, dead silent. Under the grass: **deepslate all the way down**, where the **endgame upgrade ores** are mined in depth-banded layers. The mod's namesake becomes a place you can *go* — and the name flips: you finally reach "greener pastures" and they're **hollow, wrong, and liminal.**

## Why it would exist
- **Endgame ore source** for the top upgrade tiers (see the surround-craft ladder in `IDLE_BREEDING_IDEA.md`). NOTE: v1 doesn't need this — the ladder's custom tiers use **compressed-shiny-egg blocks** (breeding loop) instead. The dimension is a *fancier later source*, not a dependency.
- **Identity piece** — a memorable, on-theme destination. The kind of thing people make videos about.

## The flat generation (the easy part)
`minecraft:flat` generator — just a stack of block layers, no noise/terrain tuning:
```json
"generator": { "type": "minecraft:flat", "settings": {
  "biome": "greenerpastures:greener_pastures",
  "features": true,          // ← REQUIRED or ores won't generate (superflat defaults off)
  "layers": [
    { "block": "minecraft:bedrock",     "height": 1   },
    { "block": "minecraft:deepslate",   "height": 210 },
    { "block": "minecraft:stone",       "height": 48  },
    { "block": "minecraft:dirt",        "height": 5   },
    { "block": "minecraft:grass_block", "height": 1   }
  ]
}}
```
Grass cap ~y200, thin soil band, deepslate to bedrock. Layers build bottom-up; tune heights + `dimension_type.min_y/height` so grass lands at y200.

## Depth-banded ores = the upgrade ladder made physical
Ores are **features** carried by the dimension's biome, each with a Y-range → *dig deeper = better tier*:

| Depth | Ore (placeholder names) | Feeds |
|---|---|---|
| y 150–199 | tier-1 custom ore (Verdant?) | first custom upgrade tier |
| y 0–150   | tier-2 custom ore (Prism?)   | mid custom tier |
| y −64–0   | endgame ore (Chroma?)        | top tier (+ netherite-binder block) |

Pure `placed_feature` JSON with height ranges. The "mine deeper for the best material" gradient *is* the progression curve.

## The liminal aesthetic (all JSON: biome `effects` + `dimension_type`)
- **Sky renderer** (`dimension_type.effects`, only 3 options):
  - `minecraft:the_nether` + pale grey fog = **no sky, endless close grey fog over flat grass** ← the money shot (no horizon = implied infinity).
  - `minecraft:the_end` = dark void/star sky, no weather (eerie-empty alternative).
- **Flat shadowless light** — high `ambient_light` → no shadows → "lit but no sun" fluorescent wrongness.
- **Desaturated palette** (biome effects: `grass_color`, `fog_color`, `sky_color`) — sickly washed-out pale green, grey-white fog. Wrong-green, not lush.
- **Sound is half the horror** — biome `mood_sound` (cave-creep stingers), low `ambient_sound` drone, sparse `additions_sound`. Even vanilla cave ambience over open grass is deeply unsettling; custom drone later.
- **Total stillness** — no weather/precipitation, no surface mobs (you, alone, on infinite fog-grass). Emptiness *is* the dread.
- **Optional** — sparse drifting ambient `particle` (white motes).

## ⚰️ The horror mechanic: 100:1 coordinate scale → "get lost = stranded"
This is the centerpiece. `coordinate_scale` only controls **cross-dimension portal coordinate math** — but at **100:1** it becomes a horror engine:
- Your entry portal is your **one anchor home** (it links back to your home coords).
- Wander **200 blocks** into the fog, lose sight of the portal, light a *new* one to leave → you exit the overworld **20,000 blocks from home**, in unexplored wilderness. Wander 500 → 50,000. Not dead — **stranded**, which is scarier.
- The rule the player learns the hard way: **"only leave through the portal you came in by."**

**Requires coordinate-LINKED portals** (vanilla-nether style, not fixed-hub) — the scale only bites because a new portal links to `position × 100`. Custom Portal API supports this.

**Amplifiers:**
- Fog tuned so you **lose sight of the portal at ~30 blocks** — careful players creep out and back; one careless sprint strands you.
- **Portal as a glowing beacon** (light/particles/hum) — the only feature in a featureless void; your lifeline.
- **Ores straight DOWN, not out** — safe loop is *vertical* (dig down from portal, climb back, leave). Lateral wandering has no reward, only the trap.
- Flat sameness = no landmarks; navigate by coords + nerve only.

**Build-it-right (since it ships to others):**
1. **One escape hatch so lost ≠ softlocked** — death→bed respawn, or a one-use "Waystone Home" charm. Keep the *threat*; never permanently brick a player (that's a 1-star review).
2. **Chunk bloat** — portals lit deep in the fog generate overworld chunks tens of thousands of blocks out; at 100:1 it scatters them aggressively. Fine in 1.21 but real. The scale is a dial: **100:1 = maximally brutal, 50:1 = still terrifying with less sprawl.**
- Coords/F3 as an "out" is a *feature* — rewards the prepared, delivers full liminal horror to the careless.

## Tech checklist (when we build it)
- [ ] `dimension_type/greener_pastures.json` (coordinate_scale, ambient_light, effects, fixed_time, min_y/height, spawn rules)
- [ ] `dimension/greener_pastures.json` (flat generator + layers)
- [ ] `worldgen/biome/greener_pastures.json` (liminal effects + ore features + sounds)
- [ ] custom ore blocks (+ deepslate variants) + raw/ingot/smelt + tool-tier + textures
- [ ] `worldgen/configured_feature` + `placed_feature` per ore (depth-banded)
- [ ] portal: **Custom Portal API** dependency, coordinate-linked, custom frame block + igniter, glowing portal
- [ ] escape item ("Waystone Home") or rely on bed-respawn
- [ ] optional custom ambient/drone sounds

## Open decisions (when un-parked)
- Scale: **50:1 vs 100:1** (brutality vs chunk sprawl).
- Sky: foggy-grey (`the_nether`) vs void (`the_end`).
- Eternal flat daylight-through-fog vs perpetual dim twilight (`fixed_time`).
- Mobs: fully peaceful, or peaceful surface + dangerous depths?
- Does the surface *do* anything (breeding bonus while in the realm) or just the mine entrance?
- Ore names (green/spectral theme).
- Escape-hatch form (bed-respawn only, or a craftable charm).

## Could it be its own mod?
**Yes** — the dimension + portal + horror is self-contained and big enough to stand alone. That's the tell that it's not v1. Easiest path: ship "upgraded pastures" first; bolt this on as a **feature update** (or release it as a separate liminal-horror mod that *integrates* with Greener Pastures if installed).
