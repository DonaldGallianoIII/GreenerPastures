# 🕸️ Pasture Visual-Scripting UI (node graph) — PARKED (Arrangement v2)

> **Scope status: post-v1.** This is the *evolution* of the Breeding Arrangement board into an Unreal-Blueprints / ComfyUI-style node graph. Ship **Upgraded Pastures v1** (bucket board + shiny proc) first. Pegged 2026-06-25 (Deuce); he's R&D-ing the *feel* via a Claude-chat mockup and will share it (as with `GreenerPastures-Layout.html`).

## 🏷️ Lexicon — the "Data Science Mod" theming (LOCKED 2026-06-25)
The whole feature set maps onto a coherent, OS-accurate metaphor (daemons run on the kernel):
- **Notebook** (Jupyter Notebook) = the **tool item**; right-click a pasture to open the GUI. Where you author. (Renames `pasture_wand`.)
- **Kernel** = the **item you craft + slot into the pasture**; carries the tier + **all augments as DATA** (anvil-applied), not separate slot items. The runtime/engine. (Renames the `breeding_upgrade` line.)
- **Daemon** = the **visual scripting layer** — the node graph that *is* your pasture's running breeding program. You program the Daemon; it runs on the Kernel. (A daemon = a background process = an auto-breeding pasture.)
- **Compiler** = the **custom block** where you apply augments to the Kernel ("write in the Notebook → **Compile** → run on the Kernel"). Its own combine GUI: Kernel + augment item → augmented Kernel. (Custom block, not the vanilla anvil — full control over GUI + cost.)
- **Augments** = the imports/packages/functions you Compile onto the Kernel.
- **Dashboard** = the analytics screen — **Plots** tab (charts: eggs/hr, shiny-rate-over-time, total voided) + **Console** tab (the egg/void **logs**, `stdout`/`stderr` — the trust feature).
- **Filters / Pipeline** = the egg-processing chain inside the Daemon.
- 🌲 **Random Cut Forest** *(easter egg, future)* = an anomaly-detection pass over the egg log that flags rare eggs (shiny / perfect IV) as statistical outliers in the Dashboard: *"anomaly detected — ✨shiny✨, score 0.99."* Real anomaly detection on real data, purely to announce a good egg.
- **Implication:** the pasture GUI can collapse to a single **Kernel slot + the Daemon view** (augments are data on the Kernel) — cleaner than the current functional-slot grid. A #14/refactor note; doesn't disturb the tested build.

## 🖼️ Notebook mockup ARRIVED (2026-06-25) — the real GUI → `mockups/GreenerPasturesNotebook.jsx`
Deuce's Claude-chat React mockup landed. It's the **canonical port reference** (full code saved in the repo). It's *more* coherent than the sketch above and confirms the lexicon. Structure: **one `pasture.ipynb` window** with a live **Kernel · running** status and **three tabs** — `Daemon` · `Dashboard` · `Compiler`.

**🆕 Two new locked concepts:**
- **Thread** = a **locked, named breeding-pair pipeline**. The Daemon hosts a *strip of thread tabs* (`pair-1`, `pair-2`, …); each thread is its own node graph; the pasture runs many concurrently. **A Thread = our pair/bucket, richer.** Lock = freeze + name it (read-only until unlocked). Maps directly onto `PastureData.pairings` (bucket → thread).
- **import vs call** = the relationship between Compiler and Daemon. **Compiler = `import`**: pip-install an augment *package* onto the Kernel (`shiny-boost==2.0`), making it available. **Daemon augment node = `call`**: a thread invokes a **chosen SUBSET of that augment's props** (checkboxes: e.g. "2× shiny rate" ✓, "sparkle in log" ✗). So the Kernel is the environment with packages installed; threads call into it per-pipeline. Elegant + very on-brand.

**Node/port pipeline (the Daemon graph), shape-coded for colorblind safety (◆=pair, ▶=eggs/pass, ✕=void):**
`unit` (a tethered mon: pair-in ◀, pair-out ▶, eggs-out ▼) → wire two units' pair ports = a breeding pair → eggs flow ▶ into `augment` nodes (eggs→eggs) → into a `filter` node (IV ranges per stat + nature + shiny gate → **pass ▶** / **void ✕**) → `collection` (kept, counter) / `void bin` (voided, counter). Pan canvas, drag node heads, drag port→port to wire, hover-wire to delete. Wire-kind compat: pair→pair, flow→flow, pass→keep, void→discard.

**Compiler tab** = library (left, pip-style packages: `shiny-boost==2.0` ✨, `iv-floor==1.3` 📈, `nature-lock==0.9` 🔒, `speed-tier==3.0` ⚡, `rcf-anomaly==0.1` 🌲 *experimental*) + a **bench**: `Kernel` slot **+** `Augment` slot **→** `Augmented Kernel`, with a **`▸ Compile`** button that runs a fake **pip-install progress log** ("Collecting… Building wheel… Successfully compiled shiny-boost==2.0 onto Tier II Kernel"). RCF arms with "🌲 anomaly detector armed."

**Dashboard tab** = **Plots** (eggs/hr, shiny_rate/1k, accepted-vs-voided **donut**, iv_total **histogram** — all `np.`-flavored) + **Console** (live terminal feed: `egg produced …` / `FILTER: … → voided` / `✓ accepted … → Collection` / `🌲 RandomCutForest: anomaly — ✨shiny✨ (score 0.99)`). **The Console IS the void log** (trust feature) **and the RCF easter egg**, realized.

### Port map — how each piece lands on the backend we already have
| Mockup piece | Backend | Status |
|---|---|---|
| **Thread** (named pair pipeline) | `PastureData.pairings` (bucket→thread) | ✅ exists (bucket board) |
| **Compiler `import`** (augment onto Kernel) | `greenerpastures:augments` data component | ✅ slice A reads it — **Compiler is the missing writer** |
| **Augment node `call`** (per-thread prop subset) | per-thread augment application | 🔜 future; v1 = whole-Kernel augment (slice A) |
| **Filter node** (IV/nature/shiny → pass/void) | read egg props after `chooseEgg`, route keep/void, emit `egg_voided` | 🔜 easy + isolated (no mixin) = task #17 reborn |
| **Collection / Void bin** | the pasture's **one shared** egg inventory + counters | ⚠️ MC pasture has ONE egg inv → Collection = shared, not per-thread storage |
| **Console feed** | `events.jsonl` (`egg_laid`/`egg_voided`) tailed | 🔜 task #6 (the void log) |
| **Plots** | analytics aggregation over `events.jsonl` | 🔜 task #6 |
| **🌲 RCF** | anomaly score over the egg log → tag in Console | 🔜 parked easter egg (#6+) |

### ⚠️ Decisions the mockup forces (resolve before/at porting)
1. **"Shiny Boost ×2 / 2× shiny rate" wording contradicts our LOCKED additive-proc rule** (IDLE_BREEDING_IDEA: never say ×N — multiplicative reads as explosive on boosted servers; our backend is a **bounded proc**, ×(1+proc) capped). **Recommend** the Compiler label augments as **"+N% shiny reroll" / "+proc"**, not "×2". (Flavor "×" is fine only if the tooltip makes clear it's a capped reroll — but rewording is safer + truer.)
2. **Compiler: block vs tab.** Locked design = Compiler is a **BLOCK with material cost** (surround-craft economy self-gates scale). Mockup shows it as a *tab* with a Kernel+Augment **slot bench**. **Reconcile:** the Compiler tab = the Compiler **block's** GUI (you open it by using the block); the **Augment slot takes a crafted augment item** → preserves the item-cost gate. Keep the block.
3. **Speed augment ("×3 egg rate")** needs the same cap/config safety as shiny (shortens our `nextBreedingInterval`). Future + balance pass.

### Recommended port order (by backend-readiness + contained-ness)
1. **Compiler FIRST** — smallest tab (slots + button + log), and it's the *writer* for the `augments` component slice A already **reads** → completes the shiny loop, kills the hand-`/give` step. Lowest risk, highest leverage. (= the GUI half of #14 slice B.)
2. **Daemon node-graph SECOND** — the crown jewel; absorbs the bucket board (#16) + the egg culler (#17) into one system. Biggest lift (bespoke canvas/wire/port widgets in a Fabric `Screen` — all doable, the mockup geometry ports 1:1, but multi-day).
3. **Dashboard THIRD** — needs analytics aggregation (#6); Console = the void log; Plots = the charts; RCF lands here.

## The vision (Deuce's words, distilled)
A visual-scripting layer *inside* the pasture GUI:
- **Auto-generated nodes** — read which units are tethered in the pasture, spawn a container/node per unit on the canvas.
- **Free-form canvas** — drag each node anywhere; it stays there (persisted layout).
- **Edge connectors / wires** — each node has little edge nodes (the blue/yellow/red circles in Blueprints/ComfyUI). Drag from Ditto's node to Charmander's node → a **wire = a breeding pair.**
- **Filter nodes** — e.g. an "IV ≥ 31" filter wired into the flow: an egg is produced, checked against the filter, and **voided before it reaches the collection container** if it fails. You're *visually building an egg filter* inside your pasture.

## How it maps onto what we already have (this is why it's tractable)
- **Wires → `PastureData.pairings`.** A wire between two mons *is* a pair. Same data model as the bucket board — the node graph is just a richer UI over the same server-side pairing data. The bucket board (and the #16 screen-merge) are the stepping stones.
- **Filter nodes → the egg-culler, reborn.** Instead of a separate culler item (#17), *filtering becomes graph nodes* between the pairs and the collection. The #17 rework could BE this — one unified system.
- **Backend filtering is easy + isolated.** After Cobbreeding's `chooseEgg` builds the egg, IVs/nature/shiny are already set (`calcStats` etc.). So in `CobbreedingBridge`/`MultiPairBreeder` we can read the egg's IVs and simply **not `addEgg`** if it fails the filter. No mixin, fully version-guarded. The hard part is purely the GUI, not the mechanics.

## What's new vs the bucket board
- **Free-form node positions** become persisted state (per pasture, per mon — a small position map in `PastureData`). We moved *away* from positions for the bucket model on purpose; the node graph brings them back deliberately, because layout *is* the UX here.
- **Wire rendering + edge-node hit-detection + a pan/zoom-ish canvas** — the real R&D surface (this is what the mockup is for).
- **Filter node types + a tiny node-eval pass** server-side (IV/shiny/nature/species gates → keep/void).

## Scope placement
- **v1 (now):** bucket board + shiny proc upgrade. Ship it.
- **Arrangement v2 (this doc):** node graph replaces/absorbs the bucket board + the egg culler. A focused milestone after v1, once the mockup nails the feel.
- Build order when un-parked: (1) node canvas + auto-nodes + drag/persist positions, (2) wires → pairings, (3) filter nodes + the server-side keep/void pass.

## 🔒 Trust requirement — the void log (Deuce, non-negotiable)
Silent voiding = players think it's broken ("my pastures haven't pooped an egg in hours, it's bugged"). So **filtering must be observable**, and this is *literally the mod's "Data Science" thesis*:
- **Every void is a logged event.** When the filter discards an egg, emit `Analytics.record(world, Event.of("egg_voided") ...)` with **species, the egg's actual IVs/nature/shiny, which filter rejected it, and the pasture pos** — same `events.jsonl` we already write, same pipeline as `egg_laid`.
- **Player-facing in-game log** (the ask) — not just the JSONL file. A **per-pasture Log view** (a tab in the wand GUI, or a "Log" output node on the canvas) showing the recent feed: *"Pair 3 → Charmander egg (IVs 30/31/31/31/31/31) → VOIDED (failed IV≥31)"* and *"… → KEPT"*. So "no eggs for hours" becomes visible proof: **"produced 47, voided 46 by IV≥31, kept 1."**
- **Bonus:** the void counter is also a *flex stat* — "voided 12,400 imperfect eggs for this 6IV" fits the idle/data-science brand (a satisfying number, not just trust).
- This makes **Task 6 (analytics dashboards) load-bearing for the filter feature** — they ship together. The dashboard/log is the player-facing window into data the mod already collects.

## Arrived ✅
The mockup landed 2026-06-25 → `mockups/GreenerPasturesNotebook.jsx` (analysed above). Backend (pairings + the `augments` component + the IV-filter path) is ready to wire up. **Recommended next port: the Compiler** (writes the `augments` component that slice A already reads) — see the port-order list above.
