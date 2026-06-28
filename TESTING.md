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
| `BreedingTierTest` | tier→pairs mapping, the **8-pair ceiling**, monotonic tiers |
| `AugmentsTest` | the **bounded shiny-proc** math (clamps 0–100% — shiny can't explode) |
| `DaemonControllerTest` | pairing model: pre-wired buckets, 8-pair clamp, half-bucket ≠ pair |

## Extraction backlog (tangled with MC today → pull into testable cores)
- [ ] **Shiny-odds math** — `effectiveShinyOdds` / `maybeProcShiny` live inside `CobbreedingBridge` (MC + reflection). Extract the probability math into an MC-free `ShinyOdds` core → test the reroll thoroughly.
- [ ] **BioBank** — species bucketing, capacity, the **render-ledger** (per-species counts + shiny/perfect-IV safety flags). Mostly pure already; lift off `ItemStack`.
- [ ] **Per-pasture FIFO egg-queue** (the `EGG_STORAGE_DESIGN.md` queue) — pure data structure, very testable.
- [ ] **IV/EV egg filtering** + **"valuable egg" detection** (shiny OR 31-IV) — pure predicates.
- [ ] **Away-from-chunk catch-up** — elapsed-intervals → egg count math.

Each becomes: core class + its `*Test`, then a thin adapter.
