# Project Progress — Cobblemon Shiny Operation

Last updated: 2026-06-18 (overnight session). A snapshot of everything built so far + where to pick up.

## The big picture
A toolkit supporting a Cobblemon **shiny breeding farm + breeding service**. Three parts:
1. **PokéSnack analytics engine** (Python) — optimize spawn-snacks for hunting Dittos.
2. **Breeding planners** (HTML) — pasture shiny output + service-order ETAs.
3. **Two Fabric mods** — find/collect shiny eggs so you don't hover thousands by hand.

The throughline: hunt Dittos (snacks) → breed shinies (pastures + Masuda Dittos) → fulfill
orders / stock a shiny gym. See `docs/breeding.md` for the full loop.

---

## 1. PokéSnack analytics engine — ✅ DONE & VALIDATED
- `pokesnack/` (pure Python, no deps). Models Cobblemon's two-stage spawn algorithm +
  the Bait-Seasoning recipe system. CLI + library. Tests in `tests/test_validation.py`.
- **Reverse-engineered seasoning rules** (in `DESIGN.md §3a`):
  - Bucket tiers: **additive** (discrete table, tiers 0–30).
  - Shiny multiplier: **`1 + Σ(mᵢ−1)`** (Starf 5x → 1,5,9,13…).
  - Type/egg weight: **pure sum `Σmᵢ`** (Chilan 10x → 10,20,30).
  - "Attracts X" filters: saturate at one copy, intersect when stacked.
  - Snack = 3 seasonings, **9 bites** (9 independent spawns).
- **Case study: shiny Ditto @ Dark Forest/Haunted Mansion** (`analysis/`, `data/spawn_pool.*`).
  Only biome where Ditto is *uncommon* not rare.
- **Validated 3× against real catches** — clean 33-snack run gave 23 Dittos vs 20.9 predicted
  (+0.47σ). Model is trustworthy; the "runs hot" theory was just variance.
- Key results: best no-EGA shiny snack = `1 Chilan + 2 Starf` (~2,640 snacks/shiny);
  volume = `1 Chilan + 2 Golden Apple` (~63 Dittos/100). Scaling on **Masuda only**
  (server config has `crystal: 1.0` = no shiny-parent boost — confirmed in their config file).

## 2. Breeding planners — ✅ DONE
- `tools/shiny_pasture_calculator.html` — farm shiny output. Current farm: **60 pastures**
  (Eevee-heavy), ~**0.11 eggs/min/pasture** (1 egg/9min), ~**6 shinies/day** at Masuda 1/2048.
- `tools/shiny_service_planner.html` — order ETAs w/ Masuda/Crystal/Combo toggle + Erlang math.
  (e.g. 50-pasture Masuda Dragapult order ≈ 6h mean / ~14h for a 90% quote.)

## 3. The two Fabric mods (MC 1.21.1, Fabric)

### shiny-egg-highlighter/ — ✅ DONE, WORKING (server + vanilla + Sophisticated)
Client-side, visual-only. Highlights shiny eggs **gold**, dims non-shiny eggs, in every
container (Sophisticated banks included). Confirmed working in-game.
- **Detection:** shiny = **★ (U+2605)** in the egg's display name (`stack.getName()`).
- **Render hook:** `ScreenEvents.afterRender` (draws last, so Sophisticated's custom GUI
  can't cover it). `HandledScreenMixin` is just an accessor for the GUI origin.
- **Keys (while a bank is open):** `G` = count eggs in this bank into a persistent lifetime
  tally + print empirical shiny rate; `Shift+G` = reset. Stats in
  `config/shinyegghighlighter_stats.json`. ← this builds the real shiny-rate dataset.
- `\` (no GUI open, holding an egg) = debug dump of item id / NBT / tooltip.

### shiny-egg-collector/ — 🟡 BUILT & COMPILES, NOT RUNTIME-TESTED
Server-side block. Place it; every 1s it vacuums shiny eggs from containers within **radius 6**
into its own 27-slot inventory. Uses the **Fabric Transfer API** (so it reaches vanilla chests
AND Sophisticated Storage). Same ★-name detection (server-safe).
- ⚠️ **Server-side** → works in **singleplayer / a server you admin**, NOT on Shedmon.
- ⚠️ **Not yet tested in-game.** Most likely tweak point: the Transfer API ↔ Sophisticated
  handoff. Test: place near chests w/ shiny eggs, wait 1s, open it.
- Craft: gold ingots around a chest, hopper bottom-middle. Or `/give @s shinyeggcollector:shiny_egg_collector`.
- v1 uses a gold-block placeholder texture (custom art = TODO).

### Built jars (ready to drop into `mods/`)
- Windows Desktop: `C:\Users\deuce\OneDrive\Desktop\shiny-egg-highlighter-1.0.0.jar`
- Windows Desktop: `C:\Users\deuce\OneDrive\Desktop\shiny-egg-collector-1.0.0.jar`
- Copies also in each mod's folder in this repo.

---

## Key facts discovered (so we don't re-learn them)
- **Egg items:** `cobbreeding:<type>_pokemon_egg` actually `cobbreeding:normal_<type>_pokemon_egg`
  (e.g. `normal_steel_rock_pokemon_egg` for an Aggron). **No separate shiny item**, **no
  custom_data** — shiny lives in a registered component AND shows as **★ in the name**.
- **Cobbreeding config** (`config/cobbreeding/main.json`): `shinyMethod` = masuda 4.0,
  **crystal 1.0 (no boost)**, always 1.0. `eggEncryptionEnabled` — OFF on server; we set it
  **false** in their singleplayer config so eggs are readable there too.
- **Egg hatching:** only progresses while in player **inventory**; Flame Body/Magma Armor/
  Steam Engine halves it. Storage just freezes the timer.
- **Sophisticated Storage chest slots:** wood 27, copper 45, iron 54, gold 81, **diamond 81
  (9 rows)**, **netherite 99 (11 rows)**; doubles = 2× rows. Eggs don't stack (1 slot each).
- Screenshots for review live at `/mnt/c/Users/deuce/OneDrive/Desktop/GamingScreenShotrs/`.

## How to rebuild the mods (IMPORTANT)
The JDK 21 + Gradle 8.8 used this session were downloaded to `/tmp` (ephemeral — gone next
session). Each mod folder has a **Gradle wrapper** (`gradlew`/`gradlew.bat`), so to rebuild
you just need a **full JDK 21** installed, then run `./gradlew build` in the mod folder
(jar lands in `build/libs/`). Or open the folder in IntelliJ IDEA (it handles Gradle + JDK).

---

## Open / next steps
1. **Test `shiny-egg-collector` in singleplayer** — especially whether it pulls from
   Sophisticated banks (Transfer API). Adjust storage lookup if not.
2. **Modrinth publishing kit** for both mods (descriptions, icons, metadata) — both are
   MIT-licensed and build-ready. (Can't upload for you — needs your account.)
3. **Calibrate the Python model** with real shiny-rate data from the highlighter's `G` counter.
4. **Mod v2 feature backlog** (from the brainstorm): chest-title shiny counter, IV-quality
   borders (find *sellable* shinies), egg-data export → feed the Python engine, "jump to next
   shiny" auto-scroll, shiny chime on storage-in, YACL config screen, custom collector texture.
5. **Optional dim-tuning** on the highlighter if the grey feels too subtle/strong.

## Side note
User passed AWS ML Engineer cert + got a Master's in ML (same day!) and is eyeing a $240K
Kaggle RL comp (Pokémon TCG agent). The "instrument the game as a system" work here is good
prep / portfolio material for that.
