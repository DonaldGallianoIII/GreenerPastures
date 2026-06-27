# 🎒 Greener Pastures — Item & Block Registry (for upgrade/recipe/art design)

_Mod id `greenerpastures`, Minecraft 1.21.1 Fabric. Authoritative as of 2026-06-25 (pulled from the registration code). Use this in the separate design repo; the main-repo Claude will map your ideas back to these IDs._

## 🧩 Items (everything currently registered)
| Registry ID | Current display name | Intended lexicon name | What it is / does | Art? |
|---|---|---|---|---|
| `greenerpastures:pasture_wand` | Pasture Wand | **Notebook** | Right-click a pasture → opens the GUI (the unified Notebook: tabs + name + Kernel slot + Daemon node-graph). Stacks to 1. | ❌ missing-texture |
| `greenerpastures:breeding_upgrade_copper` | Copper Pasture Upgrade | **Copper Kernel** | Slotted into a pasture via the GUI. Sets breeding **pairs + functional slots**; also carries **augments as data**. **2 pairs / 2 slots.** | ❌ |
| `greenerpastures:breeding_upgrade_iron` | Iron Pasture Upgrade | Iron Kernel | **3 pairs / 3 slots** | ❌ |
| `greenerpastures:breeding_upgrade_gold` | Gold Pasture Upgrade | Gold Kernel | **4 pairs / 4 slots** | ❌ |
| `greenerpastures:breeding_upgrade_diamond` | Diamond Pasture Upgrade | Diamond Kernel | **5 pairs / 5 slots** | ❌ |
| `greenerpastures:breeding_upgrade_netherite` | Netherite Pasture Upgrade | Netherite Kernel | **6 pairs / 6 slots** | ❌ |
| `greenerpastures:breeding_upgrade_greener` | Greener Pasture Upgrade | Greener Kernel (top tier) | **8 pairs / 8 slots** — fills the whole pasture (8 pairs × 2 = 16 mons, the cap) | ❌ |
| `greenerpastures:augment_shiny` | Shiny Augment | augment ("Shiny Boost") | Slot with a Kernel at the **Compiler** → writes **+30% shiny-proc** onto the Kernel's `augments` data. **Consumed** on compile. | ❌ |
| `greenerpastures:compiler` | Compiler | Compiler | Block-item (places the Compiler block). | ❌ |
| `greenerpastures:shiny_egg_collector` | Shiny Egg Collector | — | Block-item (places the collector block). | ❌ |

_(Eggs themselves are Cobbreeding's items, not ours.)_

## 🧱 Blocks
| Registry ID | Display name | What it does | Art? |
|---|---|---|---|
| `greenerpastures:compiler` | Compiler | Right-click → bench GUI: **Kernel + augment → ▸ Compile → augmented Kernel** (augment consumed). Workstation, no storage. | ❌ |
| `greenerpastures:shiny_egg_collector` | Shiny Egg Collector | Vacuums shiny eggs from adjacent pasture/space into a chest. | ❌ |

## 📦 Data component (NOT an item — but central to upgrades)
- **`greenerpastures:augments`** — a data component carried by a Kernel (the `breeding_upgrade_*` item). v1 field: **`shiny`** = proc % (int 0–100). This is the "augments are DATA, not a separate item per tier" model. Authorable by hand: `/give @s greenerpastures:breeding_upgrade_copper[greenerpastures:augments={shiny:40}]`, or in-game via the Compiler.

## 🎰 Augment / shiny model (for designing MORE augments)
- **Shiny = bounded proc reroll** (additive, capped — never "×N"). Designed tier grid: copper **10%** → iron **20%** → gold **30%** → diamond **40%** (~0.4× a ×2 Masuda, the sweet spot) → netherite **50%** → emerald **60%** → custom **80–100%**. *(The current `augment_shiny` item = a flat 30%.)*
- **Future augments** (from the mockup, not built): **IV Floor**, **Nature Lock**, **Speed Tier** (egg rate — needs a cap), **🌲 Random Cut Forest** (anomaly-flag rare eggs). Each new augment = one field on the `augments` component + one branch in `AugmentType`.
- Augments apply at the **Compiler** (consume the augment item → write data onto the Kernel; idempotent / replace-in-place).

## 🍳 Crafting / recipes — WIDE OPEN
- **No recipes exist yet** — everything comes from the creative **Tools** tab or `/give`. Designing the recipe tree is exactly what this design pass is for.
- Recipes = vanilla **datapack JSON** (`data/greenerpastures/recipe/*.json`), shaped/shapeless — no code, fully datapack-overridable.
- **Parked idea — "surround-craft":** a blank upgrade in the **center** of a 3×3, surrounded by 8 metal blocks to tier up (copper→iron→gold→diamond→netherite→emerald→custom). Each tier-up = one shaped recipe (8 blocks = steep by design).

## 🌍 Economy context (so recipes feel right)
Target server is **survival, no shops, no vanilla mobs** — everything is hand-grinded (gold = mining, Poké Balls = apricorns). Assume hand-grinding; **steep costs are intentional** (they self-gate how many mega-pastures someone can run). Endgame tiers should lean on bred-egg materials (compressed shiny eggs) per the idle-loop idea.

## 🏷️ Lexicon (DESIGN AROUND THESE NAMES)
The item IDs still use old names; the **intended** lexicon (rename pending) is: **Notebook** (the tool/wand) · **Kernel** (the slotted upgrade) · **Compiler** (the apply-augments block) · **Daemon** (the node-graph GUI) · **augments** (data Compiled onto the Kernel) · **Dashboard** (analytics). Design new items/upgrades using Notebook/Kernel/augment language; main-repo Claude maps them to registry IDs.

## 🎨 Art needed (all currently render as missing-texture)
- **Items:** `pasture_wand` (Notebook), the 6 `breeding_upgrade_*` (Kernels — ideally a shared shape recolored per tier), `augment_shiny`, plus block-items `compiler` & `shiny_egg_collector`.
- **Blocks:** `compiler`, `shiny_egg_collector` (cube textures).
- Each = a **16×16 PNG** (transparent bg for items) + a small model JSON. Theme: dark, data-science / terminal aesthetic — palette `#0c0f14` bg, accents purple `#d56bff` / green `#4fd6a0` / blue `#5cc8ff` / amber `#ffb454`.
