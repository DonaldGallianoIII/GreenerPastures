# 📡 Notebook Data Contract — React ↔ Minecraft (WS bridge)

_The exact JSON the React console renders from, and the actions it sends back. This IS the current server
payload set (`com.greenerpastures.notebook.net.*`) re-expressed as JSON — build the React app against this and it
drops straight in. Transport = `ws://127.0.0.1:<port>` (see `MCEF_REACT_SPEC.md`). Mock values below are from a
real world, so they render true-to-life. `glow` this._

## Transport & envelopes
- **Connect:** `ws://127.0.0.1:<ds_port>?ds_token=<t>` — `ds_port`/`ds_token` arrive as URL query params (prod, injected by MCEF) or Vite env (dev).
- **Server → client** (pushed on connect, then whenever that channel's data changes):
  ```json
  { "type": "state", "channel": "<name>", "data": { … } }
  ```
- **Client → server** (button/interaction):
  ```json
  { "type": "action", "channel": "<name>", "action": "<ACTION>", "payload": { … } }
  ```
- **Handshake:** on connect the server sends `{ "type": "hello", "channels": ["status","storage","compiler","pastures","augmenter","biobank"] }`.
- Actions are **fire-and-forget**; the server applies them and pushes a fresh `state` frame for the affected channel(s). The client never mutates its own state optimistically — it re-renders from the next `state`.

---

## Channel `status` — the always-on status bar
```json
{ "type": "state", "channel": "status",
  "data": { "data": 97036, "gpu": 64, "daemonOn": false } }
```
| field | type | meaning |
|---|---|---|
| `data` | int (long) | player's Data balance |
| `gpu` | int | count of GPU items in inventory |
| `daemonOn` | bool | is a powered Daemon held |

_No actions._ Pushed ~1×/s while open, but **only re-sent when a value changed** (client should treat any `state` as authoritative).

---

## Channel `storage` — Harvester loot (the Storage tab)
```json
{ "type": "state", "channel": "storage",
  "data": {
    "capacity": 2147483647,
    "items": { "cobblemon:silk_scarf": 12, "minecraft:iron_ingot": 340, "minecraft:copper_ingot": 288, "cobblemon:leftovers": 3 }
  } }
```
| field | type | meaning |
|---|---|---|
| `items` | map<itemId, int(long)> | stored loot, keyed by MC item id |
| `capacity` | int (long) | per-item cap (int-limit; effectively "huge") |

**Actions:**
```json
{ "type":"action", "channel":"storage", "action":"PULL_ONE", "payload": { "item": "minecraft:iron_ingot" } }
{ "type":"action", "channel":"storage", "action":"PULL_ID",  "payload": { "item": "minecraft:iron_ingot" } }
```
- `PULL_ONE` → withdraw one stack of that item to inventory. `PULL_ID` → withdraw **all** of that item.

---

## Channel `compiler` — the Daemon buff loadout
```json
{ "type": "state", "channel": "compiler",
  "data": {
    "hasDaemon": true,
    "daemonOn": true,
    "drainPerSec": 3.5,
    "installed": { "fortune": 3, "haste": 2 },
    "catalog": [
      { "id": "fortune",  "label": "Fortune",  "category": "ENCHANT", "cap": 3, "costPerTier": 0.75 },
      { "id": "haste",    "label": "Haste",    "category": "EFFECT",  "cap": 3, "costPerTier": 0.50 },
      { "id": "magnet",   "label": "Item Magnet","category": "HOOK",  "cap": 3, "costPerTier": 0.40 }
    ]
  } }
```
| field | type | meaning |
|---|---|---|
| `hasDaemon` | bool | a Daemon is in inventory (if false, render the "no Daemon" empty state) |
| `daemonOn` | bool | is it powered on |
| `drainPerSec` | float | total Data/s the current loadout drains |
| `catalog` | list | every buff you can install |
| `catalog[].id` | string | stable buff id (used in actions) |
| `catalog[].label` | string | display name |
| `catalog[].category` | string | `ENCHANT` / `EFFECT` / `HOOK` (group headers) |
| `catalog[].cap` | int | max tier (0 = disabled by config) |
| `catalog[].costPerTier` | float | Data/s per tier |
| `installed` | map<buffId, int> | current tier per installed buff (absent = 0) |

**Actions:**
```json
{ "type":"action", "channel":"compiler", "action":"SET_BUFF", "payload": { "buff": "fortune", "tier": 2 } }
{ "type":"action", "channel":"compiler", "action":"TOGGLE_DAEMON", "payload": {} }
```
- `SET_BUFF` sets that buff's tier (server clamps to `[0, cap]`). `TOGGLE_DAEMON` flips power on/off.

---

## Channel `pastures` — read-only pasture monitor
```json
{ "type": "state", "channel": "pastures",
  "data": { "pastures": [
    { "name": "Eevee Shiny Farm", "dim": "minecraft:overworld", "pos": 1234567890,
      "tier": "GREENER", "eggCount": 4,
      "pairs": [ "Eevee ♀ × Eevee ♂ · Breeding", "Ditto × Eevee ♀ · Ready", "Eevee × — · Incomplete" ] }
  ] }
}
```
| field | type | meaning |
|---|---|---|
| `pastures[].name` | string | pasture display name |
| `pastures[].dim` | string | dimension id |
| `pastures[].pos` | int (long) | packed BlockPos (opaque key; use with `name` for display) |
| `pastures[].tier` | string | Kernel tier (`COPPER`…`GREENER`, or `"no Kernel"`) |
| `pastures[].eggCount` | int | eggs waiting to collect (tray + overflow queue) |
| `pastures[].pairs` | list<string> | **pre-formatted** `"A × B · Status"` lines; Status ∈ {`Breeding`,`Ready`,`Idle`,`Incomplete`} — color by the trailing word |

_Read-only — no actions (you modify a pasture at the pasture in-world, not from the console)._ Snapshots are captured when the player opens a pasture with the wand, so the list = pastures they've visited.

---

## Channel `augmenter` — Kernel augment slots
```json
{ "type": "state", "channel": "augmenter",
  "data": {
    "hasKernel": true, "tier": "GREENER", "slotsUsed": 2, "slotCap": 4,
    "catalog": [
      { "type": "SHINY",     "label": "Shiny Odds",  "slotCost": 1, "applied": true },
      { "type": "SPEED",     "label": "Breed Speed", "slotCost": 1, "applied": true },
      { "type": "IV_FLOOR",  "label": "IV Floor",    "slotCost": 1, "applied": false },
      { "type": "EV",        "label": "EV Spread",   "slotCost": 1, "applied": false }
    ]
  } }
```
| field | type | meaning |
|---|---|---|
| `hasKernel` | bool | a Kernel (Pasture Upgrade) is in inventory (else render empty state) |
| `tier` | string | Kernel tier label |
| `slotsUsed` / `slotCap` | int | filled vs total augment slots (draw N pips) |
| `catalog[].type` | string | augment id (used in actions) |
| `catalog[].label` | string | display name |
| `catalog[].slotCost` | int | slots consumed (currently always 1) |
| `catalog[].applied` | bool | already on this Kernel |

**Actions:**
```json
{ "type":"action", "channel":"augmenter", "action":"APPLY_AUGMENT",  "payload": { "type": "IV_FLOOR" } }
{ "type":"action", "channel":"augmenter", "action":"REMOVE_AUGMENT", "payload": { "type": "SHINY" } }
```
- APPLY is server-gated on free slots (grays out at cap). GPU/Data cost is **deferred** — no cost yet.

---

## Channel `biobank` — kept eggs (competitive cards)
```json
{ "type": "state", "channel": "biobank",
  "data": {
    "total": 3,
    "entries": [
      { "species": "eevee", "shiny": true,  "ivs": [31,31,31,31,31,31], "evs": [0,0,0,0,0,0],
        "nature": "timid", "gender": "female", "ability": "adaptability" },
      { "species": "eevee", "shiny": false, "ivs": [31,31,31,0,31,31], "evs": [252,0,0,0,4,252],
        "nature": "jolly", "gender": "male", "ability": "run_away" },
      { "species": "ditto", "shiny": false, "ivs": [31,0,31,31,0,31], "evs": [0,0,0,0,0,0],
        "nature": "", "gender": "", "ability": "" }
    ]
  } }
```
| field | type | meaning |
|---|---|---|
| `total` | int | total kept eggs (across all species) |
| `entries[].species` | string | lowercase species id (group + title-case for display) |
| `entries[].shiny` | bool | render ★ |
| `entries[].ivs` | int[6] | **HP · Atk · Def · SpA · SpD · Spe** order; 31 = perfect (Σ/186) |
| `entries[].evs` | int[6] | same order; show the EV row only if any > 0 |
| `entries[].nature` / `gender` / `ability` | string | may be `""` (best-effort read — render conditionally, don't assume present) |

_Browse-only in v1 — no actions yet (search/sort is client-side; deposit happens at a BioBank block in-world)._ Server caps the bank at 256 eggs, so the list is bounded.

---

## Notes for the React side
- **Group the console into tabs** matching these channels: BioBank · Harvester(`storage`) · Pastures · Compiler · Augmenter · Dashboard(future `analytics`), + the `status` bar always visible.
- Treat every `state` frame as the full truth for that channel (replace, don't merge). The bridge only sends a frame when the data actually changed, so you won't get spammed.
- Empty states matter: `compiler.hasDaemon=false`, `augmenter.hasKernel=false`, empty `storage.items`, empty `biobank.entries`, empty `pastures` — each has a distinct "hold X / do Y" message in the current design.
- Strings like `nature`/`gender`/`ability` can be `""` — hide the chip rather than showing blank.
- This contract is stable; if a channel needs a new field I'll version it here first.
