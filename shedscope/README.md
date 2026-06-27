# ShedScope

Client-side **finder/ESP** mod for the *Shedmon* instance (Fabric 1.21.1). Highlights
ores, chests, spawners, vaults and Deep-Dark markers with colored boxes, draws a tracer
line to the nearest one, and shows a live distance readout on the HUD.

Client-only (it reads blocks your client already has). Your server allows X-ray, so it
scans loaded chunks through walls.

## Controls (rebindable in Options → Controls → ShedScope)
- `\` — master toggle (on/off)
- `[` — toggle ore highlights
- `]` — toggle loot/spawner highlights

## What it finds
- **Ores:** diamond, emerald, ancient debris, gold, iron, copper, redstone, lapis, coal,
  nether quartz, budding amethyst (+ deepslate variants)
- **Loot:** chests, trapped chests, barrels, spawners, trial spawners, vaults,
  suspicious sand/gravel
- **Deep Dark:** sculk shriekers, sculk catalysts, reinforced deepslate (ancient-city marker)

Edit the target list / colors in `src/main/java/com/shedmon/shedscope/Targets.java`.
Scan radius (default 80 blocks) and cap live in `Scanner.java`.

## Rebuild
The toolchain isn't on PATH globally, so point at the downloaded JDK + Gradle:

```bash
cd shedscope
export JAVA_HOME="$HOME/jdks/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$HOME/gradle-dist/gradle-8.10/bin:$PATH"
gradle build -Porg.gradle.java.installations.paths="$JAVA_HOME"
# or: ./gradlew build   (wrapper is committed)
```

Output: `build/libs/shedscope-0.1.0.jar` → copy into the instance `mods/` folder.

## Known limitations / next steps
- **Boxes/tracer are depth-tested** — they're occluded by terrain (you see them when a
  hit is in line of sight). True see-through-walls ESP needs a custom no-depth render
  layer; that's the planned v0.2.
- No config screen yet (toggles are keybinds; targets are code).
- A* walking paths were deferred in favor of the straight-line tracer (per your call).
