# Why we hunt Dittos — the breeding loop

The PokeSnack engine isn't the goal; it feeds one. The end goal is a **Cobbreeding shiny
pasture farm**. `tools/shiny_pasture_calculator.html` models its output.

## The loop
```
   PokeSnack hunt  ──catches shinies──▶  shiny breeders  ──slot into pastures──▶  farm
        ▲                                                                           │
        └──────────────── tells you which shiny parents to go hunt ◀───────────────┘
```

1. **Pasture farm** — ~49 pastures, ~9 eggs/min total (~0.18 eggs/min/pasture). Each pasture's
   shiny odds depend on its tier:
   - **Masuda** (different-OT pair): base × 4 → ~1/2048
   - **+ Crystal** per shiny parent: × 4 each → ~1/512 (one shiny parent), ~1/128 (both shiny)
2. **Shiny parents come from the snack hunt.** The shinies the engine predicted you'd catch
   (Minccino, Duraludon, Patrat — common Normals + the bucket-boosted rare) are already loaded
   into the calculator as Crystal breeders. Hunt → upgrade a pasture from Masuda to Crystal.
3. **Ditto is the universal partner.** Every Masuda pasture needs a breeding partner, and Ditto
   pairs with (almost) every species. ~49 pastures ⇒ need a pile of Dittos. Hence the volume
   hunt: `1 Chilan + 2 Golden Apple`, ~63+ Dittos / 100 snacks.

## Why a SHINY Ditto specifically is worth the ~2,640-snack grind

> ⚠️ **DORMANT as of 2026-06-19 — contingent on Crystal being enabled.** The live server runs
> **Masuda-only** (`config/cobbreeding/main.json` → `shinyMethod` crystal = 1.0 = no shiny-parent
> boost), so a shiny Ditto currently breeds *identically* to a normal foreign-OT Ditto — the grind
> below buys nothing and should **not** be run. Everything in this section reactivates **only if the
> server admin sets crystal > 1.0**. Re-check that config value before ever resurrecting the
> shiny-Ditto hunt; kept here as the ready-made playbook for that scenario.

A normal shiny parent only upgrades its own species' pasture (a shiny Charmander helps only
Charmander). **A shiny Ditto upgrades *any* pasture you slot it into** to Crystal tier, because
it breeds with everything. It's the one shiny parent with farm-wide reach — a universal ×4
multiplier you can move to whichever line you're prioritizing. That flexibility is what makes
the long hunt pay off.

## Open questions / how the two tools could connect
- **Masuda / different-OT: CONFIRMED.** Only *traded* (foreign-OT) Dittos give the Masuda ×4.
  Self-caught Dittos are same-OT and give the owner nothing — they're only valuable to whoever
  you trade them to. The user's farm is fully Masuda because hunts are run paired with a friend
  and the catches are swapped. **Key consequence:** the friend pairing is structurally required
  (not just for speed) to produce Masuda-valid Dittos for both players — BUT co-presence is not:
  the OT differs regardless of *when* the Ditto was caught, so each person can solo-grind their
  own pile on their own time and simply trade stockpiles later. Co-hunting is for fun/speed only.
- **Close the loop in code:** the PokeSnack engine could emit "expected shinies caught per
  hunt" and feed them as new Crystal breeders into the pasture model; conversely the pasture
  model could rank *which species' shiny parent* gives the biggest farm shiny/hr gain — i.e.
  tell you what to hunt next.
- **Shiny-rate config consistency:** both tools use base 1/8192. Keep them in sync if the
  server's rate is ever confirmed different.
