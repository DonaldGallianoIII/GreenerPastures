# 🛡 Kernel Augmenter — feature + UI spec

From Deuce's GUI-Forge concept art (`Screenshot_645`). Sibling to `COMPILER_UI_SPEC.md` (the Daemon Compiler). Same PNG-skin + viewport pipeline. UI rule: `viewport-ui-principle`.

---

## 1 · One-liner
Redesign the `compiler` block into the **Kernel Augmenter**: slot a Kernel → pick an augment from a scrollable catalog → spend **1 GPU** (a single universal reagent) to apply it. Augments cost **slots**; the Kernel's **tier = its slot budget**. **Replaces the 7 separate `augment_*` items with one `gpu` item.**

## 2 · Why (the decisions that drove it)
- **One GPU sprite instead of 7 augment sprites** — Deuce's driver. The 7 augment *types* become menu entries, not items.
- **GPU = tangible compute cost** — gates the Apply button by inventory ("requires GPU ×1 (have 3)"). On-theme for a data-science mod.
- **Catalog = a scroll list** — reuses the Daemon Compiler's middle-panel pattern; new augments cost zero new art.

## 3 · Layout — three panels (from the concept art)
Title bar: **🛡 KERNEL AUGMENTER · `<context>`** + `×` close. Dark panel, green/mono "data science" theme.

```
┌──────────────────  🛡 KERNEL AUGMENTER · pasture  ───────────────────[×]┐
│  KERNEL        input │  AUGMENT      select │  AUGMENTED        output  │
│                      │                      │                          │
│   [ Kernel item ]    │  ▸ Shiny Catalyst  2 │   [ augmented Kernel ]    │
│                      │    Egg Tempo  ✓    1 │     (glow) augmented ×1   │
│   ‹ Iron Kernel ›    │    IV Forge        2 │  ──────────────────────   │
│                      │    Nature Lock     1 │   ⏱ Egg Tempo      1   ×   │
│   Slots used  1 / 3  │    Ability Splice  1 │                          │
│   ■ □ □               │    Masuda Lens     2 │                          │
│                      │    EV Primer       1 │                          │
│                      │    Form Inherit    1 │                          │
│                      │    Hatch Warmer    1 │                          │
│                      │    Gender Bias     1 │                          │
│                      │ ──────────────────── │                          │
│                      │  Shiny Catalyst    2 │                          │
│                      │  "Raises shiny…"     │                          │
│                      │  requires 🖥 GPU ×1   │                          │
│                      │       (have 3)  [+]  │                          │
│                      │   [   APPLY ▸   ]    │   Capacity   1 / 3 slots  │
└──────────────────────┴──────────────────────┴──────────────────────────┘
```

- **LEFT — KERNEL (input):** the real Kernel item in a slot; its tier label (`Iron Kernel`); **Slots used N / cap** with pip indicators. *(The `‹ ›` cycler is a mockup-preview affordance — in-game the tier is read from the inserted item, not cycled.)*
- **MIDDLE — AUGMENT (select):** scrollable catalog, each row = icon · name · **slot cost**; a ✓ marks already-applied. Selected augment shows description + **GPU requirement** + **APPLY**.
- **RIGHT — AUGMENTED (output):** the resulting Kernel (glows), the list of applied augments (each with a **× remove**), and **Capacity N / cap**.

## 4 · Augment catalog (from the mockup — *names + map need confirming*)
| Catalog name | Slots | Function | Backend | Needs a value? |
|---|---|---|---|---|
| Shiny Catalyst | 2 | shiny chance ↑ | ✅ `shiny` | no |
| Egg Tempo | 1 | egg/breeding rate | ✅ `speed` (or `drop_rate`?) | no |
| IV Forge | 2 | IV floor | ✅ `iv_floor` | no (level/floor?) |
| Nature Lock | 1 | force nature | ⚠️ `nature` (cmd-only) | **yes — which nature** |
| Ability Splice | 1 | hidden ability | ⚠️ `ability` (cmd-only) | maybe (HA on/off) |
| Masuda Lens | 2 | Masuda shiny odds | 🆕 **new** | no |
| EV Primer | 1 | EV spread | ✅ `ev` | **yes — the EV spread** |
| Form Inherit | 1 | regional/form inherit | 🆕 **new** | maybe |
| Hatch Warmer | 1 | faster hatching | 🆕 **new** | no |
| Gender Bias | 1 | gender ratio | 🆕 **new** | maybe (which gender) |

*Not shown (scrolled off or renamed): existing `enrichment`, `drop_rate`, `drop_yield`, `ball`, `egg_move`. Reconcile the full list before building.*

## 5 · Mechanics
- **Slot capacity = Kernel tier.** Iron = 3 in the art. Maps to the existing `BreedingTier` "functional-upgrade slots." Set the curve (copper few → greener many).
- **Each augment costs N slots** (1–2). Can't apply if it won't fit the remaining capacity.
- **Apply consumes GPU + Data** (1 GPU + a Data cost, per the mod-wide rule that every GPU action also burns Data); disabled if you lack either.
- **Remove** an applied augment → refunds its slots (and maybe the GPU? — decide).
- **Parameterized augments** (Nature/EV/Ability/Gender) carry a *value*, not just on/off → selecting them likely opens a sub-step (see open Q2).

## 6 · Static (PNG) vs dynamic (drawn)
- **Static skin:** 3-panel frame, headers (KERNEL/AUGMENT/AUGMENTED), title bar, the `+`/`›` dividers, button backgrounds (APPLY, the GPU chip, scrollbar troughs).
- **Dynamic (drawn on top):** the Kernel item icon + tier label + slot pips; the scrolling augment rows + ✓ marks + slot costs; selected description + GPU count; the output Kernel + applied list + capacity. (Leave all these as empty slots in the PNG.)

## 7 · How it maps to the existing code
- **Reuse:** `BreedingTier` (slot counts), `Augments` component (what a Kernel carries), `AugmentType`, the `CompilerBlock` shell.
- **Change:** retire the **7 `augment_*` items → one `gpu` reagent item**; flip `CompilerBlock` from "match the slotted augment item" → "pick a type from the list + burn 1 GPU + check slot capacity."
- **New backend:** the new functions (Masuda Lens, Form Inherit, Hatch Warmer, Gender Bias) + wire the cmd-only breeding-meta (nature/ability) into the catalog.
- **Likely absorbs:** the **EV allocator (#34)** and **breeding-meta picker (#35)** — they become augment entries (EV Primer / Nature Lock / Ability Splice) here, with sub-pickers for their values.

## 8 · Open decisions (need Deuce)
1. **Slots vs levels.** The art is slot-cost + capacity, no level stepper. Does slot-cost **replace** augment levels, or do augments still have a level (amplified by Soul Tethers) *and* cost slots?
2. **Parameterized augments.** Does picking Nature Lock / EV Primer / Gender Bias open a **sub-step** to choose the value (nature, EV spread, gender)? (This is where #34/#35 live.)
3. **Soul Tethers.** Confirmed to persist as rented amplifiers — cost is now **GPU (install) + Data (ongoing burn).** Open: do tethers share the Kernel's augment slots, or sit in their own?
4. **Catalog + names.** Confirm the final augment list, the flavor-name ↔ function map, and each slot cost.
5. **Naming.** Rename the `compiler` block → **"Augmenter"** so it stops colliding with the Daemon **"Compiler."**

## 9 · Build order (when greenlit)
Logic-first: (1) `gpu` item + retire `augment_*`; (2) slot-capacity + slot-cost model on `Augments`/`BreedingTier`, headless-tested; (3) `CompilerBlock` apply/remove + GPU consume; (4) the new augment functions; (5) the PNG-skin Augmenter screen + any sub-pickers.
