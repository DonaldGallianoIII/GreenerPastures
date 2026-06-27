# Cobblemon Mod Suite — Status & Roadmap
_Updated 2026-06-22_

Three client-side **Fabric 1.21.1** mods (MIT) headed to **Modrinth + CurseForge**.
Build/deploy: built in this repo, jar staged to the Desktop, moved into the live mods folder by hand (never edit the running game dir).

---

## ✅ Shipped — built + staged (awaiting next relaunch to test)

### 🌊 HydroGrid *(brand new today)*
- 9×9 water-coverage preview on a held bucket (lit ground overlay)
- seamless next-bucket ghost grid (zero-waste 9-block spacing)
- auto-outlines water you've already placed (audit a field)
- **Field Fill** — two corners → the *minimum* water spots to cover the whole field; type them (`N`) or stamp in-world (`V`); spots check off green as you fill them
- Keys: `H` toggle · `N` field panel · `V` stamp corner

### 🥚 EggOracle *(grew to a 5-tool suite)*
- shiny-odds calculator (Masuda + same-OT, custom GUI)
- **Egg Culler** — highlights eggs in any container by shiny + IV; shiny always priority; gold shinies / light-blue keepers
- **Farm Dashboard** — editable pasture roster → per-species shiny %, shinies/day, time-to-shiny; persisted
- **Pasture Finder** — click a species → highlights it in-world
- **Pasture Fill Check** — boxes under-staffed pastures *(pending in-game test)*
- Keys: `=` open · `K` clear finder · `J` pasture check · `C` cull toggle

### 🏗️ PokeSnack Planner *(updated today)*
- mine-to-bedrock base + 17×17 spawn platform + ±64 vertical band overlay
- live home-block detection
- clean HUD that hides when idle
- Keys: `P` anchor · `L` platform

---

## ⏳ Pending
- **Relaunch & test all three** — the live mods are still old versions until reload.
- **Key test — EggOracle `J`:** does the pasture tethering sync to the client? The HUD self-reports on relaunch. This gates EggOracle's "WMS" direction.

## 🔮 Future / backlog
- **HydroGrid:** terraced/multi-level Field Fill (currently flat-plane only); planting-progress overlay (empty vs planted + counter).
- **EggOracle WMS** (if `J` syncs): live pasture map, collection routes, roster-vs-world census; slotting optimizer + Masuda-OT audit; gender / Ditto-needed column.
- **Release chores:** Forge / 1.20.1 port decision; Modrinth GIF (Field Fill) + dashboard screenshots.
- **Community requests** — posted to the Shedmon Discord 2026-06-22; collecting feedback before release.

## 🚫 Ruled out (server-side walls — won't ship as client mods)
- **Hopper egg-sorting by shiny/IV** — impossible in vanilla (eggs share an item ID, differ only in NBT). The Egg Culler is the workaround.
- **Client-side AOE auto-planting** — server caps interaction reach (~4.5 blocks) and anti-cheat flags mass-interaction. Needs a server-side plugin / farming-mod item.

> Design line: these mods **read and show**; they don't perform server-authoritative actions.
