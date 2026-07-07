# 🥚 Egg Pipeline — the breeder reroute (block-free, eggs-as-data)

_2026-07-01. Build-ready spec for the Java-side change that makes a **Kernel'd + linked** pasture produce eggs
as **data straight into the owner's Notebook BioBank** — no tray, no physical egg, all remote while loaded.
Locked with Deuce. Companions: [[egg-database-project]] (BioBank), [[pasture-operator-claim]] (link=owner),
`NOTEBOOK_DATA_CONTRACT.md`. The React console already renders this end-state on mock data._

## 1. The rule — three states, gated on the Kernel + the link
| Pasture | Breeding | Eggs go to |
|---|---|---|
| **No Kernel** | Vanilla Cobbreeding (native ticker) | Real eggs in the pasture tray — hatch normally. *(Unchanged — `MultiPairBreeder` already skips Kernel-less pastures.)* |
| **Kernel, not linked** | Our multi-pair breeder | Real eggs in the tray (as today) — nothing is lost; **link it to go remote**. |
| **Kernel + linked** | Our multi-pair breeder | **Eggs as DATA → the owner's Notebook BioBank.** Keeper→BioBank, non-keeper→Data. No physical egg. |

**Ownership = the Notebook link** (already built — `PastureClaim`/`pd.owner`): a **locked, exclusive** claim bound to
the owner's UUID; only the owner unlinks; grief-proof. Losing/replacing the Notebook item doesn't break it (per-player
stores are UUID-keyed).

## 2. The reroute — the one real code change
`MultiPairBreeder.breedPairs(...)` today builds each egg via `CobbreedingBridge.buildEggForPair(pair, shape)` → an
egg `ItemStack` → `pd.eggQueue.offer(stack)` → drains to the tray. Change: **for a Kernel'd + linked pasture, route the
built egg to the BioBank instead of the queue/tray:**

```
BredEgg egg = CobbreedingBridge.buildEggForPair(pair, shape);   // unchanged — genetics/shiny/IV/EV/nature as now
if (pd.owner != null) {                                          // Kernel'd + LINKED → eggs as data
    ingestEgg(server, pd.owner, egg.stack());                    // route to BioBank / Data (§3), never placed
} else {                                                          // Kernel'd, UNLINKED → physical eggs (today's path)
    pd.eggQueue.offer(egg.stack());
}
```
No tray-slot cap on the data path → breeding never stalls on a full tray (capped only by the BioBank, §5). The
`EggQueue` + tray drain stay exactly as-is for the unlinked path.

## 3. Ingest — KEEP EVERYTHING (revised 2026-07-01, Deuce)
> **Superseded:** the BioBank keeps **every** egg — no auto-cull. Rendering an egg to Data is an *explicit* choice
> in the visual-scripting layer (wire an egg stream into a void/Data node), never automatic at ingest. `ingestEgg`
> just deposits the egg to the owner's BioBank; BioBank full → tray fallback. Per-species **sort** (ΣIV / per-stat /
> shiny) in the console lets you find the good ones. _(The original keeper-routing design is kept below for history.)_

### (historical) Ingest — keeper-routing (folds the Renderer's cull in at birth)
`ingestEgg(server, owner, eggStack)`:
1. Read the egg's card once: `EggCard c = EggReader.card(eggStack)` (species · shiny · IVs · EVs · nature · gender · ability).
2. **SACRED guard first:** if `c.shiny()` **or** the read failed (`c == null` / `!ivsKnown`) → **keep** (never cull a shiny or an unreadable egg — same 4-guard as the Renderer).
3. Else apply the keep rule `ValueRule.DEFAULT` (keep shiny OR ≥1 perfect IV):
   - **keep** → `BioBankStore.get(server).bankOf(owner).add(species, eggStack)` (as data).
   - **cull** → `DataStore.get(server).credit(owner, RenderValuation.dataFor(1, BASE_DATA_PER_EGG, enrichMult))` — the non-keeper becomes Data, exactly like the Renderer, on the pasture's enrichment multiplier.
4. Log one `GpLog` line (`egg_ingest` keep/cull) + an `Analytics` event (feeds the Dashboard).

> **Default = keeper-routing** (matches today's Renderer/BioBank split). If a player wants to review everything, a
> per-pasture **"keep all"** toggle (or an augment) can send every egg to the BioBank and let them cull manually —
> flagged as an easy follow-on, not v1.

## 4. Withdraw — getting a real egg back out (to hatch)
BioBank stores data; you hatch a real item. `biobank` gets a **WITHDRAW** action (contract): pull an entry → rebuild
the egg `ItemStack` from its stored data (`CobbreedingBridge` egg-build from the card / the stored properties) →
`offerOrDrop` into the player's inventory → remove the BioBank entry. (The React mock already does this optimistically.)

## 5. Edge cases
- **BioBank full** (cap 256/player): pause the data path for that owner — fall back to the tray (physical eggs) so
  nothing is lost, and surface it in the console (a "BioBank full" status). *(Mirror the `EggQueue` pause-don't-drop discipline.)*
- **Owner offline / chunk loaded:** the pasture only ticks while its chunk is loaded (breeder already guards
  `isChunkLoaded`); ingest writes to the owner's persistent per-player store regardless of whether they're online.
- **Unlink while Kernel'd:** reverts to the "Kernel, not linked" row — physical eggs in the tray again.

## 6. What's reused vs. new
| | |
|---|---|
| **Reused** | `PastureClaim`/`pd.owner` (link=owner, locked) · `CobbreedingBridge.buildEggForPair` (genetics) · `EggReader.card` · `BioBankStore` (per-player, capped) · `DataStore` + `RenderValuation` (cull→Data) · `ValueRule.DEFAULT` (keep rule) · `MultiPairBreeder` gating |
| **New** | the ~10-line reroute in `breedPairs` · `ingestEgg(...)` helper · `biobank` **WITHDRAW** handler (rebuild egg → inventory) · a "BioBank full" status field |
| **Retired** | the BioBank **block** deposit path + (for linked pastures) the Renderer **block** — both fold into the ingest tick |

## 7. Contract additions (for `NOTEBOOK_DATA_CONTRACT.md`)
- `biobank` channel: add action **`WITHDRAW { index }`** → materializes a real egg into the player's inventory.
- New **`inventory`** channel (36 MC slots: `[0..8]` hotbar, `[9..35]` main) + **`storage` `DEPOSIT { slot }`** (already mocked).
- `compiler`/`augmenter`: **`gpuCost`** per row; GPU **consumed on activate, never refunded**.

## 8. Build order (Java, after the WS bridge)
1. `ingestEgg` helper + the `breedPairs` reroute (behind the `pd.owner != null` branch). Headless-test the keep/cull routing on a fake card set.
2. BioBank `WITHDRAW` (rebuild egg → inventory) + the C2S action.
3. "BioBank full → tray fallback" + console status.
4. QA: Kernel + link a pasture → eggs appear in the console BioBank, none in the tray; shiny always kept; non-keepers tick Data up; withdraw → a real egg in hand; unlink → eggs return to the tray.
