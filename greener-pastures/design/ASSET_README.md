# Greener Pastures — A Data Science Mod · Asset Package

Item textures, menu art, and UI design references for a Cobblemon-adjacent
Minecraft mod. Hand this to Claude Code as the art + design starting point.

## Conventions
- **Item textures are 32×32 PNG** with transparent backgrounds (double vanilla's
  16×16; Minecraft renders these fine). Nearest-neighbor / pixel-art.
- Filenames are already snake_case and resource-location-safe. Drop `textures/`
  into `src/main/resources/assets/<modid>/`.
- Every variant set (kernels, data disks, daemon states) is **pixel-registered** —
  identical bounding boxes — so swapping textures in a slot never shifts the icon.

## ⚠️ Not yet decided (implementation TODOs)
- **MC version + mod loader** — NOT pinned. Registration, block entities, and
  model/predicate JSON differ across 1.20 → 1.21 and Fabric vs NeoForge. Confirm
  before wiring code.
- **Augment list, slot costs, and Data values** in the UI mocks are PLACEHOLDERS.
- **Data-disk conversion ladder** (Byte→…→TeraByte) is undecided (real 1000× vs
  game-balanced).
- Blocks: undecided whether the machines are placed blocks or item-opened screens.

---

## textures/item/

### daemon/  — the Daemon (inventory item, grants buffs while ON + fed)
- `true_daemon_happy_on.png` — **ON / fed** state (green, happy).
- `mad_daemon_off_hungry.png` — **OFF / starved** state (red, mad).
- Behavior: toggled ON/OFF; while ON it drains **Data** per second and grants the
  buffs compiled onto it. When Data hits 0 it starves back to OFF (red). Texture
  swaps on the ON/OFF flag (item property / predicate). Backend command exists:
  `/gp daemon set <buff> <tier> | list | clear | on | off`.
- Its loadout is assembled in the **Daemon Compiler** screen (see design_reference).

### kernel/  — Kernels (go inside Pasture; hold augments)
- `kernel.png`, `copper_kernel.png`, `iron_kernel.png`, `golden_kernel.png`,
  `diamond_kernel.png` — a material-tier ladder.
- Design: **tier = augment capacity** (base 1 slot → diamond 5, working proposal).
- Augmented via the **Kernel Augmenter** screen.

### augment/  — the augment ingredient
- `augment_gpu.png` — a GPU. This is the **single "augment" item** consumed as the
  recipe cost when applying any augment to a kernel. The augment *type* is chosen
  from a list in the Augmenter UI; the GPU is just the required fuel. One sprite
  powers the whole augment system (deliberate — avoids per-augment art).

### data_disk/  — Data currency denominations (floppy disks)
- `blank_data_disk.png` — empty / unwritten.
- `byte_` → `kilobyte_` → `megabyte_` → `gigabyte_` → `terabyte_data_disk.png` —
  ascending Data denominations (the physical form of the Daemon's `Data` balance).
- `team_rocket_forbidden_data_disk.png` — illicit high-value variant (dark-economy
  flavor). Effect TBD.

### misc/
- `soul_tether.png` — item used to **augment Pasture blocks** (separate system from
  kernel augments; not yet designed).
- `jupyter_notebook.png` — likely the **wiki-book / guide item** that opens the
  main menu (see menu_art). On-theme "data science" tool. Role TBD.

## menu_art/  — main-menu splash (transparent PNGs unless noted)
- `logo_greener_pastures.png` — title logo (green matrix glow).
- `background_pastures.png` — full-bleed landscape (opaque JPEG-source; the
  eggshell-graveyard field). Menu background.
- `frame_purple.png`, `dragon_crest.png` — an alternate ornate-frame direction
  (currently unused by the splash layout).
- `illus_charmander.png`, `illus_chansey.png` — menu illustration sprites.

## design_reference/  — interactive UI mocks (React) — NOT shipping code
These are visual/behavioral specs. In-game these become **viewport-scaled native
GUIs (vanilla DrawContext): a PNG skin + relative hitboxes**, no embedded browser.
- `daemon-compiler.jsx` — Daemon buff-loadout screen. 3 columns: DAEMON (item +
  live Data + ON/OFF) → EFFECT (buff catalog + tier stepper + install) → LOADOUT
  (installed buffs + total Data/s drain + ON/OFF switch). Data ticks live.
- `kernel-augmenter.jsx` — anvil/enchant-table pattern. KERNEL (tiered, capacity)
  + AUGMENT (scrollable enchant list; each Apply consumes 1 GPU) → AUGMENTED
  (result kernel + applied list + capacity). Gates: slots, GPU present, no dupes.
- `gui-forge-loaded.jsx` — main-menu splash composition (background + logo +
  illustrations), and the underlying layered-PNG layout tool.

## _source/
- `Daemon_Ghost.pxo` — Pixelorama source for the daemon.
- `SUPERSEDED_*` — earlier daemon sprites, replaced by the daemon/ set. Kept for
  reference; do not ship.
- `WIP_cracked_egg_88px.png` — 88×88, unfinished; not a final 32×32 item.
