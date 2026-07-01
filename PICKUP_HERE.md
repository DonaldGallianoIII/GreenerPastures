# 🎯 PICKUP — Decked-out Daemon node-graph (stages 2–3), phased build

> **DIRECTIVE (Deuce, 2026-07-01):** Execute **every phase below, in order, autonomously** — build + `git commit` each phase, do **NOT deploy between phases**. Deuce **batch-tests everything at the very end** (his [[batch-qa-workflow]]): deploy the final jar **only when he asks**. If a phase reveals a blocker, note it, pick the sanest default, keep going. When all phases are done + building green, tell him "all N phases built + committed, ready to deploy + batch-test."

## Where we are (already shipped this session)
- MCEF console renders React in-game (`NotebookBrowserScreen`, browser kept alive). Data over the loopback WS bridge (`DsBridge` :25599). Right-click a pasture → `NotebookItem.PASTURE_OPENER` (client: open console + set a placeholder `NotebookState.pastureConfig`, `DsBridge.pushNow()`) + `NotebookNet.pushPastureConfig` (server sends `NotebookPastureConfigS2C`). React `PastureConfig` shows name/link/Kernel + the **DaemonGraph**.
- **DaemonGraph v1 + pan/zoom** (in `greener-pastures-ui/src/App.jsx`): each roster mon = a node; drag a mon's port onto another → a **pair** (wires ↔ `PastureData.pairings`, two mons in one bucket = a pair, via the `pasture`/`PAIRINGS` bridge action). Scroll = zoom (cursor-fixed), drag canvas = pan. Node positions are **client-side only** (auto-laid-out) — Phase 4 persists them.
- Native inventory overlay (real icons, grab-and-move via `NotebookInvSwapC2S`, Kernel slot) is done + separate.
- Design bible: **`VISUAL_SCRIPTING_UI_IDEA.md`** (unit/filter/sink node model, shape-coded ports ◆pair ▶flow ✕void, the **void-log trust requirement**). Read it before Phase 3.

## Key facts (don't re-derive)
- **Build (Java):** `JAVA_HOME=/home/donaldgalliano/jdks/jdk-21.0.11+10 /home/donaldgalliano/pokemon-prediction/greener-pastures/gradlew -p /home/donaldgalliano/pokemon-prediction/greener-pastures build` (Bash `dangerouslyDisableSandbox:true`). **Always read the real output for `BUILD SUCCESSFUL`** — a grep pipe's exit 0 is NOT gradle's.
- **Build (UI):** `cd greener-pastures-ui && npm run build` → single-file `index.html` into `greener-pastures/src/main/resources/assets/greenerpastures/html/` (viteSingleFile). Rebuild the jar after.
- **Deploy (END ONLY):** `cp` jar → `/mnt/c/Users/deuce/curseforge/minecraft/Instances/Greener Pastures Test/mods/greenerpastures-0.1.0.jar`, verify md5 + `zipfile.testzip()`. MC must be closed.
- **Codec:** `PacketCodec.tuple` ≤6 fields; `PacketCodecs.map(HashMap::new, key, val)`; `VAR_LONG/VAR_INT/BOOL/STRING`; list = `X.CODEC.collect(PacketCodecs.toList())`. For >6 fields or nested, pack into a sub-record or a JSON string field.
- **DrawContext (native):** `drawItem`, `drawItemInSlot` (count — NOT drawStackOverlay), `drawItemTooltip`, `drawBorder`, `fill`.
- **Commit trailer:** `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>` + `Claude-Session: https://claude.ai/code/session_017Dq3vi9HWWc9bYUDAaUSYs`.
- Files: server net `greener-pastures/src/main/java/com/greenerpastures/notebook/net/*` · bridge `notebook/bridge/DsBridge.java` · client `client/notebook/{NotebookState,NotebookBrowserScreen}.java` · `client/GreenerPasturesClient.java` · pasture data `pasture/breeding/{PastureData,PastureRegistry,MultiPairBreeder}.java` · ingest `notebook/EggIngest.java` · React `greener-pastures-ui/src/App.jsx`. Filter/keep logic to reuse: `economy/RenderRun`, `biobank/ValueRule`, `RenderValuation`, `DataStore`, `biobank/BioBankStore`; egg reads `egg/oracle/cull/EggReader` + `EggInfo` (species/shiny/ivsKnown/ivTotal/perfectCount — verify fields).

---

## PHASE 1 — Graph data model + sync (server ⇄ client)
Add a persisted per-pasture graph beyond pairings: filter/sink nodes + flow edges, plus mon-node positions.
- **`PastureGraphData`** (new, in `pasture/breeding/`): `record Node(String id, String type, double x, double y, String monId, Map<String,String> config)` + `record Edge(String from, String fromPort, String to, String toPort)`; a class holding `List<Node> nodes` + `List<Edge> edges`. Types: `MON`, `FILTER_IV`, `FILTER_EV`, `FILTER_NATURE`, `FILTER_SHINY`, `SINK_BIOBANK`, `SINK_DATA`, `SOURCE` (egg entry). NBT read/write (mirror `BioBankData` NBT style). Config values are strings (IV min per stat `"hp":"31"`, natures `"list":"adamant,jolly"`, shiny `"gate":"only"`).
- Add `PastureGraphData graph` to `PastureData` (+ NBT in its serialize/deserialize; default empty).
- **Sync down:** simplest = add a **graph JSON string** field to `NotebookPastureConfigS2C` (Gson-serialize the graph server-side). React parses it. (If tuple arity blocks — it's currently 6 fields — make a separate `NotebookGraphS2C(long pos, String json)` pushed alongside from `pushPastureConfig`.)
- **Sync up:** **`NotebookGraphSaveC2S(long pos, String json)`** — React sends the whole graph JSON on edit; `NotebookNet.onGraphSave` validates reach + parses (Gson) → `pd.graph = ...` → `reg.markDirty()` → `pushPastureConfig`. Bridge: route a `pasture`/`GRAPH` action → send this packet (extend `handlePastureAction` in DsBridge).
- Commit: `feat(daemon): persisted pasture graph model + sync (nodes/edges/positions)`.

## PHASE 2 — React node editor: filter/sink nodes, typed ports, flow wiring
In `DaemonGraph` (App.jsx), grow beyond mon-pairing:
- **Graph state** from `cfg.graph` (the JSON): nodes (incl. auto MON nodes merged with roster + saved positions) + edges. Local edits → `send('pasture','GRAPH',{pos, json})` (debounce ~300ms). MON node positions now persist via the graph (drop the client-only `positions` for mons).
- **Palette** (a small toolbar): `+ IV`, `+ EV`, `+ Nature`, `+ Shiny`, `+ → BioBank`, `+ → Data`, `+ Source`. Adds a node at canvas center.
- **Node render per type** with **typed ports** (colored dots + label): MON = `pair ◆` + `eggs ▶`; FILTER_* = `in ▶`, `pass ▶`, `void ✕`; SINK_* = `in ▶`; SOURCE = `out ▶`. Keep pan/zoom + graph-space coords (`toGraph`).
- **Wire types + compat:** pair↔pair (mon-mon, still → pairings), flow ▶ (eggs/out → in, pass → in/sink, void → void-in/Data). Reject incompatible drops. Click a wire to delete.
- **Node config UI** (click a node → inline panel): IV = 6 min-IV number inputs; EV = 6 stat toggles/threshold; Nature = multi-select chips; Shiny = radio (any / only-shiny / no-shiny). Writes into the node's `config` → save graph.
- Delete node (a ✕ on the node) → remove node + its edges → save.
- Commit: `feat(daemon): filter/sink node palette + typed ports + config (visual editor)`.

## PHASE 3 — Backend eval: route eggs through the graph + the void log
Make the graph functional. In `EggIngest.ingest` (currently keep-all for linked pastures), when the pasture has a non-trivial graph, **evaluate**:
- Walk the flow from `SOURCE` (or, if none, a single linear chain of the wired filters → sink). At each `FILTER_*`, evaluate the egg's props (`EggReader`/`EggInfo`): pass → follow `pass` edge; fail → follow `void` edge (default → Data). Terminate at a sink.
  - `FILTER_IV`: pass iff every stat's IV ≥ its config min. `FILTER_SHINY`: gate only/no/any. `FILTER_NATURE`: pass iff nature ∈ config list. `FILTER_EV`: breeder EVs may be trivial → gate on config or pass-through v1.
- Reach `SINK_BIOBANK` → `BioBankStore.deposit` (keep). Reach `SINK_DATA` or voided → `DataStore.credit(owner, RenderValuation.dataFor(...))` (render to Data).
- **SACRED guard stays:** shiny or unreadable egg → always keep, regardless of graph.
- **Void log (NON-NEGOTIABLE, [[observability-first-logging]] + design doc):** every void emits `Analytics.record(... Event "egg_voided" ...)` (species + actual IVs/nature/shiny + which filter rejected + pasture pos) AND `GpLog.i("egg_ingest","void",...)`. Keeps too (`egg_ingest keep`). The trust feature — must be observable.
- If the graph has **no filters/sinks**, fall back to current keep-all (don't break existing pastures).
- Commit: `feat(daemon): server eval — route bred eggs through the filter graph (keep/void + void log)`.

## PHASE 4 — Polish
- Persisted mon-node positions (via Phase 1 graph — verify round-trip; drop client-only positions).
- A **Log** mini-view in the pasture config (or a node): last N `egg_ingest keep/void` lines from a small ring buffer synced over the bridge (`log` channel) — the player-facing void feed. If time-boxed, at least surface kept/voided counts.
- `npm run build` + jar build; confirm green.
- Commit: `feat(daemon): persisted node positions + void-log view`.

## FINISH
After Phase 4: full `gradlew build` green + `npm run build` green + jar built. **Do not deploy.** Tell Deuce all phases are built + committed and it's ready for him to close MC → deploy → batch-test the whole pipeline (add filters, wire a pipeline, breed, watch keep/void + the void log). Update the [[daemon-nodegraph-deckedout]] memory with what shipped.
