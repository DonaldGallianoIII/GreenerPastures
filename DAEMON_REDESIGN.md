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
