# 🖥️ Notebook Console — Build Plan (task #36)

**Greenlit 2026-06-30.** The console is the mod's core UI: right-click the Notebook → tabbed data-science shell. This plan is the **compaction-safety net** — if context resets mid-build, resume from here + `NOTEBOOK_CONSOLE_SPEC.md` + `greener-pastures/design/design_reference/notebook-console.NOTES.md`.

## Resolved decisions (Deuce, 2026-06-30)
- **No EMC currency.** "Dark EMC" was just flavor for Data (the egg data is "dark"). Economy = **Data + GPU** only. The mock's status-bar EMC is dropped.
- **Harvester tab = ITEMS** (harvested mon-drops / loot), NOT eggs — the int-limit item warehouse (the "Storage" concept). The mock showed eggs; corrected to items.
- **Dashboard tab = STUB** for now ("coming soon"); built later.
- **Tabs:** Pastures · BioBank · Harvester (items) · Compiler · Augmenter · Dashboard (stub).

## Standing constraints
- **Logic-first:** MC-free cores → headless JUnit (no MC boot) → MC adapters → UI last. Mirror `DataAccount` (pure) + `DataStore` (PersistentState adapter).
- **UI = viewport PNG-skin + relative hitboxes** (vanilla DrawContext, no browser). Palette + per-tab schema in the NOTES digest.
- **Storage is player-bound** (survives losing the Notebook).
- **Observability:** every feature logs JSONL via `GpLog`.
- Build: `JAVA_HOME=/home/donaldgalliano/jdks/jdk-21.0.11+10 <repo>/greener-pastures/gradlew -p <repo>/greener-pastures build`. **Commit only when Deuce asks.**

## Build order
**Phase 0 — DONE (art + items):** all 32×32 item art in-game; `GpItems` registers `gpu` + 7 `data_disk_*` + `notebook`; `NotebookItem` (right-click pasture → wand GUI; air → console stub); full Kernel ladder. Jar `1734a6e`.

**Phase 1 — MC-free cores + tests  ✅ (NotebookStorage: 13 tests green)**
- [x] `notebook/NotebookStorage` — the int-limit item warehouse + `NotebookStorageTest` (13 tests).
- [~] ~~`PastureLinkSet`~~ **REMOVED** — redundant: `PastureData.owner` (the explicit claim) already IS the per-pasture ownership/link. "My pastures" = derive from `PastureRegistry` where `owner == me` (a tab-open scan, not hot-path).

**Phase 2 — persistence + link trigger  ✅**
- [x] `NotebookStore extends PersistentState` — per-player item warehouse + NBT (the harvested-loot store).
- [~] ~~`NotebookLinks`~~ **REMOVED** — redundant (see Phase 1); ownership lives on `PastureData.owner`.
- [x] **Link/unlink trigger = the "Link" button** in `PastureScreen` → `ClaimOperatorPayload` → the pre-existing `PastureClaim`/`onClaim`. **The claim = the Notebook link:** the owner collects the pasture's outputs into their Notebook + pays its tether. (Backend `owner` + `PastureClaim` + payload already existed from the June operator-claim work — only the button was missing.)
- ⚠️ Output-routing (owner's pasture outputs → their `NotebookStore`) is **Phase 3**; today the button only sets/releases ownership.

**Phase 3 — background network tick**  ← IN PROGRESS
- [x] **3a · Harvest (staples):** `PastureHarvest` (END_WORLD_TICK) sweeps owned pastures once/min → rolls drops (reuses `DropsBridge` + the exact drop-tether billing) → deposits into the owner's `NotebookStore`. Harvester block **gated off for owned pastures** (no double-dip). GpLog `notebook_harvest collect`.
- [ ] **3b · Rituals / type-drops on the network tick** — move the block's ritual pull-state to `PastureData` + run `customDrops` over owned pastures. (Owned pastures currently collect staples only; rituals still fire on the block for *unowned* pastures.)
- [ ] **3c · Render:** non-keeper eggs on owned pastures → owner's Data (migrate the Renderer block likewise).
- [ ] **3d · Retire the machine blocks** (Harvester/Renderer/BioBank/Compiler) once their ticks/UI fully live on the Notebook — a deliberate cleanup, not mid-build.

**Phase 4 — console Screen shell**  ✅ ENGINE DECIDED (Deuce, 2026-06-30): **owo-ui** (native; MCEF parked as fallback)
- Deuce loved the **Claude-chat JSX look** ("very science + tech based" = the goal); we reproduce it **natively in owo-ui**. His own `PORTING_WEB_UI.md` (3-pass research, same day) picks owo-ui as "the in-world workhorse" and **parks MCEF** (100–236 MB Chromium, no built-in JS bridge, **GL friction with Sodium+Iris — which his pack runs**). owo-lib is a tiny standard lib **already in his pack (~0 KB)**, its flexbox maps ~1:1 from the JSX's CSS flexbox, and **XML hot-reload (Ctrl+F5)** gives fast look-iteration. The specs' "PNG-skin" wording is superseded; the JSX is now the **design target to match** (layouts/schemas still hold).
- **Web→owo map** (from `PORTING_WEB_UI.md`): `display:flex`→`Containers.horizontal/verticalFlow`; `flex-grow`→`Sizing.expand()`; `width:100%`/`fit`/`Npx`→`Sizing.fill()`/`content()`/`fixed(N)`; `overflow:scroll`→`Containers.verticalScroll`; `background/border/shadow`→`.surface(Surface…)`; `onClick`→`.onPress()`/`.mouseDown()`; `transform`→`RenderEffect.transform` (Daemon pan/zoom); JSX markup→`assets/greenerpastures/owo_ui/*.xml`. Palette + per-tab schema: `design/design_reference/notebook-console.NOTES.md`.
- **MCEF fallback:** Deuce reserved *"if I don't like owo we change later."* The full MCEF `2.1.6-1.21.1` recipe (deps · browser-in-Screen · input · `CefMessageRouter`/`window.cefQuery` bridge · `mod://` jar-asset loading · gotchas) is **saved in `MCEF_RECIPE.md`** so a switch is a build task, not a re-research.
- [x] **Wire owo-lib** — `maven.wispforest.io` + `modImplementation "io.wispforest:owo-lib:0.12.15.4+1.21"` + `"depends": {"owo": ">=0.12.15"}`. Build green (resolves + all tests pass).
- [x] **`NotebookScreen extends BaseOwoScreen<FlowLayout>`** — shell built: window chrome (title bar w/ traffic-lights + kernel status · tab strip · `gp://` bar · content · status bar Data/GPU/Kernel/Daemon, **no EMC**), 6 tabs (BioBank·Harvester·Pastures·Compiler·Augmenter·Dashboard), working tab switching (recreate-screen on click). Opens client-side from `NotebookItem` via a `CONSOLE_OPENER` `Runnable` hook (set in `GreenerPasturesClient`, keeps client refs out of the common item). Build green. **owo gotcha logged:** `.gap()` is FlowLayout-only → call it on the typed var, not after `.surface()/.padding()` (which return `ParentComponent`).
- [ ] **In-game render check** (compiles ≠ renders) — deploy + eyeball vs the mock; tune palette/spacing (owo XML hot-reload once ported to a model, or edit constants + rebuild).
- [ ] Reusable component harness (the Augment Bench triptych base too) — lands with the Compiler/Augmenter tabs.

**Phase 5 — tabs**
> **Full interactive blueprint: `NOTEBOOK_INTERACTIVE_SPEC.md`** (2026-06-30) — the shared sync layer + per-tab reads/actions/backend-hooks + economy + build order + open questions, grounded in a 4-way backend inventory. **Build-readiness re-orders these:** Storage + Compiler backends are DONE (fast); Pastures medium (needs per-UUID walk + remote-link call); **Augmenter + BioBank need real BACKEND builds first** (Augmenter's GPU/slot/Data model is unbuilt; BioBank reads only 4 of ~13 stats). Slices 1–3 (sync+status → Storage → Compiler) are unblocked; 4–7 gate on Deuce's design forks (§7).
- [ ] Harvester (items warehouse grid, click-pull, pull-all) → NotebookStore.
- [ ] Pastures (list + 8-pair grid w/ status + progress) → pasture data + link set.
- [ ] Compiler (Daemon triptych) → existing `/gp daemon` backend.
- [ ] Augmenter (Kernel triptych + GPU) → needs the augment redesign (GPU reagent + slots; `KERNEL_AUGMENTER_SPEC.md`).
- [ ] BioBank (species accordion + full stats + search/sort) → BioBank persist backend.
- [ ] Dashboard — STUB.

**Phase 6 — economy wiring:** GPU + Data dual-cost on augments/upgrades; Data-disk read/write (the Notebook is the drive); capacity upgrade-gating.

## Key files / patterns
- Persistence to mirror: `economy/DataStore.java` (PersistentState, per-player map, NBT) + `DataAccount` (pure). `pasture/breeding/PastureRegistry.java` (dim→pos→PastureData).
- Items: `economy/GpItems.java` (gpu/disks/notebook), `pasture/breeding/NotebookItem.java` (console item).
- Pasture GUI open: `PastureWand.openMenu(sp, pos)` (reusable).
- New console package: `com.greenerpastures.notebook.*`.
- Palette + tab schemas: `design/design_reference/notebook-console.NOTES.md`.

## QA + test coverage (filled as built)
**Headless tests (green):** `NotebookStorageTest` (13) — the int-limit warehouse. Pre-existing `PastureClaim` + `TetherRuntime` tests cover the claim + drop-tether math the harvest reuses.

**Needs in-game QA — deploy jar `b7562ae`:**
| # | Check | How |
|---|---|---|
| N1 | **Link button** claims/releases a pasture | Open a pasture with the Notebook (or wand) → click **Link** → chat *"Linked — this pasture is yours…"*; click again → *"Unlinked…"*; a 2nd player clicking → *"owned by someone else"*. |
| N2 | **Block-free harvest** deposits to the owner | Own a pasture with tethered mons → wait ~1 IRL min → `~/gp-logs/latest.log` shows `notebook_harvest collect … items:N`. (Visible in the Storage tab once that UI exists.) |
| N3 | **No double-dip** with a Harvester block | Owned pasture + an adjacent Harvester block → block logs `skip_tick why:owned_uses_notebook`; only the network tick collects. |
| N4 | **Tether drain** on harvest | Owned pasture + a fed Drop-Rate/Yield tether → `tether drain … src:notebook_harvest` and the owner's Data ticks down. |
| N5 | **Daemon art** + **Link button placement** | Right-click Daemon → hungry↔happy swap + glint; eyeball the Link button isn't crowding Arrange/Daemon (right column, y+38). |
| N6 | **Notebook console shell renders** | Right-click the Notebook **in the air** → owo console opens; click each tab → active highlight + `gp://` path + blurb swap; ✕ closes. Eyeball palette/layout vs `design/design_reference/notebook-console.NOTES.md`. (Right-click a **pasture** still opens the wand menu.) |

**Deferred (tracked):** rituals on owned pastures (3b) · render→Data (3c) · block removal (3d).

## Resume pointer
If compacted mid-build: read this + `NOTEBOOK_CONSOLE_SPEC.md` + the NOTES digest. Check `greener-pastures/src/main/java/com/greenerpastures/notebook/` for what exists; run the build to see test state; continue at the first unchecked box.

**Status @ 2026-06-30 (post-compaction):** Phases 1–3a **committed on `main`** (`963671f`) + build green; item-art + block-free harvest foundation done. **UI ENGINE DECIDED — owo-ui** (native, flexbox-from-JSX, owo-lib already in the pack ~0 KB, XML hot-reload). Path there: I re-read the code (found the existing **GpCanvas**/`NotebookView`/`studioLive` system that already renders the Daemon graph in-game) and surfaced Deuce's own `PORTING_WEB_UI.md`, which parks MCEF (100–236 MB + no JS bridge + **Sodium/Iris GL friction on his rig**). Deuce briefly picked MCEF, then on seeing his own notes switched to owo — reserving *"if I don't like it we change later"* (**full MCEF recipe parked in `MCEF_RECIPE.md`**). **Shell BUILT + building green:** owo-lib wired; `NotebookScreen` shell (window chrome + 6 tabs + tab switching) opens from the Notebook item (air right-click). **Not yet eyeballed in-game** (compiles ≠ renders — QA N6). Uncommitted. Next: Phase 5 per-tab content + a server→client data-sync layer for the live status bar (Data/GPU/Kernel/Daemon are placeholders). The UI phases want Deuce's eyes, so surface progress but don't block.
