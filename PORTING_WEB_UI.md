# 🌐 Web Dev → Minecraft: UI Strategy

_Research synthesis (2026-06-30) — three verified passes on getting web-dev ergonomics into Greener Pastures
(Fabric 1.21.1 / Java 21, public Modrinth+CF release). Deep-dives: `greener-pastures/OPTION_B_LOCALHOST_WEB_BRIDGE.md`
+ the A/C findings folded in below. `glow PORTING_WEB_UI.md`._

## TL;DR — it's not A-vs-B-vs-C, it's a **stack matched to the surface**

The honest answer to "can we make it happen": **yes, two ways that are each nearly free**, plus a heavy "true web
in-world" path we park.

- **Dashboards / data / analytics → Option B (localhost web bridge).** The mod serves a web app you open in your
  real browser, live data over WebSocket. **~600 KB added** (Minecraft already bundles Netty). This is the *real*
  web-dev-porting win, and it lands squarely on the mod's "Data Science" identity + the pending dashboards task.
- **In-world interactive GUIs → Option C (owo-ui).** Already bundled in your pack (**0 KB added**), flexbox-like
  layout + **XML hot-reload**. Closes ~80% of the "MC UI is painful" gap decisively.
- **Rich HTML on an in-game screen → Option A (MCEF).** Genuinely works on 1.21.1, but a **~100–236 MB per-OS
  Chromium download** per user makes it wrong for a lightweight public mod. **Parked** (optional add-on if ever).

**Recommended first move:** build a **live analytics dashboard via B** — it's the flagship "web porting" proof,
near-zero weight, evolves the `DashboardExport.toHtml()` we already ship, and is decoupled from any game-UI risk.
Adopt **owo-ui (C)** as the default for the next in-world GUI we touch.

---

## Decision matrix

| What you're building | Tool | Why |
|---|---|---|
| Live analytics dashboards, data/list views, monitoring, exports | **B · localhost web bridge** | "Data Science" identity; ~600 KB; builds on existing HTML export; bigger/better than in-game; multi-monitor + headless-server ops |
| In-world GUIs: wand pairing, Compiler menu, BioBank lists, config screens | **C · owo-ui** | Already bundled (0 KB); flexbox + XML hot-reload; blur-free `BaseOwoScreen` |
| The **Daemon** node-graph editor (pan/zoom + drag + wires) | **C · owo-ui custom component** | owo scaffolds pan/zoom (`RenderEffect.transform`) + `drawLine` + drag events; you write only the graph semantics |
| Rich HTML rendered **on an in-game screen/block** (if ever) | **A · MCEF** (optional) | Only true-web-in-world path; ~100 MB+ Chromium ⇒ parked unless specifically wanted |

---

## Option B — Localhost web app + WebSocket bridge ✅ **the flagship**

**How:** the mod stands up a tiny HTTP/WS server bound to `127.0.0.1`, serves a bundled web app from the jar, and
streams live JSON (balances, breeding, egg feed, analytics) over WebSocket. You build a real web app — any framework,
real DevTools, real hot-reload.

**The one load-bearing fact:** MC 1.21.1 bundles **Netty 4.1.97.Final** but **not** `netty-codec-http`. So we reuse
MC's Netty engine on our own `ServerBootstrap` and **nest a single ~600 KB jar** (`netty-codec-http:4.1.97.Final`) —
version-matched, so **no shading/relocation** and its transitive deps are already present. (Rejected: Javalin/Jetty/
Ktor = multi-MB; NanoHTTPD = abandoned/CVE; the JDK's `HttpServer` can't do WebSocket.)

**Precedents:** BlueMap (rewrote its server to plain NIO to *drop* Jetty — the strongest signal), Spark (streams to an
external web viewer), WSMC + Online Emotes (both reuse MC's Netty + nest `netty-codec-http` on Fabric 1.21.1).

**Fits our code today:** the agent found we already have `DashboardStats` + `DashboardExport.toHtml()` and a pending
**F5 dashboards** task (#6). B is the *live* evolution of that static export — and the routing/serialization stays an
MC-free, headless-tested core (our "logic-first" house style); only the Netty bind + Fabric lifecycle is the thin adapter.

**Security checklist (v1):** loopback-only bind · short-lived token handshake · ephemeral-port fallback (`ServerSocket(0)`) ·
**off by default on dedicated servers**, ops-gated · **read-only** to start (no game mutations from the page).

**The "separate window" trade-off is often an *advantage*:** a breeding/analytics dashboard *wants* to be a big,
resizable, multi-monitor, exportable browser window next to the game — not a cramped in-game screen. And it's the only
option that works **headless** for a server admin watching their farm.

**Cons:** alt-tab (not overlaid in-world); you run a local socket (mitigated by the checklist).

---

## Option C — owo-ui (web-like **native** UI) ✅ **the in-world workhorse**

**Already in your pack:** `owo-lib 0.12.15.4+1.21` — exact 1.21.1 / Java 21 match, **0 KB added**. (Standalone release
just needs `depends: owo` in `fabric.mod.json`.)

It's a genuine declarative, flexbox-flavored component engine — not a thin `Widget` wrapper. The web→owo mental map:

| Web / CSS / React | owo-ui |
|---|---|
| `display:flex` row/col | `Containers.horizontalFlow / verticalFlow` |
| `flex-grow` | `Sizing.expand()` |
| `width:100%` / `fit-content` / `200px` | `Sizing.fill()` / `Sizing.content()` / `Sizing.fixed(200)` |
| `position:absolute` | `StackLayout` + `Positioning.absolute/relative/across` |
| `padding` / `margin` / `gap` | `.padding(Insets)` / `.margins(Insets)` / spacers |
| `background`/`border`/`shadow` | `.surface(Surface…)` (`PANEL`, `flat`, `outline`, `blur`, `tiled`…) |
| `z-index` | `.zIndex(int)` |
| `onClick`/`onInput` | `.mouseDown()/mouseDrag()/keyPress()…` event streams; `.onPress()` |
| CSS `transition` | `AnimatableProperty` + `Animation` + `Easing` |
| `overflow:scroll` / `hidden` | `Containers.verticalScroll` / clipping **on by default** |
| `transform: translate/scale` | `RenderEffect.transform(Matrix4f)` ← **pan/zoom** |
| JSX markup | XML model files `assets/<mod>/owo_ui/*.xml` |
| **Vite HMR** | **Ctrl+F5 XML hot-reload** — edit markup, reopen screen, no recompile |

**The headline win for a web dev:** XML hot-reload + an inspect-element-style component overlay (Alt+Shift). Iteration
on the deferred GUIs goes from "recompile-relaunch per tweak" to "save-XML-reopen-screen."

**Can it do the hard screens?**
- Wand pairing / Compiler menu / BioBank lists / config: **yes, comfortably** (flow+grid+scroll+panels; real inventory
  slots via `BaseOwoHandledScreen.SlotComponent`).
- **Daemon node-graph: hybrid** — owo scaffolds the painful 70% (pan/zoom via `RenderEffect.transform`, free node
  placement via absolute positioning, **native `drawLine` wires**, per-node drag events, blur-free base Screen). You
  write only the inherent 30%: the graph data model + zoom-aware hit-testing (screen↔canvas un-projection) + wire
  routing/validation. **It stays a custom owo *component*, never a bare `Screen` from zero.** This also makes our
  "don't overload left-click for pan+action" principle clean — node-drag vs canvas-pan vs scroll-zoom separate naturally.

**Caveats:** no reactive data-binding (you push updates imperatively in tick/handlers — fine, just not Vue magic);
no single `gap:` (use spacers); a builder "type-decay" ordering gotcha (children-first, or use XML). Cobblemon's own UI
is **bespoke** (324 hand-rolled classes, no reusable toolkit) — to match its look you re-texture via `Surface.tiled`/
nine-patch, which owo supports.

---

## Option A — MCEF (embedded Chromium) ⚠️ **parked / optional**

**Verdict: technically viable on 1.21.1, wrong for a lightweight public mod.** Real HTML/CSS/JS rendered onto a MC
`Screen` via Chromium off-screen-render → GL texture. Use **Keksuccino's MCEF fork** (`mcef-keksuccino`, actively
maintained, Fabric/Forge/NeoForge, 1.21.1). Serves your page from `assets/<mod>/html/` via a built-in `mod://` scheme.

**Why parked:** every user downloads a **~100–236 MB per-OS Chromium** on first launch + a hard native-library
dependency; **no Android, client-only**, GL friction with Sodium/Iris (your pack runs both), and **no built-in JS↔Java
bridge** (you hand-wire a JCEF `CefMessageRouter`). LGPL-2.1 (fine to depend on). Great for a *heavy* in-game web
dashboard; antithetical to "quick install." **Only revisit** if we specifically want web content on an in-world
screen/block and accept the weight — likely as an *optional* companion mod, never a core dependency.

---

## Recommended architecture (the stack)

```
Greener Pastures
├─ In-world GUIs ........ owo-ui (C)         ← 0 KB, hot-reload, the wand/Compiler/BioBank/config + Daemon canvas
│   └─ Daemon node-graph . custom owo Component (pan/zoom + drawLine + drag, graph logic ours)
├─ Data / dashboards .... localhost web bridge (B)  ← ~600 KB, real web app, live WS, evolves the HTML export
└─ Web-on-a-block ....... MCEF (A) [PARKED]  ← optional add-on only if ever wanted
```

Both B and C are **near-free and non-exclusive** — we can pursue them in parallel. A is a deliberate non-goal for now.

## ▶️ Where to start — the B proof-of-concept (highest value, lowest risk)
1. **Core (MC-free, headless-tested):** a `DashboardRouter` that maps a path/topic → JSON, reusing `DashboardStats`.
2. **Adapter:** Netty `ServerBootstrap` on `127.0.0.1:0`, `HttpServerCodec` + `WebSocketServerProtocolHandler` (from the
   nested 600 KB jar), serving a bundled `dashboard.html` + a `/ws` stream; started on world-load, gated + tokened.
3. **Web app:** vendor **uPlot** (tiny) into `resources/`, render a live chart of egg/Data throughput off the WS feed;
   a "Download CSV/HTML" button reuses `DashboardExport`. Open it from a `/gp dashboard` chat link.
4. Ship read-only v1; later add controls (which would graduate to a tokened POST or a control-channel).

That single PoC proves the whole "web dev porting" thesis, advances the real F5/#6 backlog, and adds ~600 KB.

## 🧭 Deuce's direction (2026-06-30, locked-in preference)
Deuce wants to **develop the UI as a real web app with Claude Code first, then move it into Minecraft** — i.e., the
web-dev DX (real HTML/CSS/JS, real browser, fast iteration) is the priority, not a from-scratch native build. He's
leaning **away from "the one that packages EVERYTHING"** (MCEF as the *default*) — but is **explicitly willing to
package Chromium if the develop-in-web-then-port workflow needs it.** So the shortlist re-weights:
- **Favored:** a genuine web app — **Option B** (served + WS bridge) is the most natural "build it in web, it stays
  web." A **design-in-web → port-to-owo-ui** pipeline (Option C) also fits the "develop in web, move to MC" flow.
- **On the table (not the default):** **Option A / MCEF** for true in-world web panels — acceptable *if* that's
  what's needed to keep the UI as literal web tech in-game; the ~100 MB Chromium is a cost Deuce will pay for it.

## Open questions for Deuce (no rush — pick when you surface from QA)
- **Start with B (dashboard) or C (re-skin an existing GUI in owo-ui with hot-reload)?** I lean **B** — it's the
  flagship and advances #6.
- **Dashboard scope v1:** which views first — live egg/shiny feed, Data economy over time, breeding-goal progress?
- **owo-ui adoption:** retrofit an existing screen as the pilot, or apply it to the next *new* GUI?

## Sources / deep-dives
- **Option B full writeup (architecture, lib table, security, PoC):** `greener-pastures/OPTION_B_LOCALHOST_WEB_BRIDGE.md`
- MCEF: CinemaMod/mcef + `mcef-keksuccino` (Modrinth) · owo-ui: docs.wispforest.io/owo/ui · LibGui: CottonMC/LibGui
- Verified against local jars: `owo-lib-0.12.15.4+1.21.jar`, `Cobblemon-fabric-1.7.3+1.21.1.jar`, MC's bundled Netty.
