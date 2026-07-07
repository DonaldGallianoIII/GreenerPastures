# 🧪 Spec — MCEF + React Console & "A Data Science Mod Dependency"

_2026-07-01. The UI pivot Deuce chose after seeing owo render: **bundle real MCEF + React, render it in-game**,
develop it rapidly over **localhost** (no Minecraft recompile per tweak), and package the shared runtime as a
**separate dependency mod** so future mods just declare it. Grounds on the already-verified `MCEF_RECIPE.md`
(MCEF `2.1.6-1.21.1`, `com.cinemamod.mcef`) + `PORTING_WEB_UI.md` (Option B localhost WS bridge). `glow` this._

---

## 0. The decision
- **Render:** real React/HTML/CSS in-world via **MCEF** (embedded Chromium → GL texture on a `Screen`). Crisp, the JSX mock verbatim.
- **Develop:** the React app runs in a normal browser with Vite hot-reload, talking to a **live Minecraft** over `ws://127.0.0.1`. No Gradle/MC relaunch to iterate the UI.
- **Package:** a standalone **dependency mod** ("A Data Science Mod Dependency") owns MCEF + the bridge + the JS SDK. Greener Pastures (and future mods) just `depends` on it and ship a React bundle.

## 1. The key idea — two transports, one app
MCEF and the localhost bridge are **not** alternatives. Each does one job:

| Concern | Mechanism | Dev (browser) | Prod (in-game) |
|---|---|---|---|
| **Data** (state + actions) | WebSocket `ws://127.0.0.1:<port>` | ✅ same | ✅ same |
| **Rendering** | React DOM | Vite dev server (a real browser tab) | MCEF Chromium → GL texture on a MC `Screen` |

```
      React app  (ONE codebase, ONE bundle)
   ┌───────────────┬──────────────────────────┐
   │  DEV: browser │  PROD: MCEF in-game panel │
   └───────┬───────┴─────────────┬────────────┘
           │  ws://127.0.0.1:<port>   (uniform data transport)
           ▼                     ▼
     Client-side WS bridge  (Dependency mod)
           │  reads client cache · forwards actions
           ▼
   existing S2C / C2S packet layer  ◄────►  MC server (DataStore, BioBank, …)
```

Because the **data path is a WebSocket in both modes**, the exact same React build runs on your laptop browser (instant hot-reload against a live world) and inside the game. That's the whole win. (We deliberately do **not** use MCEF's `cefQuery` JS↔Java bridge — it only exists inside Chromium, so it can't power localhost dev. WebSocket is the one transport that works in both.)

## 2. What's reused vs. new — nothing built so far is wasted
| Layer | Status |
|---|---|
| **Server logic** — `NotebookNet` receivers, `DataStore`/`BioBankStore`/`PastureSnapshotStore`, all pushes/actions | ✅ **unchanged** |
| **Client↔server packets** — the 6 S2C payloads + `NotebookActionC2S` | ✅ **unchanged** (they define the JSON schema the React app reads) |
| **Client cache** — `NotebookState` + the change-detection I just added | ✅ **reused** as the bridge's data source |
| **owo `NotebookScreen`** (676 lines) | ❌ retired — replaced by a React app + a ~1-file MCEF panel |
| **New:** the Dependency mod (MCEF + WS bridge + JS SDK), the React app (Vite), build wiring | 🆕 |

## 3. The Dependency mod — "A Data Science Mod Dependency"
Working id: **`dsruntime`** (display "Data Science Runtime"; Deuce to finalize the name/id).

**Depends on:** `mcef` (`com.cinemamod:mcef:2.1.6-1.21.1`, runtime `mcef-fabric`) → Chromium. **Nests:** `netty-codec-http:4.1.97.Final` (~600 KB, version-matched to MC's bundled Netty — no shading) for the WS server.

**Provides (Java API) — the reusable platform:**
- `WebPanelScreen` — a blur-free MC `Screen` that hosts a full-window MCEF browser: creates the browser (guards `MCEF.isInitialized()` + the download menu), draws its texture (V-flipped), forwards mouse/keyboard/scroll (per `MCEF_RECIPE.md §4–5`), `useBrowserControls(false)` so React gets raw keys, `browser.close()` on `close()`. Consumer mods subclass or construct it with a bundle id.
- `DsBridge` — the client-side WS server (loopback bind, ephemeral port in prod / fixed port in dev, token handshake). Consumers register **channels**:
  ```java
  DsBridge.channel("notebook")
      .state(() -> NotebookStateJson.of())          // supplier → JSON snapshot (pushed on change)
      .onAction((type, payloadJson) -> …);          // React → Java action
  ```
- `DsBridge.openPanel(modid, bundlePath)` — resolves dev-URL (`http://localhost:5173?...`) vs prod-URL (`mod://<modid>/index.html?...`), injects `ds_port` + `ds_token` as query params, opens a `WebPanelScreen`.

**Provides (JS) — the SDK, an npm package `@datascience/bridge`:**
```ts
import { connect, useChannel, send } from '@datascience/bridge'
connect()                              // reads ds_port/ds_token from URL (prod) or Vite env (dev)
const state = useChannel('notebook')   // React hook → live state, re-renders on push
send('notebook', 'PULL_ONE', { item }) // action → Java
```
The SDK hides dev/prod detection, reconnect, and the token handshake. Consumer React code is 100% plain React.

## 4. The bridge protocol (WebSocket, JSON frames)
- **Handshake:** client connects with `?ds_token=…`; server validates (loopback + token), replies `{"type":"hello","channels":[…]}`.
- **Server→client** (on connect + whenever a channel's state changes — driven by the existing `NotebookState` change-detection): `{"type":"state","channel":"notebook","data":{…}}`.
- **Client→server:** `{"type":"action","channel":"notebook","action":"SET_BUFF","payload":{"buff":"fortune","tier":2}}`.
- The `data` shapes **are** the current S2C payloads (`NotebookStatusS2C`, `…StorageS2C`, `…CompilerS2C`, `…PasturesS2C`, `…AugmenterS2C`, `…BioBankS2C`) serialized to JSON. The `action`s **are** `NotebookActionC2S` (`PULL_ONE/PULL_ID/SET_BUFF/TOGGLE_DAEMON/APPLY_AUGMENT/REMOVE_AUGMENT`). **The contract already exists — we're re-encoding it as JSON.**

## 5. The React app (lives in Greener Pastures)
Vite + React + TypeScript. One component per current tab, each reading one channel:

| owo tab (today) | React component | Channel data (existing payload) |
|---|---|---|
| Status bar | `<StatusBar/>` | `status` (`NotebookStatusS2C`) |
| Harvester/Storage | `<StorageTab/>` | `storage` (`NotebookStorageS2C`) |
| Compiler | `<CompilerTab/>` | `compiler` (`NotebookCompilerS2C`) |
| Pastures | `<PasturesTab/>` | `pastures` (`NotebookPasturesS2C`) |
| Augmenter | `<AugmenterTab/>` | `augmenter` (`NotebookAugmenterS2C`) |
| BioBank | `<BioBankTab/>` | `biobank` (`NotebookBioBankS2C`) |
| Dashboard | `<Dashboard/>` (finally free — real charts) | `analytics` (new; folds in task #6) |

The JSX mock in `design/design_reference/notebook-console.NOTES.md` becomes the actual stylesheet. Palette/spacing/fonts are now real CSS — crisp.

## 6. The localhost dev loop (the DX Deuce wants)
1. Launch the dev MC instance → `dsruntime` starts the WS bridge on a **fixed dev port** (e.g. `25599`) and logs it.
2. `cd greener-pastures-ui && npm run dev` → Vite at `localhost:5173`.
3. Open `localhost:5173` in your browser → the SDK connects to `ws://localhost:25599` → **live data from the running world**. Edit React, save, **hot-reload** — no MC restart.
4. Ship: `npm run build` → bundle lands in `resources/assets/greenerpastures/html/`. In-game, MCEF loads `mod://greenerpastures/index.html?ds_port=<ephemeral>&ds_token=<t>` → identical app, in-world.

Dev uses a fixed port + dev token (loopback). Prod uses an ephemeral port + random token injected via the `mod://` URL query params. Same SDK reads both.

## 7. owo → React migration map (concrete)
- **Delete:** `client/notebook/NotebookScreen.java` (the owo screen) + its owo imports.
- **Keep as-is:** everything in `notebook/net/` (server), `NotebookState` (becomes the bridge's snapshot source — its change-detection triggers WS pushes), the C2S action enum.
- **Add (in GP):** `client/notebook/NotebookBridge.java` — registers the `notebook` channel: state supplier = serialize `NotebookState`; action handler = map to `NotebookActionC2S` + `ClientPlayNetworking.send`. Repoint `NotebookItem.CONSOLE_OPENER` to `DsBridge.openPanel("greenerpastures", "index.html")`.
- The 1/sec client poll I added stays (keeps `NotebookState` fresh); the bridge pushes to React only when it changes.

## 8. Build & packaging
- **UI project:** `greener-pastures-ui/` (Vite) → `npm run build` outputs to `../greener-pastures/src/main/resources/assets/greenerpastures/html/` (lowercase filenames — MCEF `mod://` is case-sensitive/lowercased). Optionally a Gradle task chains `npm run build` before `processResources`.
- **`fabric.mod.json` (GP):** add `"depends": { "dsruntime": "*" }`. Users install: MCEF + Data Science Runtime + Greener Pastures.
- **Dependency mod jar:** carries the nested `netty-codec-http`; declares `"depends": { "mcef": "*" }`.

## 9. Phased build order (prove, then extract)
1. **Spike (inside GP first, de-risk):** stand up the WS bridge + one `WebPanelScreen` + a 1-tab React app (Status + BioBank) reading real data in a dev browser **and** in MCEF. Proves MCEF renders on the Sodium/Iris rig and the WS loop works end-to-end. _Highest-risk unknowns, smallest surface._
2. **Extract** the MCEF + bridge + SDK into the standalone **`dsruntime`** mod once the spike renders in-world.
3. **Port the tabs** one at a time to React (Storage → Compiler → Pastures → Augmenter → BioBank), each reading its existing payload.
4. **Dashboard** as real charts (closes task #6, the "Data Science" flagship).
5. Retire owo `NotebookScreen`; polish; publish the two mods (Runtime + GP) to Modrinth/CF.

## 10. Risks & open decisions
- **Chromium weight/UX** — ~100–236 MB per-OS download on first run (MCEF's own progress menu). Guard every browser creation on `MCEF.isInitialized()`; graceful "installing renderer…" state.
- **Sodium/Iris GL friction** — the reason MCEF was parked. **Smoke-test rendering under both in Phase 1 before extracting** (your pack runs them). If it fights, the localhost-browser path still works as a fallback surface.
- **MCEF fork** — `MCEF_RECIPE.md` verified **CinemaMod `2.1.6-1.21.1`**; `PORTING_WEB_UI.md` notes the **Keksuccino fork** as actively maintained. _Confirm which is current for 1.21.1 at build time_ (pick the maintained one; the API in the recipe holds for both).
- **Security** — WS is loopback-only + token; fine for a client. On dedicated servers the console is client-side so there's no new server surface.
- **Licensing** — MCEF is LGPL-2.1 (fine to depend on, not shade). Netty codec is Apache-2.0.
- **Naming** — "A Data Science Mod Dependency" display name + a short mod id (`dsruntime`?) — your call.
- **Two-mod maintenance** — the cost of the reusable dependency. Phase 1 keeps it inside GP until proven, so we don't juggle two mods before it renders.
