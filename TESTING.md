# 🧪 Testing — headless logic tests (no Minecraft boot)

_Greener Pastures · **2026-06-28**. Deuce's call: test game logic without booting the game constantly, and do **UI last**. `glow TESTING.md`._

## Run them
```
cd greener-pastures && ./gradlew test
```
Plain **JUnit 5**, runs in seconds, **no game window**. (JDK: `~/jdks/jdk-21.0.11+10`.) Report: `build/reports/tests/test/index.html`.

## The principle — logic-first, UI last
1. Put game logic in **Minecraft-free core classes** (plain data in / plain data out) — like `client/ui/DaemonController` (that's why the desktop studio can run it too).
2. **Unit-test the core** (instant, no MC).
3. Wire a **thin MC adapter** (ItemStack/NBT/block-entity/networking) around the core.
4. **Build UI LAST**, once the logic is proven.

The thinner the MC layer, the more of the mod is testable in milliseconds.

## Why plain JUnit (not `fabric-loader-junit`)
`fabric-loader-junit` boots the whole Fabric loader, which reads our `fabric.mod.json`, sees our **hard `cobblemon` dependency**, can't find Cobblemon in the test runtime (it's compile-only), and refuses to start. Plain JUnit skips all that. Minecraft classes ARE on the test classpath (the `Augments` test loads MC codecs fine), so tests *can* use MC types; a test that needs **registries** bootstrapped can call `SharedConstants.createGameVersion(); Bootstrap.initialize();` in a `@BeforeAll`.

## Covered now
| Test | What it proves |
|---|---|
| `BreedingTierTest` (4) | tier→pairs mapping, the **8-pair ceiling**, monotonic tiers |
| `AugmentsTest` (4) | bounded shiny-proc clamp (0–100% — can't explode) |
| `ShinyOddsTest` (9) | the bonus-reroll math extracted from `CobbreedingBridge` (effective odds = calcShiny, proc gates, never double-counts a shiny) |
| `EggQueueTest` (8) | per-pasture FIFO: pause-when-full-**never-evict**, drain-to-tray, snapshot/restore |
| `CatchUpTest` (7) | bounded offline breeding (cycles elapsed, "banks ~24 then waits") |
| `DaemonControllerTest` (3) | pairing model: pre-wired buckets, 8-pair clamp, half-bucket ≠ pair |
| `ValueRuleTest` (6) | "valuable egg" guard (shiny / perfect-IV / IV-total) |
| `RenderSelectionTest` (3) | keep-vs-render split |
| `RenderLedgerTest` (6) | Send-to-Renderer preview: per-species counts + independent safety flags |
| `IvFilterTest` (5) | per-stat IV gate (Daemon FILTER node) |

**55 green** as of 2026-06-28.

## Extraction backlog — ✅ cores done (2026-06-28)
- [x] **Shiny-odds math** → `ShinyOdds` (CobbreedingBridge now delegates to it).
- [x] **Per-pasture FIFO egg-queue** → `EggQueue`.
- [x] **IV/EV filtering + "valuable egg"** → `IvFilter` + `ValueRule`.
- [x] **Away-from-chunk catch-up** → `CatchUp`.
- [~] **BioBank** — value / selection / render-ledger cores done (`ValueRule` · `RenderSelection` · `RenderLedger`); what remains is the **adapter** that lifts the live store off `ItemStack` + wires these in.

## Next: the thin MC adapters (need an occasional in-game check)
The cores are proven; wiring them into the live game is the remaining work — and the ONLY part that wants Minecraft:
- `MultiPairBreeder` → produce via `ShinyOdds`, enqueue into `EggQueue`, drain to the tray; offline via `CatchUp`.
- BioBank → build `EggSummary` from egg `ItemStack`s (extend `EggReader`), use `RenderSelection` + `RenderLedger` for the Send flow.
- Then UI, last.
