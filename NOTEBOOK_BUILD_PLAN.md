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

**Phase 4 — console Screen shell**  🚨 UI PIVOT (Deuce, 2026-06-30): **NOT PNG-skin**
- Deuce loved the **Claude-chat JSX look** ("very science + tech based" = the goal). Reproduce it **natively** — strong candidate **owo-ui** (its flexbox / component / surface model maps ~1:1 from the JSX's CSS flexbox; crisp, native-free, monospace + icons + hover states). **MCEF (real React) = last-resort.** → **DECIDE owo-ui vs MCEF FIRST**, then build. The specs' "PNG-skin" wording is superseded; the **layouts / schemas still hold.**
- [ ] `NotebookScreen` — the shell: title bar · tab strip · `gp://` command bar · status bar (Data + GPU, **no EMC**). Tab switching. Opened from `NotebookItem.use` (replace the console stub).
- [ ] The reusable component harness (owo-ui) — the Augment Bench base too. Palette + per-tab schemas: `design/design_reference/notebook-console.NOTES.md`.

**Phase 5 — tabs**
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

**Deferred (tracked):** rituals on owned pastures (3b) · render→Data (3c) · block removal (3d).

## Resume pointer
If compacted mid-build: read this + `NOTEBOOK_CONSOLE_SPEC.md` + the NOTES digest. Check `greener-pastures/src/main/java/com/greenerpastures/notebook/` for what exists; run the build to see test state; continue at the first unchecked box.

**Status @ compaction (2026-06-30 eve):** Phases 1–3a **committed on `main`** + build green. The item-art layer + block-free harvest foundation is done. **UI approach PIVOTED — NOT PNG-skin:** reproduce the Claude-chat JSX look natively via **owo-ui** (confirm owo-ui vs MCEF as the first UI step — see Phase 4). Next: Phase 4 (console shell in owo-ui), then the tabs. The UI phases want Deuce's eyes, so surface progress but don't block.
