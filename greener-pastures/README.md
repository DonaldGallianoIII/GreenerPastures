# Greener Pastures — *A Data Science Mod*

A Cobblemon breeding suite for **Fabric 1.21.1** that unifies pasture management and egg
automation **and hands you the data**: every breeding / hatch / cull event is recorded
locally, aggregated, and surfaced as in-game charts plus one-click **CSV / self-contained
HTML dashboards** that players and server admins can pull for themselves.

> Your data, your machine — nothing phones home.

## Modules
- **pasture/** — PastureKeeper (per-pasture no-wander + loot collector) and Better Pasture (multi-pair breeding)
- **egg/** — egg odds + culler + finder, shiny highlighter, shiny-egg auto-collector
- **analytics/** — local event log, incremental aggregation, CSV + HTML export *(the data science)*
- **core/** — shared config, networking, keybinds

## Build
On a JRE-only box, point the bundled wrapper at the downloaded JDK:

    JAVA_HOME=~/jdks/jdk-21.0.11+10 ./gradlew build

Output: `build/libs/greenerpastures-<version>.jar`

## Requires
Cobblemon (and Cobbreeding for breeding features). Both are referenced **compile-only** and
are **never bundled** — players install them separately.

## License
MIT © DonaldGalliano. Built for Cobblemon (MPL-2.0) and Cobbreeding (MIT) — thanks to those teams.
