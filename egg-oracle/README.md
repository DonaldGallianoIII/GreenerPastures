# EggOracle

A clean, configurable **shiny-egg odds calculator** for Cobblemon breeding — in a modern
popup, not an inventory-slot UI.

Set your server's shiny rate and Masuda multiplier, describe your pasture setup, and
EggOracle shows your real **per-egg odds**, **shinies per day**, **average time to shiny**,
and **chance of a shiny within N hours**. Because not every server runs 1/8192, everything
is configurable up front (with presets).

Client-side and read-only — it never moves or deletes items. Works on any server.

## Usage
- Press **=** to open (rebindable: Options → Controls → EggOracle). `O` is avoided — Cobblemon already uses it.
- Pick a **preset** (Vanilla 1/8192, Cobbreeding, or Custom) or type your own numbers.
- Inputs: base shiny rate, Masuda ×, different-OT toggle, number of breeding pairs,
  avg egg time (min), and the "chance within N hours" window.
- Results update live on the right.

## Egg Culler (in-container overlay)
Open any chest/bank and EggOracle tints every Cobbreeding egg by quality, reading the hatchling's
real data straight off the item (shiny + IVs, baked in at breeding):
- **gold** = shiny · **light blue** = keeper · **red** = cull · faint white = egg whose IVs couldn't be read.
- Each egg shows its **IV total** (out of 186) in the corner; a summary line sits above the GUI:
  `Eggs N  ★shiny  keep K  cull C`.
- Keep/cull rule (default): keep if shiny, **or** IV total ≥ 120, **or** ≥ 3 perfect (31) IVs — else cull.
- Press **C** while a container is open to toggle the overlay.

Reads via Cobbreeding's own API (`EggUtilities.extractProperties`) — no NBT spelunking; if Cobbreeding
isn't present it falls back to shiny-by-name. Read-only, so it's safe on any server.

## The math
Mirrors Cobbreeding's `calcShiny()`: `shinyRate` is a denominator and Masuda divides it
when parents have different Original Trainers.

```
effectiveRate = baseRate / (differentOT ? masudaMult : 1)
pPerEgg       = 1 / effectiveRate
eggs/hour     = pairs * 60 / eggTimeMin
shinies/day   = eggs/hour * 24 * pPerEgg
P(>=1 in t h) = 1 - (1 - pPerEgg)^(eggs/hour * t)
```

## Build
```bash
export JAVA_HOME="$HOME/jdks/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$HOME/gradle-dist/gradle-8.10/bin:$PATH"
gradle build -Porg.gradle.java.installations.paths="$JAVA_HOME"
# -> build/libs/eggoracle-0.1.0.jar
```

## Roadmap
- **v0.2** — auto-read active pastures (real Pokémon + OTs) so it calculates from your
  live setup instead of manual input. (Needs confirming OT data is client-accessible.)
- Disk-persisted profiles; shiny-charm / per-pair breakdown.
- Polish pass for Modrinth/CurseForge listing (icon, screenshots, metadata).

## License
MIT — see [LICENSE](LICENSE).
