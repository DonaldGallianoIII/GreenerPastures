# ⌬ Daemon Compiler — UI spec (for the design pass)

Reference for the design-image tool. We then port it in-game as a **viewport-scaled PNG skin + relative hitboxes** (no embedded browser). UI rule: `viewport-ui-principle` (all UI viewport-based).

---

## 1 · What the Compiler does (function)
The Daemon is an inventory item that grants buffs while **ON + fed** (Data > 0). You don't get the whole suite for free — you **compile** exactly the buffs you want onto it, each at a tier, and you pay Data/s for **only what you install**. This screen is where you assemble that loadout.

Backend already exists (BUG-004): `/gp daemon set <buff> <tier> | list | clear | on | off`. **The UI is a visual front-end over that — no new logic.**

What the screen exposes:
- Pick from the ~15 supported buffs: Fortune, Auto-Smelt, Vein-Miner, XP Boost, Potion Duration, Looting, Lure, Luck of the Sea, Frost Walker, Respiration, Swift Sneak, Feather Falling, Haste, Saturation, Magnet.
- Each installs at a **tier** (most 1–3; Frost Walker 1–2). Up to **32** buffs.
- Each installed buff costs `tier × costPerSec` Data; the bill is the **sum**.
- Toggle the whole Daemon **ON/OFF** (ON = enchant glint; runs from anywhere in your inventory while fed).

---

## 2 · Minecraft context
- Full-screen GUI drawn **over the dimmed game world** (MC darkens the backdrop behind the panel).
- It is **one centered panel** — not the vanilla inventory chrome. No hotbar shown.
- Renders **native** (vanilla DrawContext): a **PNG skin scaled to the viewport + clickable hitboxes at relative coords** on top. No browser, no Chromium.
- Aesthetic: **"A Data Science Mod"** — dark terminal / circuit-board panel, crisp, a **Greener-Pastures green** accent for ON / positive, **amber** for Data cost/drain. Reads naturally beside MC's dark GUIs, a touch techier.

---

## 3 · Space / dimensions (viewport-based)
- Panel fills a **centered ~80% × ~62%** of the player's window — scales with the viewport, never monitor-size-dependent.
- **Design the image at a 16:10 reference canvas (e.g., 1600 × 1000 px)**, dark/transparent background; we scale-to-viewport at runtime.
- Inner layout = **three columns, a left→right pipeline**, middle widest (it's the work area):

```
┌───────────────────────  ⌬ DAEMON COMPILER  ──────────────────────────[×]┐
│  DAEMON          │          EFFECT               │       LOADOUT         │
│  (input ~28%)  → │      (work area ~40%)      →  │    (outcome ~32%)     │
│                  │                               │                       │
│  [ item slot ]   │  ▾ buff catalog (scroll)      │  installed (scroll)   │
│   glint if ON    │   ▫ Fortune        1.5/s      │   Feather Falling +3  ×│
│                  │   ▫ Auto-Smelt     2.0/s      │   Fortune         +3  ×│
│  Data: 48,210    │   ▫ Feather Fall…  0.25/s     │   …                   │
│  ● ON            │  ───────────────────────────  │  ───────────────────  │
│  Installed 3/32  │  “Reduces fall damage.”       │  Total: 4.25 Data/s   │
│                  │   −  [ 3 ]  +     0.75/s       │  ~9m at this drain    │
│                  │      [ Install ▸ ]            │   [    ON / OFF    ]   │
└──────────────────┴───────────────────────────────┴───────────────────────┘
```

---

## 4 · The three zones

### LEFT — `DAEMON` (the input item)
- One large **item slot** showing the Daemon being programmed (item icon, glint if ON).
- Live readouts beneath:
  - **Data balance** (ticks live, amber) — `Data: 48,210`
  - **ON / OFF lamp** (green = on)
  - **Installed** — `3 / 32`
- The subject everything else acts on.

### MIDDLE — `EFFECT` (what you're applying)
- A **scrollable catalog** of the buffs: each row = icon · name · per-tier Data/s.
- Selecting one opens its **detail**:
  - One-line description of what it does.
  - A **tier stepper** `−  [ 3 ]  +` (clamped to that buff's max).
  - The **Data/s** this line costs at the chosen tier (amber).
  - **[ Install ▸ ]** → writes it into the loadout (appears on the right).
- The active editing zone — "the effect being applied."

### RIGHT — `LOADOUT` (the outcome)
- A **scrollable list** of installed buffs: row = name · tier · Data/s · **[ × ] remove**.
- **Total drain** — summed Data/s (amber, bold) — the bill.
- **Runtime @ current Data** — optional estimate (`~9m at this drain`).
- The big **ON / OFF switch** (green when ON) — flips the whole Daemon; left lamp/glint mirrors it.
- What the Daemon *becomes*.

---

## 5 · Static (bake into PNG) vs dynamic (draw in slots)
**Static — bake into the design image:**
- 3-column frame, borders, title bar, the `→ →` arrows.
- Zone headers `DAEMON · EFFECT · LOADOUT`.
- Button **backgrounds**: Install, ON/OFF switch track, ± stepper frames, scrollbar troughs.
- Circuit / etch decoration.

**Dynamic — drawn on top at runtime (leave as flat placeholder rectangles in the image):**
- Left: Daemon icon, live Data number, ON/OFF lamp, `N/32`.
- Middle: the buff rows (scrolling), selected description, tier number, per-tier cost.
- Right: installed-loadout rows (scrolling), total drain number, switch knob position.

> For the mock, fill placeholders with **example data** (Feather Falling +3, total 4.25 Data/s) to visualize — just know those regions get redrawn live.

---

## 6 · Hitboxes (relative — where clicks land)
- Top-right **× close**.
- Middle: each buff **row** (select) · **− / +** tier · **Install** · list **scrollbar**.
- Right: each row's **× remove** · loadout **scrollbar** · the big **ON/OFF switch**.

---

## 7 · How it opens (recommended)
**Sneak + right-click** the Daemon opens this screen; plain right-click keeps the quick ON/OFF toggle. It operates on that held Daemon — no inventory grid needed. (Folding it into the existing Compiler *block* is a possible later move; holding the item is the simplest first cut.)

---

## 8 · One-paragraph prompt for the image tool
> A Minecraft-style full-screen GUI panel, 16:10, dark circuit-board "data science" theme with green and amber accents. Three columns separated by small right-pointing arrows, with a title bar reading "⌬ DAEMON COMPILER" and an × at top right. Left column header "DAEMON": a large item slot holding a glowing artifact icon, and below it live readouts — "Data: 48,210", a green ON lamp, "3/32 installed". Middle column header "EFFECT" (widest): a scrollable list of buff rows each with an icon, a name and a small per-second cost; below it a selected buff showing a one-line description, a "− 3 +" tier stepper, a "0.75/s" cost, and an "Install ▸" button. Right column header "LOADOUT": a scrollable list of installed buffs each with a name, a level, a cost and a small × remove; a bold "Total: 4.25 Data/s"; a faint "~9m at this drain"; and a large green ON/OFF switch at the bottom. Crisp, slightly techier than vanilla Minecraft, dark translucent background behind the panel. Leave the list areas as clean empty rectangles.
