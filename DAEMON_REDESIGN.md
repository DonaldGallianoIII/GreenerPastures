# 🔮 Daemon Redesign — "Compile-Your-Own Buffs"

_From QA **BUG-004** (2026-06-30). Replaces the current model — *hold a fed Daemon → get the entire 15-buff suite at
a global Mk tier, billed continuously* — with a player-built, opt-in loadout. Logic-first + no-UI-first per house
rules. `glow DAEMON_REDESIGN.md`._

## The new model (Deuce's design)
1. **Compile your loadout.** Put the Daemon into the **Compiler** (the same block that installs augments onto
   Kernels) and compile the specific buffs you want, each at a chosen level. The Daemon carries that loadout as a
   data component (like a Kernel's `Augments`). A cheap **Feather-Falling-only** Daemon is a valid, minimal build.
2. **Right-click = ON/OFF.** Right-clicking the held Daemon toggles it on; **ON shows the enchanted-item glint**
   (visual confirm). Off = inert, zero drain.
3. **Works from your inventory / backpack.** Once on, it grants its installed buffs while it's anywhere in the
   player's inventory or backpack — **not just in-hand**.
4. **Drain = only what's installed.** While on, it drains the summed per-second cost of **only the compiled buffs**
   (each buff's fixed cost), never the whole catalog. One-buff Daemon = cheap; loaded Daemon = pricier. You only
   pay for what you chose.
5. **No chunk-loading, ever.** It only acts for an **online player** whose inventory holds it (always loaded). It
   **never force-loads a chunk** — a Daemon sitting in a chest in an unloaded chunk simply does nothing. _(Deuce:
   "as long as it's turned on that's fine, unless that means we load a chunk — I don't want that.")_

## What this removes
- The auto-enabled full `SUPPORTED` suite — no more "all passives on just by holding it."
- The global **Mk I/II/III** tier + the creative sneak-RC level cycle — replaced by **per-buff compiled levels**.
- Continuous billing for unused **event** buffs — you install + pay for only what you want.

## Build order (logic-first, no-UI-first)
1. **Core (MC-free, headless-tested):** `DaemonLoadout` = a `BuffId → level` map + an `on` flag. Resolver bills
   `Σ (level × costPerSec)` over the **installed** buffs only — a generalization of today's `BuffResolver`
   (which resolves the fixed `SUPPORTED` set at one global tier) to a per-item loadout.
2. **Item:** store the loadout + on-state as Daemon data components; right-click toggles `on` and sets
   `minecraft:enchantment_glint_override = on`; tooltip lists installed buffs + the Data balance.
3. **Grant/drain adapter:** the per-second settle loop scans the player's **full inventory** for an **ON** Daemon
   (not just hands), grants its installed buffs, drains its summed cost. Never force-loads. Online players only.
4. **No-UI install path:** `/gp daemon set <buff> <level>` · `list` · `clear` (mirrors `/gp augment`) — so the
   whole thing is testable before the Compiler GUI exists.
5. **Compiler GUI:** extend the Compiler block to accept a Daemon and compile buffs onto it (publish-phase UI; the
   command covers function first). A prime **owo-ui** candidate (`PORTING_WEB_UI.md` Option C).

## Open questions (for Deuce)
- **Compile cost:** is installing a buff onto the Daemon a **one-time cost** (Data / materials at the Compiler), or
  is the only cost the **per-second drain** while it's on? _(Leaning: running-drain-only for v1; add an install cost
  later if the economy needs a sink.)_
- **Per-buff caps:** keep the **+3 ceiling** per buff (Mk-III-equivalent), or let the Compiler push higher for more
  drain? _(Default: keep +3.)_
- **Which buffs are compilable:** the same `SUPPORTED` set (the worker-not-fighter catalog), correct?

## Migration / QA notes
- Existing Daemons in-world carry no loadout → they'd grant nothing until compiled. Fine for a test world (re-`/give`
  + compile). Flag for the changelog when public.
- The current `daemon_level` component + `/gp data`-fed drain test path get superseded; `/gp daemon` becomes the new
  test affordance. The buff *delivery* code (mixins, attribute reconcile, magnet, etc.) is reused unchanged — only
  **what's resolved + billed** flips from "global suite" to "this item's loadout."

---

## 🔧 IMPLEMENTATION-READY BUILD GUIDE (BUG-004) — verified 2026-06-30, ready to code
_All APIs below were read/verified this session. Decisions defaulted (flag if wrong): **compile cost = running-drain-
only** (no upfront), **per-buff cap = each buff's own `maxTier` / +3**, **compilable set = `DaemonBuffs.SUPPORTED`**._

### Current code it replaces (exact behavior, read this session)
- **`DaemonItem.use()`** (`economy/DaemonItem.java`): sneak+creative RC cycles the `DAEMON_LEVEL` int (Mk I→II→III);
  plain RC shows balance + Mk. `levelOf(stack)` = `getOrDefault(DarkEconomy.DAEMON_LEVEL,1)` clamped `1..TIER_CEILING`.
  **→ retire the Mk-level cycle; RC becomes the ON/OFF toggle.**
- **`BuffResolver.resolve(cfg, daemonLevel, Set<BuffId> applicable)`** (`buff/BuffResolver.java`): loops `BuffId.values()`,
  keeps those in `applicable` + enabled, `tier = min(daemonLevel, min(maxTier, TIER_CEILING))`, `drain = Σ tier×costPerSec`,
  returns `ResolvedBuffs(Map<BuffId,Integer> tiers, double cost, int level)`. **→ add a loadout-driven variant.**
- **`DaemonBuffs.settle()`** (`buff/DaemonBuffs.java`): once/sec → `heldDaemonLevel(player)` (checks **main+off hand only**
  for `DarkEconomy.DAEMON`) → if level>0 `resolve(cfg, level, SUPPORTED)` → bill via `DataStore.tryDebit` (fractional
  `drainCarry`) → `applyEffects` + `DaemonAttributeBuffs.reconcile` → cache `lastPaid` (drives per-tick `runHooks` magnet +
  `paidBuffs(player)` read by the enchant mixins). **`SUPPORTED`** = the 15 deliverable buffs (HASTE, SATURATION, MAGNET,
  FORTUNE, AUTO_SMELT, XP_BOOST, VEIN_MINE, POTION_DURATION, LURE, LUCK_OF_THE_SEA, FROST_WALKER, LOOTING + the 3
  attribute buffs via `DaemonAttributeBuffs.DELIVERED`). **→ scan whole inventory for an ON Daemon; resolve its loadout.**
- **`DarkEconomy.init()`** (`economy/DarkEconomy.java`): registers `DAEMON` item + `DAEMON_LEVEL`
  (`ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build()`). **→ add 2 components.**
- **`BuffId`** (`buff/BuffId.java`): catalog of 18; fields `(id, label, BuffCategory, registryId, vanillaMax, gathering)`.
  Confirm/`byId(String)`. Per-buff cap from `BuffConfig.settingOf(id).maxTier()`.

### Component-codec patterns (verified this session — mirror these)
- Map component (for `DaemonLoadout` = `Map<BuffId,Integer>`): mirror **`Augments`** — `Codec.unboundedMap(Codec.STRING,
  Codec.INT).xmap(...)` keyed by `BuffId.id` (drop unknown keys), packet `PacketCodecs.map(HashMap::new, STRING, VAR_INT, MAX)`.
- Boolean component (the ON flag): `ComponentType.<Boolean>builder().codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL).build()`.
- **Enchant glint:** set vanilla `DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE` (Boolean) on the stack for the shiny
  look (verify the yarn name at build). `ItemStack.set/get/remove(ComponentType)` all available. `EvSpread`/`Augments`
  show both codec styles (RecordCodecBuilder + tuple-6 worked; unboundedMap worked).

### Files to write/change
1. **NEW `buff/DaemonLoadout.java`** (pure): wrap `Map<BuffId,Integer>` (installed buff→level) + `CODEC`/`PACKET_CODEC`
   (mirror `Augments`, key by `BuffId.id`, clamp level ≥0, drop non-`SUPPORTED`/unknown ids). `withLevel(id,lvl)`,
   `level(id)`, `levels()`, `isEmpty()`, `NONE`. Headless-tested.
2. **`buff/BuffResolver.java`** — add `resolveLoadout(BuffConfig cfg, Map<BuffId,Integer> loadout, Set<BuffId> deliverable)`:
   for each `(id,lvl)` in loadout, skip if not deliverable / cfg-disabled, `tier = min(lvl, min(cfg cap, TIER_CEILING))`,
   `drain += tier×costPerSec`, collect. Return `ResolvedBuffs`. (Keep old `resolve` so existing tests pass.) **+ tests.**
3. **`economy/DarkEconomy.java`** — register `DAEMON_LOADOUT` (`ComponentType<DaemonLoadout>`) + `DAEMON_ON`
   (`ComponentType<Boolean>`). Leave `DAEMON_LEVEL` registered (back-compat; unused).
4. **`economy/DaemonItem.java`** — `use()`: toggle `DAEMON_ON` (default false), mirror to `ENCHANTMENT_GLINT_OVERRIDE`,
   message = ON/OFF + balance + 1-line loadout summary. Add `isOn(stack)` + `loadoutOf(stack)`. Drop the Mk cycle.
   Tooltip: ON/OFF + installed buffs.
5. **`buff/DaemonBuffs.java`** — replace `heldDaemonLevel` with `firstActiveDaemon(player)`: scan `player.getInventory()`
   (main incl. hotbar + offhand) for a `DAEMON` stack with `isOn`==true; return its loadout (or null). In `settle()`: none →
   `clear`; else `resolveLoadout(cfg, loadout, SUPPORTED)` → bill/apply as today. (Sophisticated-Backpacks inv = v2 note;
   vanilla inv is always loaded → no chunk-load.)
6. **NEW `economy/DaemonCommand.java`** (`/gp daemon`): `set <buff> <level>` (validate `BuffId` ∈ SUPPORTED + level ≤ cap;
   write `DAEMON_LOADOUT` on the held Daemon), `list`, `clear`, optional `on`/`off`. Mirror `AugmentCommand`. Wire
   `DaemonCommand.init()` into `GreenerPastures.onInitialize()` by the other `/gp` commands.
7. **QA_RESULTS:** flip BUG-004 → ✅ built; add a Q-row for in-game test (compile + headless only — needs MC verify).

### Test plan (headless)
`DaemonLoadoutTest` (withLevel / clamp / unknown-key-drop / NONE) + `BuffResolverTest` additions (resolveLoadout: drain =
only installed × cost; per-buff cap; non-deliverable skipped; empty → NONE; config-disabled skipped).

### Watch-outs
- **Order in `settle`:** read loadout → resolve → bill → apply, same as today; the ON-check + inventory-scan are the only
  new front gates. Keep the broke-account `clear` + fractional `drainCarry`.
- **`paidBuffs(player)`** must still return the resolved set so the enchant mixins keep working — unchanged plumbing.
- **maxCount(1)** on the Daemon, but a player could hold 2 stacks — use the FIRST ON one (don't sum).
- Migration: existing in-world Daemons have no loadout + no ON flag → grant nothing until compiled + toggled (fine for a
  test world; changelog note for public).
