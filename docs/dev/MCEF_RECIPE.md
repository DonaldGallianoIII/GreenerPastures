# 🧩 MCEF Integration Recipe — PARKED FALLBACK

> **Status (2026-06-30):** the Notebook console is being built in **owo-ui** (Deuce's call, after his own
> `PORTING_WEB_UI.md` parked MCEF for weight + no built-in JS bridge + Sodium/Iris GL friction on his rig).
> Deuce reserved: *"we can do owo, but if I don't like it we change later."* **This is that escape hatch** — a
> source-verified, ready-to-execute recipe for **MCEF `2.1.6-1.21.1`** (Fabric / MC 1.21.1 / Java 21 / Yarn) so a
> switch is a *build* task, not a re-research. Verify against the `1.21.1` branch head at build time.

**Package root:** `com.cinemamod.mcef`. Key classes: `MCEF`, `MCEFBrowser` (extends `org.cef.browser.CefBrowserOsr`),
`MCEFClient`, `MCEFApp`, `MCEFRenderer`, `ModScheme`.

## 1. Gradle wiring
```gradle
repositories {
    maven { url = uri('https://mcef-download.cinemamod.com/repositories/releases') }
}
dependencies {
    modCompileOnly "com.cinemamod:mcef:2.1.6-1.21.1"         // common API — compile against this
    modRuntimeOnly "com.cinemamod:mcef-fabric:2.1.6-1.21.1"  // the Fabric mod — runtime; NOT bundled into our jar
}
```
Two artifacts: `mcef` (common, compile-only) + `mcef-fabric` (the loaded mod, runtime-only). Since `mcef-fabric` is
`modRuntimeOnly` it is not shaded in → declare `"depends": {"mcef": "*"}` in `fabric.mod.json`; users install MCEF alongside.

## 2. Init + lifecycle
MCEF self-initializes early via a mixin on `Minecraft.setScreen` (downloads natives on the first title screen behind its
own `MCEFDownloaderMenu`, then calls `MCEF.initialize()`). All-static `com.cinemamod.mcef.MCEF`:
```java
static boolean isInitialized()
static MCEFApp   getApp()       // throws if not initialized
static MCEFClient getClient()
static void scheduleForInit(MCEFInitListener task)   // functional: onInit(boolean successful)
static MCEFBrowser createBrowser(String url, boolean transparent)
static MCEFBrowser createBrowser(String url, boolean transparent, int width, int height)
```
There is **no** `MCEF.INSTANCE`. Guard creation:
```java
if (!MCEF.isInitialized()) MCEF.scheduleForInit(ok -> { if (ok) createBrowserNow(); });
else createBrowserNow();
```
`getApp/getClient/createBrowser` throw `RuntimeException("Chromium Embedded Framework was never initialized.")` if early.

## 3. Create a browser
```java
browser = MCEF.createBrowser("mod://greenerpastures/index.html", true); // transparent=true for an overlay
resizeBrowser();
```
`MCEFBrowser extends CefBrowserOsr`; the factory calls `setCloseAllowed()` + `createImmediately()`. Change page later:
`browser.loadURL(url)`.

## 4. Render in a Screen (Yarn: DrawContext / Tessellator)
Texture id from `browser.getRenderer().getTextureID()` (returns 0 until first paint). Draw a full-screen quad with the
`PositionTexColor` shader, **V flipped** (CEF's buffer is top-down):
```java
RenderSystem.disableDepthTest();
RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());
Tessellator t = Tessellator.getInstance();
BufferBuilder b = t.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
// (x0,y1)uv(0,1)  (x1,y1)uv(1,1)  (x1,y0)uv(1,0)  (x0,y0)uv(0,0), color 0xFFFFFFFF
BufferRenderer.drawWithGlobalProgram(b.end());
RenderSystem.enableDepthTest();
```
(Agent's confirmed snippet was Mojmap `GuiGraphics`+`Tesselator`; the above is the Yarn translation — verify the exact
1.21.1 buffer API when wiring.) Resize (browser px = logical × GUI scale): `browser.resize(scaleX(w), scaleY(h))` from
both `init()` and the `resize()` override. Cleanup: `browser.close()` in `close()` (frees the GL texture + CEF browser).

## 5. Input forwarding (all methods on MCEFBrowser)
```java
sendMouseMove(int x,int y); sendMousePress(int x,int y,int button); sendMouseRelease(int x,int y,int button);
sendMouseWheel(int x,int y,double amount,int modifiers);
sendKeyPress(int keyCode,long scanCode,int modifiers); sendKeyRelease(...); sendKeyTyped(char c,int modifiers);
```
Map from `mouseClicked / mouseReleased / mouseMoved / mouseScrolled / keyPressed / keyReleased / charTyped`, scaling
coords by GUI scale; call `browser.setFocus(true)` on interaction. `browser.useBrowserControls(false)` stops MCEF
hijacking Ctrl+R / Ctrl+± / Ctrl+0 / Alt+←→ so raw keys reach React. (1.21.1 `mouseScrolled` has (scrollX,scrollY) —
forward scrollY.)

## 6. JS ↔ Java bridge — `CefMessageRouter` + `window.cefQuery` (MCEF wraps nothing; use raw JCEF)
**Java → JS:** `browser.executeJavaScript(code, browser.getURL(), 0)` — e.g. `"window.__mcefPush(" + json + ")"`.
**JS → Java:** register a router against the raw client:
```java
CefMessageRouter router = CefMessageRouter.create(); // default jsQueryFunction = "cefQuery"
router.addHandler(new CefMessageRouterHandlerAdapter() {
    @Override public boolean onQuery(CefBrowser b, CefFrame f, long id, String request,
                                     boolean persistent, CefQueryCallback cb) {
        // parse request; do MC-world mutations via MinecraftClient.getInstance().execute(...) — onQuery is OFF-thread
        cb.success("ok"); return true;         // or cb.failure(code, msg)
    }
}, /* first = */ true);
MCEF.getClient().getHandle().addMessageRouter(router);   // getHandle() = raw org.cef.CefClient
```
JS side: `window.cefQuery({ request:'buttonClick:startBreeding', persistent:false, onSuccess:r=>{}, onFailure:(c,m)=>{} })`.
Teardown: `removeMessageRouter(router); router.dispose();`. Pattern: stream state **Java→JS** via `__mcefPush`; send
commands **JS→Java** via `cefQuery`. Persistent queries let the page subscribe and Java push repeatedly.

## 7. Load the UI from the jar — the built-in `mod://` scheme
MCEF auto-registers a `mod` scheme at init. `mod://<modid>/<path>` resolves to the classpath resource
`/assets/<modid>/html/<path>` (**lowercased**), served straight from the jar. So:
1. Put the built React bundle at `src/main/resources/assets/greenerpastures/html/` — `index.html`, `bundle.js`,
   `style.css` (keep filenames **lowercase**).
2. `MCEF.createBrowser("mod://greenerpastures/index.html", true)`.
3. Reference sub-resources relatively (`./bundle.js`) or `mod://greenerpastures/bundle.js`.
No localhost server / temp extraction needed. NOTE: MCEF runs CEF with `--disable-web-security` (local fetch/XHR fine;
the page runs with web security off). Fallback if the classloader lookup misfires: `file://` temp-dir extraction.

## 8. Native download UX
First run pulls the Chromium build from `https://mcef-download.cinemamod.com` (mirror configurable in
`config/mcef/mcef.properties`) → caches at `<gamedir>/mods/mcef-libraries/<platform>/`. **~100 MB+ per platform**
(the earlier research put the per-OS ceiling near ~236 MB). Shows a progress menu; defers init until done. On
offline/failure `isInitialized()` stays false and creation throws → **always guard on `isInitialized()`**.
`skip-download=true` + manually-supplied natives is possible.

## Gotchas (1.21.1)
1. Declare `"depends":{"mcef":"*"}` — `mcef-fabric` isn't bundled. 2. Compile vs runtime split (never compile against
`mcef-fabric`). 3. The shipped 1.21.1 example screen is **Mojmap**; translate to Yarn (`GuiGraphics`→`DrawContext`,
`getGuiScale()`→`getScaleFactor()`, `onClose()`→`close()`) — the 1.20.1 `BasicBrowser.java` is the Yarn naming
reference. 4. Texture id is 0 until first paint — guard the draw. 5. Keep the flipped V (top-down BGRA buffer) or the
page renders upside-down. 6. `onQuery` runs **off** the main thread → marshal world mutations via `execute(...)`. 7.
`useBrowserControls(false)` if React needs Ctrl/Alt combos. 8. Plan the offline-download-fail path. 9. `mod://` path is
lowercased + classloader-relative under `resources/assets/<modid>/html/`. 10. `--disable-web-security` is global. 11.
Per-OS natives cache at `<gamedir>/mods/mcef-libraries/`. 12. **GL friction with Sodium + Iris** (which the Shedmon pack
runs) is the reason MCEF was parked — smoke-test rendering under both before committing. 13. Source of truth: CinemaMod/mcef
`1.21.1` branch + CinemaMod/java-cef `master`.

_Sources: github.com/CinemaMod/mcef (1.21.1 branch), github.com/CinemaMod/java-cef (master),
github.com/CinemaMod/mcef-fabric-example-mod._
