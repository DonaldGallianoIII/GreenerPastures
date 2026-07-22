# Under-represented drops - audit + change proposal (2026-07-21, Deuce + Claude)

> Source: `all-species-drops.json` (Cobblemon 1.7.3, 1025 species, 2150 entries), cross-checked against
> the live GP `rituals.json` type-drops so nothing here duplicates what we already patched.
> Context: Deuce's worlds are survival, no shops, NO vanilla mobs - if a pasture can't produce it,
> only mining can. See `FARMABILITY_GAP.md` for the full 1333-item closure analysis (33% reachable).

## Headline: GOLD is the biggest hole in the game

- `gold_nugget`: **only Meowth + Persian** (one family), small rates. `gold_ingot`: **zero**. `raw_gold`: **zero**.
- The punchline: **Gimmighoul and Gholdengo - the literal gold Pokemon - drop only `cobblemon:relic_coin`** (24-48!).
- No GP type-drop covers gold yet.

## Single-family bottlenecks (one evolution line owns the whole supply)

| Item | Sole source | Note |
|---|---|---|
| redstone | Electabuzz line | electric type-drop begging to exist |
| gunpowder | Voltorb line | |
| diamond | Sableye, Carbink | maybe deliberately scarce - Deuce's call |
| emerald | Sableye + Nickit line | |
| iron_ingot | Duraludon only | nuggets: Honedge line + Orthworm/Revavroom; raw 0-1: Magnemite/Bronzor |
| honeycomb | Combee line | |
| nautilus_shell | Omanyte line | |
| prismarine_crystals | Chinchou line | conduit path effectively locked |
| name_tag | Klefki only | |

## Zero-source even after GP type-drops

(type-drops already cover: blaze_rod, ghast_tear, quartz, lapis, echo_shard, amethyst_shard, ice, sugar cane, beetroot, nether wart)

`glowstone_dust` · `copper` (ore-abundant, low priority) · `obsidian` · `sponge` · `shulker_shell` ·
`totem_of_undying` · `wither_skeleton_skull` · `saddle` · `elytra` · `trident`

Flavor absurdity spotted along the way: **Excadrill (a drill) drops dirt**; Probopass drops flint.

## Proposal - three tiers, matched to mechanisms we already have

1. **Type-drops config additions** (zero code, `rituals.json`): gold nugget on steel/normal ·
   redstone on electric · glowstone on electric/fire · sponge on water · copper on electric.
2. **NEW `speciesDrops` overlay** (small build - `DropsBridge.dropTableFor` was designed as this hook):
   per-species extra entries injected into the harvest roll. Starter set: **Gimmighoul/Gholdengo → raw
   gold**, Meowth line nugget buff, Excadrill/Probopass → raw iron.
3. **Ritual/gacha tier for trophies** (config-only, pity-gated): shulker_shell, wither_skeleton_skull,
   totem_of_undying, saddle, name_tag, elytra, trident. These should NOT trickle; they're what the
   composition-gated gacha with pity was built for.

## OPEN QUESTIONS for Deuce (blocking build)

- Which tiers to build? (Claude recommends 1 + 2 as one session; 3 is a config-editing pass.)
- **Gold flavor**: Gholdengo-line-only (scarce + thematic) vs also a broad steel-type trickle?
- Are diamond/emerald scarcities intentional (leave alone) or gaps (patch)?
