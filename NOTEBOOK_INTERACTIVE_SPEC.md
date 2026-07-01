# 🖥 Notebook Console — Interactive Spec (making everything work)

**Greenlit 2026-06-30** (Deuce: *"spec out making everything interactable… we can change the shell later if I don't like it"*). This is the blueprint for turning the owo-ui console **shell** (built, Phase 4) into a fully functional data-science console: a **live status bar + six working tabs**, all riding one shared **server↔client sync layer**.

Companions: `NOTEBOOK_CONSOLE_SPEC.md` (layouts) · `design/design_reference/notebook-console.NOTES.md` (palette + per-tab schema) · `NOTEBOOK_BUILD_PLAN.md` (phasing) · `KERNEL_AUGMENTER_SPEC.md` · `COMPILER_UI_SPEC.md`. The chrome may be re-skinned later (owo makes that cheap) — this spec is about **behavior**, not final pixels.

---

## 0. Where we are
- **Shell built + green:** `NotebookScreen extends BaseOwoScreen<FlowLayout>` — window chrome + 6 tabs + tab switching, opens client-side from the Notebook item. **Everything it shows is a placeholder; nothing reads live data.**
- **The engine behind it is alive + committed** (`963671f`): the `PastureHarvest` tick drops loot into each owner's `NotebookStore`; `DataStore`, the Daemon, and tethers all work. The console just doesn't *read* any of it yet.
- **This spec = the reading/acting layer** that connects the live shell to the live engine.

---

## 1. Economy model (the ground truth)
Confirmed from the backend inventory — this shapes every tab:

- **Data = a per-player BALANCE.** `com.greenerpastures.economy.DataStore` (`PersistentState`, keyed by UUID on `World.OVERWORLD`). `DataStore.get(server)` → `balanceOf(uuid)` (read, no-create) · `tryDebit(uuid, n)` (spend iff affordable) · `credit(uuid, n)` (grant).
- **GPU + the 7 data-disks = PHYSICAL inventory items** (`GpItems.GPU`, `GpItems.DISK_*`), **NOT balances.** A player's "GPU count" = `player.getInventory().count(GpItems.GPU)` server-side; **spending GPU = removing item stacks** from the inventory. Same for disks. (Only Data is abstract; GPU is tangible loot you craft + hold.)
- **Dual-cost rule (Deuce, standing):** *every GPU-consuming action ALSO costs Data.* So APPLY-augment / INSTALL-buff / UPGRADE-tab each **debit Data (`tryDebit`) AND consume GPU item(s) from inventory** — both gates must pass atomically or the action rejects (refund nothing because nothing was taken).
- **Data disks = NOT built** (art-only today; denominations blank→byte→kilo→mega→giga→tera→rocket imply values, none coded). Writing Data→disk / reading disk→Data (the Notebook as the read/write drive) is **new work** — spec'd in §4, deferred past the first tabs.

---

## 2. Architecture — the sync layer (every tab rides this)
The console is a client `BaseOwoScreen` with **no** container and **no** vanilla sync, and **no S2C networking exists in the codebase yet** (only C2S, per `PastureNet`). So we build one small, reusable sync layer — the foundation all six tabs share. Mirror the `PastureNet` / `OpenPasturePayload` idiom exactly.

### 2.1 Pieces
- **`NotebookState` (client, static cache)** — the single source the screen renders from: **status** (Data, GPU count, Kernel tier + aug count, Daemon on/off) + the **active tab's data** (storage map / pasture list / biobank page / daemon loadout / kernel state). The screen NEVER touches server objects; it reads `NotebookState`.
- **C2S `NotebookRequestC2S(tab, arg?)`** — "send me tab *X*'s data" (on open · on tab switch · after an action). `arg` carries e.g. the expanded species (BioBank) or selected pasture.
- **C2S `NotebookActionC2S(action, args…)`** — a tagged action: `PULL_ONE · PULL_ALL · CLAIM_PASTURE · INSTALL_BUFF · SET_BUFF_TIER · REMOVE_BUFF · TOGGLE_DAEMON · APPLY_AUGMENT · UPGRADE_TAB · WRITE_DISK · READ_DISK`. Server validates (Data + GPU gates) → mutates → re-pushes.
- **S2C `NotebookStatusS2C`** — the always-on status-bar payload (`Data` long, `GPU` int, `kernelTier`, `kernelAug`, `daemonOn`). Pushed on open + after **every** action.
- **S2C `NotebookTabS2C`** — the **active** tab's data (a small family, or one tagged payload). Tab data can be large (BioBank) → **send only the active tab**, paged where needed (species list first, entries on expand). Never stream the whole world.

### 2.2 Flow
```
OPEN / SWITCH  → client sends NotebookRequestC2S(tab)
              → server gathers (DataStore + inventory + NotebookStore + PastureRegistry + BioBank + Daemon)
              → server sends NotebookStatusS2C + the tab's S2C
              → client updates NotebookState + rebuilds the screen
ACTION         → client sends NotebookActionC2S(action, args)
              → server validates Data + GPU gates → mutates → GpLog → re-sends Status + tab snapshot
              → client rebuilds
```

### 2.3 Registration idiom (copy `PastureNet`)
- **New common seam `NotebookNet.init()`** (called from `GreenerPastures.onInitialize`, after `GpItems.init()`):
  - `PayloadTypeRegistry.playC2S().register(NotebookRequestC2S.ID, CODEC)` + `ServerPlayNetworking.registerGlobalReceiver(NotebookRequestC2S.ID, NotebookNet::onRequest)`
  - same for `NotebookActionC2S`
  - `PayloadTypeRegistry.playS2C().register(NotebookStatusS2C.ID, CODEC)` (+ the tab S2C payloads)
- **Client init (`GreenerPasturesClient`):** `ClientPlayNetworking.registerGlobalReceiver(NotebookStatusS2C.ID, (p, ctx) -> ctx.client().execute(() -> { NotebookState.applyStatus(p); NotebookScreen.refreshIfOpen(); }))`, and likewise per tab payload.
- **Payload template:** `OpenPasturePayload` — a `record … implements CustomPayload` with `static final CustomPayload.Id<T> ID`, a `PacketCodec<RegistryByteBuf, T> CODEC = PacketCodec.tuple(...)`, and `getId()`.
- **Threading:** every receiver hops to its main thread (`server.execute(...)` / `ctx.client().execute(...)`); server actions re-validate reach/ownership like `PastureNet.onClaim`.

### 2.4 Screen refresh
`NotebookScreen.refreshIfOpen()` — if the current screen is a `NotebookScreen`, rebuild it from `NotebookState` (recreate with the same active tab, or re-run `build`). Keep the active tab; preserve scroll where practical (Phase-5 polish).

### 2.5 Logic-first + observability (house style)
- **Pure cores, headless-tested** (mirrors `DataAccount` / `NotebookStorage`): a `NotebookSnapshot` builder (server objects → plain DTO) and the **cost-gate validators** (Data + GPU affordability math) as MC-light JUnit units — no game boot.
- **Every action logs JSONL via `GpLog`** (observability rule): `action · player · dataCost · gpuCost · result`.

---

## 3. Per-tab specs
*(Drafting from the Pastures / BioBank / Compiler+Augmenter backend inventories — landing next. Each tab documents: **reads** · **actions** · **backend hooks** · **owo layout**.)*

### 3.1 Harvester / Storage — *the first slice (backed by the tested `NotebookStorage`)*
- **Reads:** `NotebookStore.get(server).storageOf(uuid).snapshot()` → `Map<itemId, count>`; per-item `capacity()`. Status: Data + GPU.
- **Actions:** `PULL_ONE(itemId)` and `PULL_ALL(itemId)` → `NotebookStore.withdraw(uuid, itemId, n)` → give the `ItemStack` to the player (drop overflow). Later: capacity `UPGRADE_TAB` (Data + GPU).
- **owo layout:** a `Containers.verticalScroll` of a `GridLayout` — each cell = `Components.item(new ItemStack(item))` + count label; `.mouseDown()` → `PULL_ONE`, shift → `PULL_ALL`; a "Pull all" button in the tab header. Species/id-tinted per the NOTES.
- *(Other tabs fill in when their inventories land.)*

**Build-readiness at a glance** (from the backend inventories — this re-orders the build):

| Tab | Backend today | Work to make it live |
|---|---|---|
| **Storage** | ✅ complete (`NotebookStorage`, tested) | sync + UI only — **easiest** |
| **Compiler** | ✅ complete (`/gp daemon` ops + `BuffResolver`) | sync + owo skin — **easy** |
| **Pastures** | 🟡 mostly (registry + `rosterOf` + claim) | per-UUID walk + payload + remote-link decision |
| **Augmenter** | 🔴 **backend unbuilt** (spec model missing) | build slot/GPU/Data/remove model *first*, then UI — **heavy** |
| **BioBank** | 🔴 **read-layer unbuilt** (reads 4 of ~13 stats) | extend `EggReader` + `entries()` + sort/search + addressing — **heaviest** |
| **Dashboard** | — | defer (evolve the HTML export / Option-B) |

### 3.2 Pastures  ✅ *BUILT — read-only snapshot monitor (Deuce §7.1)*
- **Model:** capture-on-open, not live enumeration. Opening a pasture in-world (`PastureWand.openMenu`) writes a `PastureSnapshot{name, dim, pos, tier, eggCount, pairs[]}` into a per-player `PastureSnapshotStore` (PersistentState). Each `pairs[]` entry is a preformatted `"A × B · Status"` (Status ∈ Breeding/Ready/Idle/Incomplete, derived at capture from `rosterOf` buckets + `isBreedingActivated` + `nextBreedTick`). The console reads these **remotely + read-only** — no console mutation; you change a pasture at the pasture.
- **Reads:** `PastureSnapshotStore.get(server).snapshotsOf(uuid)` → `NotebookPasturesS2C`. **Actions:** none from the console.
- **owo:** left = scroll list (name · eggs) with click-select; right = selected snapshot's pairs list (status color-coded) + header (name · tier · eggs · pairs) + "read-only — modify at the pasture" note.
- **Hooks:** `PastureWand.openMenu` (capture site), `CobbreedingBridge.rosterOf/isBreedingActivated`, `PastureData` (name / tier() / eggQueue / nextBreedTick), `MonEntry`.

### 3.3 BioBank  🔴 *heaviest — read-projection layer is missing*
- **Reads:** accordion top = `BioBankData.speciesCounts()`; drill = **`entries(species)` — MUST ADD** (`bySpecies` is private today); per-entry stats = a **NEW rich projection** from an **extended `EggReader`** (today it reads only species / shiny / IV-total / perfect-count).
- **Missing fields to build:** per-stat IVs(6), gender, EVs(6), nature, ability + hidden-flag, Tera, ball, OT, moves. The extraction seam **exists** — mirror `CobbreedingBridge`'s `PokemonProperties` getters into `EggReader.decodeUncached` → a new `BioEntry` record. (Eggs are **0-EV by design** → EV column reads 0 unless hatched mons are banked — Q3.)
- **Addressing fork:** `BioBankStore` is **block-addressed, not per-player** — "my kept mons" isn't a concept. Resolve via a **linked BioBank** (store its pos on the Notebook, like a pasture link) or aggregate owned banks — Q2.
- **Actions:** browse-only v1; later withdraw / send-to-Renderer / sort+search.
- **owo:** search box + sort (IV total / per-stat / shiny) + `N kept`; species accordion (`verticalScroll` of expandable rows); per-entry card = shiny★ · gender · IV chips (31=green) · EV chips (252=amber) · nature · ability(* hidden) · Tera(type color) · ball · OT · move chips.
- **Build state:** substrate solid; the **entire browse/projection/sort/sync layer is new** + `EggReader` extension + `entries()` accessor + addressing decision.

### 3.4 Compiler (Daemon)  ✅ *backend complete — the tab is a skin*
- **Reads:** catalog = `DaemonBuffs.supported()` → per `BuffId` `{id,label,category, cap = min(BuffConfig.settingOf(b).maxTier,3), costPerSec}`; the held Daemon's loadout = `DaemonItem.loadoutOf(stack)`; ON = `DaemonItem.isOn`; live totals = `BuffResolver.resolveLoadout(cfg, loadout.toLevels(), supported)` → `ResolvedBuffs.dataPerSecCeil()`; runtime@Data = `balance / dataPerSec`.
- **Actions:** `INSTALL_BUFF(id,tier)` / `SET_BUFF_TIER` / `REMOVE_BUFF(id)` → `held.set(DAEMON_LOADOUT, loadout.withLevel(id,tier))` (tier≤0 removes; validate `supported` + `cap`); `TOGGLE_DAEMON` → `DaemonItem.setOn`. Server-authoritative on the held main-hand Daemon (mirror `DaemonCommand`).
- **Hooks:** `BuffId`, `BuffConfig`/`BuffSetting`/`BuffSystem`, `DaemonBuffs.supported()`, `DaemonLoadout.withLevel/toLevels`, `DaemonItem`, `BuffResolver`→`ResolvedBuffs`, `DarkEconomy.DAEMON_LOADOUT/DAEMON_ON`, `DataStore`.
- **owo:** triptych — DAEMON (sprite ON/OFF · Data · installed n/32) → EFFECT (scroll `supported()`, each `L{tier}` · cost/s · `− tier +` · INSTALL) → LOADOUT (installed rows · total drain Data/s · runtime@Data · Power ON/OFF).
- **Build state:** backend done; tab = sync + owo skin. **Note:** installing a buff has **no upfront cost — the ongoing Data drain while ON *is* the cost** (so not a GPU/Data dual-cost action unless Deuce wants an upfront install fee — Q5). Naming trap: `DaemonController`/`DaemonScreen` = the pasture pair-graph, **not** this.

### 3.5 Augmenter (Kernel)  ✅ *BUILT — slot model (GPU/Data cost + new functions deferred per §7.5)*
- **Spec vs built:** `KERNEL_AUGMENTER_SPEC.md`'s model (1 **GPU** reagent + **Data** cost + per-augment **slot cost** + capacity = `BreedingTier.slots` + **remove/refund** + ~10 catalog + 4 new fns) is **UNBUILT**. Live = the old per-`AugmentItem` bench (`CompilerMenu`: consumes 1 augment item; no GPU/Data/slots/remove).
- **Backend to build first:** (1) per-augment **slot-cost** + capacity = `BreedingTier.slots` (tooltip-only today) + a **slots-used** tracker (derive from `Augments` × costs); (2) **apply gate** = slots-remaining ≥ cost AND `inv.count(GPU) ≥ 1` AND `DataStore.tryDebit(uuid, dataCost)` (**the dual-cost**) → `AugmentType.apply(kernel)` + consume 1 GPU + no-dupe; (3) **remove/refund**; (4) retire the 7 `augment_*` items → GPU; (5) later: the 4 new fns (Masuda/Form/Hatch/Gender) + sub-pickers.
- **Reads:** held Kernel `BreedingUpgradeItem.tier()` → capacity `.slots`; installed = `Augments.toLevels()`; catalog = `AugmentType`/`AugmentFunction` (+ slot-cost); GPU count = inventory; Data balance.
- **Actions:** `APPLY_AUGMENT(fn)` (dual-cost gate), `REMOVE_AUGMENT(fn)`.
- **owo:** triptych — KERNEL (cycle tier ‹ › · slots used/cap + pips) → AUGMENT (scroll ~10, each `n slot(s)` · ✓ applied · `GPU ×1 (have n)` · APPLY gated) → AUGMENTED (result kernel · applied rows · capacity n/cap).
- **Build state:** heaviest backend build; do after Storage/Compiler/Pastures. **This is where the GPU + Data economy first bites.**

### 3.6 Dashboard  ⏸ — defer. Later: evolve `DashboardExport.toHtml()` / pair with the Option-B localhost dashboard (`PORTING_WEB_UI.md`) for heavy charts; in-console = a few live counters (Data/cycle · eggs/hr · shiny tally) off the analytics event stream.

---

## 4. Economy actions (Data + GPU)
- **Rule:** every GPU-consuming action **also debits Data**, atomically (both or neither).
- **Gate table** (numbers = Deuce to set, Q5):

| Action | GPU | Data | Other gate |
|---|---|---|---|
| `APPLY_AUGMENT` | 1 | `dataCost(fn)` | slots-remaining ≥ `slotCost(fn)`; no dupe |
| `UPGRADE_TAB` (capacity/features) | `gpu(tab,lvl)` | `data(tab,lvl)` | — |
| `INSTALL_BUFF` | 0 (or upfront?) | 0 (or upfront?) | the ongoing drain is the real cost |
| `WRITE_DISK` / `READ_DISK` | — | Data ↔ disk value | consumes/produces a disk item |

- **Data disks (new build):** the Notebook is the read/write drive — `WRITE_DISK` debits Data + upgrades a blank/lower disk to a denomination; `READ_DISK` credits Data + blanks it. Denomination→value map TBD (blank→byte→…→rocket). Deferred past the first tabs.
- **Pure spend helper (testable):** `Cost(dataCost, gpuCount)` + `CostGate.tryPay(DataStore, PlayerInventory, Cost)` — debits Data + removes GPU atomically, rolls back Data if GPU is short. Headless-tested.

## 5. Build order (vertical slices — each independently shippable)
Re-ordered by **backend-readiness**:
1. **Sync layer + live status bar** — ✅ **BUILT + green (2026-06-30).** `NotebookNet` (common seam) + `NotebookRequestC2S` (C2S) + `NotebookStatusS2C` (first S2C) + client `NotebookState` cache. The console **requests-on-open / on-tab-switch**; the server pushes **Data balance + GPU inventory-count + any-Daemon-ON**; the client caches it and rebuilds the screen in place (`refreshIfOpen`→`clearAndInit`, no re-request loop); the status bar renders it live. *Needs in-game deploy to verify the numbers populate.*
2. **Storage tab** — ✅ **BUILT + green (2026-06-30).** `NotebookStorageS2C` (snapshot) + `NotebookActionC2S` (PULL_ONE / PULL_ID) + server `pull` handler (withdraw → offer-or-drop into inventory). The Harvester tab renders the real `NotebookStore` as a clickable item grid (icon + compact count, biggest first); **left-click pulls one stack, right-click pulls all of that id**; the action re-pushes status + storage so the grid + status bar refresh live. *Needs in-game deploy to verify.*
3. **Compiler tab** — ✅ **BUILT + green (2026-06-30).** `NotebookCompilerS2C` (catalog + loadout + drain) + `NotebookActionC2S` extended with `SET_BUFF`/`TOGGLE_DAEMON` (+ an `amount`=tier field). Targets the first Daemon in the player's inventory. Triptych: **DAEMON** (status · Data · n/32) · **EFFECT** (scroll catalog, each row `−  L{t}/{cap}  +` + cost/s, installed rows highlighted) · **LOADOUT** (live drain Data/s · runtime@Data · **Power ON/OFF**). Each ±/power click → server sets the `DaemonLoadout` component / `setOn` → re-pushes. *Needs in-game deploy to verify.*
4. **Pastures tab** — ✅ **BUILT + green (2026-06-30).** Read-only snapshot monitor (§3.2): new per-player `PastureSnapshotStore` + capture hook in `PastureWand.openMenu` + `NotebookPasturesS2C`. Tab = pasture list (name · eggs) → click → the selected pasture's pairs with per-pair status (color-coded), read-only. *Needs in-game deploy to verify.*
5. **Augmenter tab** — ✅ **BUILT + green (2026-06-30).** Built the missing slot-capacity backend: capacity = `BreedingTier.slots`, uniform 1-slot/augment, slots-used derived from the Kernel's applied `AugmentType`s. `NotebookAugmenterS2C` + `APPLY_AUGMENT`/`REMOVE_AUGMENT` on the first held Kernel — apply writes the augment (`AugmentType.apply` copy written back to the inventory slot), remove strips it; gated by **slots + no-dupe**. **GPU/Data cost DEFERRED** (§7.5, no-op seam); per-augment slot costs + the 4 new functions are the later economy pass. Tab: KERNEL (tier · slots used/cap + pips) · AUGMENT (catalog, per-row APPLY/REMOVE). *Needs in-game deploy to verify.*
6. **BioBank read-layer + tab** — extend `EggReader` + `entries()` + sort/search + addressing, then accordion (needs Q2, Q3).
7. **Dashboard + Data disks.**

Each slice: pure core + headless tests → payloads → server handler → owo tab → in-game QA. **Slices 1–3 are unblocked and can start immediately.**

## 6. QA
- **Headless (green before UI):** `NotebookSnapshot` builder; `CostGate.tryPay` (Data+GPU atomicity + rollback); per-tab DTO mappers; Storage already covered by `NotebookStorageTest`.
- **In-game per slice:** status bar shows real Data/GPU; Storage pull round-trips + persists; Compiler install/tier/remove/toggle reflects in loadout + drain; Pastures list matches owned + link toggles + status/progress correct; Augmenter apply debits GPU+Data + respects slots + rejects when short; BioBank accordion + stats + sort/search correct.
- Every action emits a `GpLog` line.

## 7. Design decisions — RESOLVED (Deuce, 2026-06-30)
1. **Pastures = remote READ-ONLY monitor via last-opened snapshots.** The console shows each pasture's contents + what's actively breeding from a **snapshot captured when you last right-clicked/opened it in-world** (remote chunks aren't loaded → no live read). **No modifying a pasture from the console** — pairing / claim / name stay in the in-world wand menu. → build a per-player `PastureSnapshotStore` + a capture hook on pasture-open; the Pastures tab is **display-only** (no CLAIM action). *(supersedes §3.2's enumerate+claim model)*
2. **BioBank = PER-PLAYER store (block-free).** All blocks are being removed; the Notebook is the hub. Migrate `BioBankStore` block-addressed (dim+pos) → **per-player UUID-keyed** (like `NotebookStore`/`DataStore`). "My kept mons" = my bank. *(supersedes §3.3 addressing)*
3. **BioBank = eggs only** (no hatched mons). **EVs on eggs are REAL** — set via the **EV augment path** (`AugmentFunction.EV` / `EV_SPREAD`) — so the EV column is meaningful; extend `EggReader` to read per-stat EVs (+ the other missing fields). *(supersedes the "0-EV" note in §3.3)*
4. **Augmenter path = CONFIRMED** — build the augment redesign (slots + apply/remove + catalog).
5. **Costs + crafting recipes DEFERRED.** Build augment/tab actions with **NO GPU/Data cost gate** for now — `CostGate` is a no-op seam until economy numbers land. *(supersedes the cost gates in §3.5 + §4)*
6. **Search bar + `gp://` pathing = builder's discretion** — BioBank search will be functional; `gp://` a light navigation affordance.
