# Notebook Console — reference-mock digest

Build-oriented extraction of Deuce's full React/JSX console mock (2026-06-30 — *"inspiration, not exact, doesn't have everything"*). The runnable JSX is in the Claude-chat thread; this is the durable digest. Companions in this folder: `daemon-compiler.jsx`, `kernel-augmenter.jsx`.

## Shell chrome
- **Title bar:** traffic-light dots (red/amber/green) · `greener-pastures :: notebook` · `● kernel: connected` · ✕ close.
- **Tab strip:** browser-style tabs (icon + label), active tab raised; a `+` "add tab" affordance.
- **Command bar:** a `gp://…` address per tab (`gp://biobank`, `gp://harvester`, `gp://pastures`, `gp://daemon/compiler`, `gp://kernel/augmenter`) + a blinking cursor. Browser-address-bar vibe.
- **Status bar:** `Data <amber>` · `EMC <cyan>` · `⌬ <Kernel tier> ·N aug` · `● Daemon ON/OFF`.

## Palette (exact — match in the PNG skin)
`bg #070c11 · panel #0c141b/#0f1a22 · tabBar #0a1219 · inset #070e13 · line #1b2a36 · lineHi #2c4150 · green #43d869 · greenDim #1d6b38 · amber #f5b234 · cyan #5be0e0 · gold #ffd24a · text #cfe3da · muted #688089 · slot #060b0f · pink #f2a7c4`. Scrim behind panel `rgba(3,6,9,.72)`. Font: monospace.

## Tabs in the mock
`BioBank · Harvester · Pastures · Compiler · Augmenter` (+ add-tab).
**Reconcile with the spec's 6:** Compiler / Augmenter / BioBank / Pastures are realized directly. **"Harvester"** here = the withdrawable auto-collected-**eggs** grid (≈ the spec's **Storage** tab, but eggs not loot — confirm). **Dashboard** not mocked yet (icon already staged).

## Per-tab
**Pastures** — left: pasture list (`184 total · showing 12`; ready-dot + `n/8`). Right: selected pasture = **8 pair slots** (2-col grid); each `parentA × parentB`, status `Breeding`(cyan)/`Ready`(green)/`Idle`(muted) + progress bar; empty = `+ add pair`. Footer: *keepers pass the filter into BioBank.*

**Harvester (≈ Storage)** — 12-col grid of auto-collected **egg stacks** (species-tinted egg + count); **click a stack → pull one**; **Pull all**. *eggs auto-collected from all pastures · colour = species.*

**BioBank** — species **accordion** (expand → entries). Header: search box + **sort** (IV total / per-stat HP…Spe / Shiny) + `N kept`. Per **entry**: shiny★ · gender (♀ pink / ♂ cyan) · **IV** chips (31 = green) · **EV** chips (252 = amber) · nature · ability (`*` = hidden) · **Tera** (type-colored) · ball · `OT` · **moves** chips. This *is* the two-level browser (BUG-007), now with full competitive stats.

**Compiler** — the triptych per `COMPILER_UI_SPEC.md`: DAEMON (happy/mad sprite by ON/OFF · Data · status · `installed n/32`) → EFFECT (15-buff list · `L{tier}` · cost/s · `− tier +` · INSTALL) → LOADOUT (installed rows · **Total drain Data/s** · runtime@Data · **Power ON/OFF**). Live Data drain ticks.

**Augmenter** — the triptych per `KERNEL_AUGMENTER_SPEC.md`: KERNEL (cycle tier `‹ ›` · **slots used/cap** + pips; cap = tierIdx+1, base 1 → diamond 5) + AUGMENT (10 augments · `n slot(s)` · ✓ if applied · requires **GPU ×1 (have n)** · APPLY gated by slots + GPU + no-dupes) → AUGMENTED (result kernel ×n · applied rows · **Capacity n/cap**).

## New / to confirm
- **EMC** currency in the status bar (cyan) — a second currency beyond Data/GPU? Ties to the dark-economy design. Confirm role or drop.
- **Harvester vs Storage** — mock's Harvester is *eggs*; the spec's Storage is harvested **loot/drops** (int-limit warehouse). One tab (eggs) or two (eggs + drops)?
- **Dashboard** — not mocked; still planned (icon staged).
- All augment/buff/slot-cost/Data numbers in the mock are placeholders (per the asset README).
