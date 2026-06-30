# Greener Pastures — Live Dashboard Delivery

This is the research write-up for **Option B**, the "open it in your real browser" path for the analytics
dashboards (FEATURES_V2 **F5**, task #6). It builds directly on what we already ship: `analytics/DashboardStats`
(pure aggregators) and `analytics/DashboardExport` (self-contained CSV + dark-theme HTML). Option B turns that
*static export* into a *live page*.

All version/feature claims below were verified against current (2025–2026) sources; URLs are cited inline and
collected at the bottom.

---

## Option B — Localhost web app + WebSocket bridge

**The idea:** the mod runs a tiny embedded HTTP server bound to `127.0.0.1`. It serves a small web app
(HTML/CSS/JS bundled in the jar's `resources/`), and the player opens `http://localhost:<port>` in their normal
browser. The page opens a **WebSocket** back to the mod and receives live JSON (new egg events, updated
`DashboardStats`) which it renders into charts and tables. Export buttons reuse the *same* CSV/HTML we already
generate.

This is the live sibling of what BlueMap, Dynmap, and Spark already do for Minecraft: render data into a real web
page outside the game window. ([BlueMap](https://github.com/BlueMap-Minecraft/BlueMap),
[Dynmap](https://dynmap.wiki.gg/wiki/Installation), [Spark](https://spark.lucko.me/docs/Using-the-viewer))

### Architecture sketch

```
  Minecraft (logical server: SP world OR dedicated)
  ┌──────────────────────────────────────────────────────────┐
  │  analytics/EventLog ──► DashboardStats (pure, tested)     │   ← already exists
  │            │                                              │
  │            ▼                                              │
  │  GpDashboardServer  (new; server-side, MC-free core)      │
  │   ├─ HTTP GET  /            → index.html  (from jar)      │
  │   ├─ HTTP GET  /app.js,/css → static assets (from jar)    │
  │   ├─ HTTP GET  /export.csv  → DashboardExport.toCsv(...)  │   ← reuse
  │   ├─ HTTP GET  /export.html → DashboardExport.toHtml(...) │   ← reuse
  │   └─ WS        /live?token= → push JSON frames on event   │
  │            │  bound to 127.0.0.1:<port>, token-gated      │
  └────────────│─────────────────────────────────────────────┘
               ▼  (loopback only, or LAN for a dedicated server's ops)
        Player's real browser  →  Chart.js / uPlot renders live counters
```

Data flow is **one-directional and trivial**: the mod pushes; the browser draws. The WS carries small JSON frames
(`{"type":"egg","shiny":true,"tier":"Mk3",...}` and periodic `{"type":"stats", ...DashboardStats}` snapshots). No
RPC, no commands coming back in v1 — that keeps the security surface tiny.

### Recommended library choice + WHY

**Top pick: reuse Minecraft's bundled Netty — but be honest about the one missing piece.**

I verified what Netty modules Minecraft 1.21.1 actually puts on the classpath (PrismLauncher's `net.minecraft`
meta, cross-checked against this box's `~/.gradle/caches/fabric-loom/1.21.1/` and the CurseForge install's
`libraries/io/netty/`). All three sources agree — **Minecraft 1.21.1 bundles Netty `4.1.97.Final`**, specifically:

> `netty-buffer`, `netty-codec`, `netty-common`, `netty-handler`, `netty-resolver`, `netty-transport`,
> `netty-transport-classes-epoll`, `netty-transport-native-epoll`, `netty-transport-native-unix-common` — all
> `4.1.97.Final`.

**The catch:** `netty-codec-http` is **NOT** in that list. That's the module holding `HttpServerCodec`,
`HttpObjectAggregator`, and `WebSocketServerProtocolHandler` — i.e. the HTTP/WebSocket bits you'd actually use.
So "Netty is free" is only *half* true: the core engine is on the classpath, but the HTTP/WS codec is not.
(Sources: [PrismLauncher meta](https://github.com/PrismLauncher/meta-launcher/blob/master/net.minecraft/1.21.json),
[Netty `WebSocketServerProtocolHandler` Javadoc](https://netty.io/4.1/api/io/netty/handler/codec/http/websocketx/WebSocketServerProtocolHandler.html).)

This gives **two honest sub-options**, both lighter than any third-party framework:

1. **Netty + just `netty-codec-http` (RECOMMENDED).** Add a single jar — `io.netty:netty-codec-http:4.1.97.Final`
   (pin to MC's exact version) — `include`d via jar-in-jar. It's one module (~600 KB) and its transitive deps
   (`netty-codec`, `-handler`, `-buffer`, `-common`, `-transport`) are **already provided by Minecraft**, so you
   nest *only* `netty-codec-http` itself and nothing else. No relocation needed: you're matching MC's own Netty
   version, so there's no class clash. This is the path WSMC and Online Emotes effectively take — WSMC handles
   "TCP and WebSocket connections on the same listening port" by adding the HTTP/WS codecs into Netty's pipeline,
   and its Forge build "explicitly depends on netty-codec-http for handling HTTP and WebSocket."
   ([WSMC](https://github.com/rikka0w0/wsmc))
   - You can either spin up your **own** `ServerBootstrap` on a separate loopback port (simplest, fully
     isolated from the game socket — recommended for us), or, advanced, inject handlers into MC's existing
     channel to share the game port the way WSMC does (more coupling, not worth it for a dashboard).

2. **A single-file micro-lib that needs nothing from MC.** If you'd rather not touch Netty at all:
   [`nanohttpd-websocket`](https://github.com/NanoHttpd/nanohttpd) is famously "one Java file" for the core and
   has a WebSocket add-on. **But** NanoHTTPD is effectively abandoned and carries an unpatched advisory, so I'd
   avoid it for a public release. A modern zero-dep alternative is
   [`robaho/httpserver`](https://github.com/robaho/httpserver) (zero-dependency JDK-style server *with*
   WebSocket + virtual-thread friendly) — worth a look, but it's a less-known dependency to vouch for publicly.

**Explicitly NOT recommended for this mod:**

- **Javalin** — pleasant API, but Javalin 7 pulls in **Jetty 12+ and requires Java 17+**; that's a multi-MB
  servlet stack nested into a mod whose entire philosophy (see `build.gradle`) is "compile-only deps, bundle
  nothing." Overkill. ([Javalin docs](https://javalin.io/documentation))
- **Jetty / Undertow directly** — same weight problem; these are app-server engines. Tellingly, **BlueMap
  rewrote its integrated webserver onto plain Java NIO specifically to *drop* Jetty** and get "more lightweight on
  CPU and RAM." If the biggest web-map mod ships its own tiny server to avoid Jetty, we shouldn't add Jetty for a
  stats page. ([BlueMap webserver](https://bluemap.bluecolored.de/wiki/configs/Webserver.html))
- **Ktor** — great, but it's the Kotlin/coroutines world; drags in the Kotlin runtime + Ktor + a Netty/CIO
  engine. We're a Java 21 mod and only touch Kotlin `compile-only` to read Cobblemon's signatures. No.
- **JDK built-in `com.sun.net.httpserver.HttpServer`** — zero dependencies and it *can* serve our static files
  and the CSV/HTML export endpoints (JDK 18+ even has `SimpleFileServer`). **But it cannot do WebSocket at all** —
  "the built-in JDK httpserver implementation has no support for connection upgrades, so it is not possible to add
  websocket support." So it can't deliver the *live* half. It's a fine fallback for a "serve the static export
  over HTTP" mode, but not for the live bridge.
  ([JDK 21 `com.sun.net.httpserver`](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html))

> **Net recommendation:** Netty + nested `netty-codec-http:4.1.97.Final`, on our own loopback `ServerBootstrap`.
> Reuses MC's Netty engine, adds exactly one small jar, no relocation, and we're already shipping a mod that lives
> *inside* Netty's process anyway. If we want a true zero-new-dependency tier, ship a `HttpServer`-based
> **static export server** (no live WS) as a lightweight mode and gate the live WS mode behind the one extra jar.

### Dependency-weight verdict

| Option | New jar(s) nested | Approx added size | Relocation? | Live WS? | Verdict |
|---|---|---|---|---|---|
| **Netty + `netty-codec-http`** | 1 (codec-http only; rest provided by MC) | ~0.6 MB | No (matches MC's 4.1.97) | ✅ | **Recommended** |
| JDK `HttpServer` | 0 | 0 | n/a | ❌ (no upgrade support) | Good static-only fallback |
| `robaho/httpserver` | 1 | small, zero-dep | No | ✅ | Viable zero-MC-coupling alt |
| NanoHTTPD (+ws) | 1–2 | ~small | No | ✅ | Avoid (abandoned/CVE) |
| Javalin | many (Jetty 12 stack) | several MB | No (but heavy) | ✅ | Too heavy |
| Ktor | many (Kotlin + Ktor + engine) | several MB | Maybe | ✅ | Wrong ecosystem |

For a Modrinth/CF release where "minimal dependency weight" is a stated goal, the Netty-reuse path costs **one
~600 KB jar** and zero relocation headaches. That's the lightest option that still does live streaming.

The **web-app side adds zero Java weight**: ship a single small static bundle. Prefer **uPlot** (~40 KB, built for
exactly this — fast live time-series) or **Chart.js** (~200 KB, friendlier API) vendored into `resources/` so the
dashboard works **offline** (no CDN, important for a game mod). Avoid React/Recharts build toolchains for v1 —
plain JS + one chart lib keeps the jar small and the build trivial.
([uPlot](https://github.com/leeoniya/uPlot), [Chart.js](https://www.chartjs.org/))

### Security checklist

- [ ] **Bind to loopback only.** `new InetSocketAddress("127.0.0.1", port)` (or Netty channel bound to
      `127.0.0.1`). Never `0.0.0.0` in single-player. ([ServerSocket bind / port 0](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html))
- [ ] **Auth token / handshake.** Generate a random token at server start (e.g. `UUID`/`SecureRandom`). Require
      it as `?token=` on the WS handshake and on the export endpoints; reject otherwise. Surface the full
      `http://127.0.0.1:<port>/?token=...` URL to the player via a chat message / clickable command so they don't
      have to type it. (Mirrors how Spark hands you a one-click link to its viewer.)
- [ ] **Ephemeral / configurable port with conflict handling.** Default to a fixed port (say `25599`) but if it's
      taken, fall back to an OS-assigned free port via `new ServerSocket(0)` → `getLocalPort()`, then log/announce
      the chosen port. Make the port configurable. Never crash the game because a port is busy.
- [ ] **Don't auto-expose on multiplayer.** On a **dedicated server**, default the dashboard to **OFF** (or
      loopback-only). Only bind to a LAN/public interface behind an explicit opt-in config flag *and* ops-gating.
      Detect environment with Fabric's `EnvType` / `MinecraftServer.isDedicated()` and branch.
      ([Fabric lifecycle/env](https://maven.fabricmc.net/docs/fabric-api-0.87.1+1.20.2/net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents.html))
- [ ] **Ops-only on servers.** Tie the token to an op/permission check; non-ops can't mint a dashboard link.
- [ ] **Read-only v1.** No command/control over the WS — only data push. Removes the "remote code/command
      execution" class of risk that mods like WebConsole have to defend against.
      ([WebConsole](https://github.com/godemperoroftheworld/WebConsole))
- [ ] **CORS / Origin sanity.** It's loopback + token, but still set restrictive `Access-Control-Allow-Origin`
      and check the `Origin` header on the WS upgrade to blunt CSRF-style drive-by connects from other local pages.
- [ ] **Lifecycle.** Start the server on `SERVER_STARTED`, stop and free the port on `SERVER_STOPPING`/`STOPPED`.
      Don't leak a bound socket across world reloads.
- [ ] **Observability.** Per our standing rule, the server logs every bind/handshake/disconnect through the single
      `GpLog` JSONL seam so we can tail it while MC runs.

### Minimal proof-of-concept outline (serve a page + stream a live counter to a browser chart)

Goal: prove the pipe end-to-end with a fake counter before wiring real `DashboardStats`.

1. **`resources/dashboard/index.html`** — one `<canvas>` + a few lines of JS:
   ```html
   <canvas id="c"></canvas>
   <script src="uplot.min.js"></script>
   <script>
     const ws = new WebSocket(`ws://${location.host}/live${location.search}`); // carries ?token=
     const xs = [], ys = [];
     ws.onmessage = e => {
       const m = JSON.parse(e.data);           // {"t":169..., "eggs": 42}
       xs.push(m.t); ys.push(m.eggs);
       chart.setData([xs, ys]);                 // live redraw
     };
   </script>
   ```
2. **`GpDashboardServer` (Java, MC-free core):**
   - Netty `ServerBootstrap` bound to `127.0.0.1:<port>`; pipeline = `HttpServerCodec` →
     `HttpObjectAggregator(64KB)` → a tiny `ChannelInboundHandler` that:
     - for `GET /` and `/uplot.min.js` etc. → read the file from `getResourceAsStream("/dashboard/...")` and
       write it back with the right `Content-Type` (this is the "serve static from the jar" step);
     - for `GET /live` → verify token, then add `WebSocketServerProtocolHandler("/live")` and a frame handler;
     - register the new WS channel in a thread-safe `Set<Channel>`.
   - A scheduled task (or a hook on each real egg event) broadcasts a JSON `TextWebSocketFrame` to every channel
     in the set. For the PoC, a timer that does `counter++` every second is enough.
3. **MC adapter (thin):** on `SERVER_STARTED`, construct `GpDashboardServer`, print the tokenized localhost URL to
   chat; on `SERVER_STOPPING`, `close()` it. The egg-event hook calls `server.broadcastEgg(event)`.
4. **Verify:** open the URL → watch the line climb in the browser as the counter ticks. Then swap the fake counter
   for `DashboardExport.toJson(DashboardStats.summarize(events))` and you have the real dashboard.

Because `GpDashboardServer`'s framing/serialization is plain string/JSON work, the **serialization + endpoint
routing can live in an MC-free class and be unit-tested headless** the way `DashboardStats`/`DashboardExport`
already are — only the Netty bind + Fabric lifecycle wiring is the thin un-testable adapter.

### Pros / Cons

**Pros**
- Live, no manual re-export; the page updates as eggs hatch.
- Reuses everything: `DashboardStats` aggregators and `DashboardExport` CSV/HTML become live endpoints + export
  buttons on the same page.
- Real browser = real charting (uPlot/Chart.js/D3), real tables with sort/filter, copy-paste, print-to-PDF,
  proper fonts — none of which the in-game GUI toolkit gives cheaply.
- Cheap dependency-wise (one ~600 KB jar) and the web bundle adds no Java weight.
- Scales to the **dedicated-server ops** use case for free: the same server can host an ops-only dashboard.
- Strong precedent (BlueMap, Dynmap, Spark) — players already understand "open the link in your browser."

**Cons**
- It's **a separate window, not in-world** (see below). Some players expect everything inside the game.
- One extra moving part: a bound socket, a port, a token, lifecycle to manage. More than a static file write.
- Multiplayer needs careful defaults so we never silently expose a port (covered in the checklist).
- The live WS half needs the `netty-codec-http` jar; a true zero-dep build can only do the *static* export server.
- We own a little web code (HTML/JS) in addition to Java — small, but it's a second surface to maintain.

### The "separate window, not in-world" trade-off — and when it's an ADVANTAGE

The honest downside is that this **leaves the Minecraft window**. For a quick "how many shinies today?" glance,
an in-world GUI (which we already build with `GpCanvas`) is more immersive and is the right tool.

But for the thing F5 actually asks for — *real data-science dashboards* — leaving the window is a **feature**:

- **Big dashboards.** Multi-panel charts, long sortable tables (per-species luck, streaks, living-dex completion)
  need real screen space and real scroll. They're cramped in a 16:9 in-game GUI; they're comfortable in a browser
  tab.
- **Multi-monitor.** A breeder can park the live dashboard on a **second monitor** and keep playing fullscreen on
  the first. An in-world screen can't do that — it pauses/overlays the game. This is the single biggest win.
- **Export & share.** The browser already has Ctrl-P → PDF, "Save page as," copy-paste into spreadsheets, and our
  existing CSV/HTML download buttons sit right there. Sharing a screenshot of a real chart > a screenshot of a
  blocky GUI.
- **Charting power for free.** uPlot/Chart.js/D3 give zoom, hover-tooltips, legends, and live redraw that we'd
  otherwise have to reimplement pixel-by-pixel in `GpCanvas`.
- **Ops on headless servers.** A dedicated server has *no* game window at all — a web dashboard is the *only* way
  to show ops live breeding analytics. In-world simply isn't an option there.

So the guidance is **both/and, not either/or**: keep the quick in-world glance GUI, and offer the browser
dashboard as the "open the big board" view for deep analysis, multi-monitor play, and server ops — exactly the
split BlueMap (web) vs. an in-game minimap mod occupy.

---

### Sources

- BlueMap (integrated webserver, Java-NIO rewrite to drop Jetty): https://github.com/BlueMap-Minecraft/BlueMap ·
  https://bluemap.bluecolored.de/wiki/configs/Webserver.html
- Dynmap (integrated webserver, port 8123): https://dynmap.wiki.gg/wiki/Installation
- Spark profiler (external web viewer over bytebin + bytesocks WS; ships `bytesocks-java-client`):
  https://spark.lucko.me/docs/Using-the-viewer · https://github.com/lucko/spark · https://github.com/lucko/spark-viewer
- WSMC (reuses MC's Netty pipeline; TCP+WebSocket on one port; Forge build depends on `netty-codec-http`):
  https://github.com/rikka0w0/wsmc · https://www.curseforge.com/minecraft/mc-mods/wsmc
- WebConsole (Fabric remote web management; WS server + integrated web server, config-gated):
  https://github.com/godemperoroftheworld/WebConsole · https://www.curseforge.com/minecraft/mc-mods/webconsole
- Online Emotes (Fabric 1.21.1; nests `netty-codec-http` because MC doesn't ship it):
  https://modrinth.com/mod/online-emotes/version/3.2.3+mc1.21.1-fabric
- Minecraft 1.21.1 Netty library set (4.1.97.Final; **no** `netty-codec-http`):
  https://github.com/PrismLauncher/meta-launcher/blob/master/net.minecraft/1.21.json
- Netty WebSocket pipeline (`WebSocketServerProtocolHandler`, `HttpServerCodec`):
  https://netty.io/4.1/api/io/netty/handler/codec/http/websocketx/WebSocketServerProtocolHandler.html ·
  https://github.com/netty/netty/blob/4.1/example/src/main/java/io/netty/example/http/websocketx/server/WebSocketServerInitializer.java
- JDK `com.sun.net.httpserver` (serves static files; **no** WebSocket/upgrade support):
  https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/package-summary.html ·
  https://github.com/robaho/httpserver
- NanoHTTPD (one-file server; note abandoned/CVE): https://github.com/NanoHttpd/nanohttpd
- Javalin (Javalin 7 → Jetty 12+, Java 17+): https://javalin.io/documentation
- Fabric Loom `include` / jar-in-jar (non-transitive): https://docs.fabricmc.net/develop/loom/ ·
  https://docs.architectury.dev/loom/using_libraries
- Fabric lifecycle / dedicated-vs-integrated env: https://maven.fabricmc.net/docs/fabric-api-0.87.1+1.20.2/net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents.html
- Java `ServerSocket` (loopback bind, port 0 = OS-assigned free port):
  https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/ServerSocket.html
- Charting: uPlot https://github.com/leeoniya/uPlot · Chart.js https://www.chartjs.org/
