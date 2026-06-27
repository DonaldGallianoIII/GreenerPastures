# Shiny Egg Highlighter (Fabric, MC 1.21.1)

A **client-side, visual-only** mod that paints shiny Cobbreeding eggs **gold** in any
inventory/storage screen — so scanning your egg bank becomes "glance and grab the glowing
ones" instead of hovering thousands of eggs one at a time.

Why it can work: your server runs Cobbreeding with **egg-data encryption off** (that's why
you can hover and see shiny), so the shiny flag is in the client-side item data. This mod
reads it and highlights the slot. It only **reads and draws** — it never moves items or
acts for you, so it's the ban-safe QoL category (like a minimap), not a macro/bot.

## Build
1. Install **JDK 21** (required for MC 1.21.x).
2. From this folder: `./gradlew build` (Windows: `gradlew.bat build`).
   - First run needs the Gradle wrapper. If `gradlew` is missing, run `gradle wrapper`
     with a local Gradle 8.8+, or open the folder in IntelliJ (it'll set Loom up).
3. Grab the jar from `build/libs/shiny-egg-highlighter-1.0.0.jar` (use the non-`-sources` one).
4. Drop it in your instance's `mods/` folder. Requires **Fabric API** (you already run it).

## Use
- Open any chest/storage screen — **shiny eggs get a gold border + tint.** Done.
- **First-time calibration (10 sec):** hold an egg in your main hand and press the dump key
  (**default `\`**, rebindable under Controls → "Shiny Egg Highlighter"). It prints the item
  id, the `custom_data` NBT, and the tooltip lines to chat + the log, and whether it currently
  detects shiny. Hold a **known shiny** egg and confirm "detected shiny? true".

## If detection is off
The detector (`ShinyEggDetector.java`) tries two things: read the shiny flag from
`custom_data` NBT, then fall back to scanning the tooltip for "shiny". The tooltip fallback
should work out of the box. If it mis-fires, use the dump output to fix:
- **Wrong/ missed egg item:** adjust `looksLikeEgg()` to the real id you saw printed.
- **Shiny key has an odd name or lives in a registered component:** adjust `nbtHasShinyTrue()`
  (it currently recursively matches any key containing "shiny" that's true).
- **Tooltip wording is unusual** (e.g. "Shiny: ✔"): tweak the string check in `tooltipSaysShiny()`.

## Notes / next ideas
- Mappings target **Yarn 1.21.1**; if Loom can't resolve `yarn 1.21.1+build.3`, bump it to a
  valid build in `gradle.properties`. Class/method names (`HandledScreen.drawSlot`, etc.) are
  Yarn names — adjust if you switch to Mojmap.
- Performance: detection runs per visible slot per frame. Fine for inventories; if a giant
  Sophisticated bank ever stutters, add a tiny per-stack cache keyed on the stack instance.
- Possible v2: a counter in the screen title ("3 shiny eggs here"), or a hotkey to auto-pull
  highlighted eggs into the hotbar (⚠️ that last one crosses into automation — check rules).
