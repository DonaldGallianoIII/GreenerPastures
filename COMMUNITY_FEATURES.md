# 🌐 Greener Pastures — Community Feature Wave (the #minecraft-chat thread)

_Locked + brainstorm features from Deuce's open feature-request thread (2026-06-29). Companion to
`FEATURES_V2.md`. Build order + engineering read below; these are NOT started. House rules apply (logic-first,
config-driven, commit-not-deploy, every MC change → a `QA_PENDING.md` row)._

## At a glance

| # | Feature | Status | Build size | Reuses |
|---|---|---|---|---|
| C1 | **Egg → Pokéball cosmetics** (spend eggs/Data → breed in a chosen ball) | locked | M (core) + UI later | the `BALL` augment (Q32) + the Data economy |
| C2 | **Chicken-egg-style throws** (throw a Pokémon egg → spawns the wild mon) | locked · **✅ research done** | M | `EggReader`; a `ThrownItemEntity` subclass |
| C3 | **MissingNo Easter egg** (the centerpiece) | locked · **✅ research done** | M (cycling = Tier A, **no client code**) | the augment/tether system; Cobblemon's illusion system; rides C2 |
| C4 | **Cake crafting** (eggs as a cake ingredient) | locked | **S** (a recipe) | — |

---

## C1 — Egg economy: Pokéball cosmetics
Spend bred eggs to redeem a ball cosmetic for breeding (e.g. Cherish Ball = 5,000 eggs). **The effect already
exists** — the `BALL` augment sets an egg's hatch ball (Q32, deployed). This adds:
- the **cost/redemption layer** — pay → unlock a ball → it applies to your breeding;
- a **UI selector** for the ball (Deuce: "same pattern as the visual-scripting menu" → publish-phase GUI).
- **Open Qs:** pay in **literal eggs or Data** (the Renderer already turns eggs→Data, so Data is the natural
  currency)? **Unlock-once** (5k → that ball forever) or **per-batch**? Per-player unlock set, persisted.
- _Functional core is buildable no-UI now (a `/gp ball` command or auto-apply); the picker is publish-phase._

## C2 — Chicken-egg-style throws  (the carrier for C3) — ✅ RESEARCH DONE, ready to build
Right-click a Cobblemon egg → lob our own projectile carrying the egg → on impact spawn the **wild** mon + consume
the egg. **Verified approach (research pass, 2026-06-29):**
- **Egg item = `ludichat.cobbreeding.PokemonEgg`** (one item class; no projectile/dispenser exists in Cobbreeding).
- **Trigger = Fabric `UseItemCallback`, gated on SNEAK-right-click** (do NOT override `PokemonEgg.use` — foreign
  class). `PokemonEgg.use` is a no-op on a normal (encryption-on) server and only a decrypt-debug when encryption is
  off; sneak-right-click is fully unclaimed, so it sidesteps even that edge. Filter with `EggReader.isEgg`, consume
  the event. **Hatching is fully decoupled** (it only ticks in a *player* inventory via `inventoryTick`), so a
  thrown egg can't hatch mid-air.
- **Carry the whole `ItemStack`**, not hand-copied NBT: subclass `net.minecraft.entity.projectile.thrown.ThrownItemEntity`
  (vanilla `EggEntity`'s base) — its `setItem(stack)` stores + serializes the full stack, so all six Cobbreeding
  components (`cobbreeding:egg_info` etc.) ride along automatically; `getStack()` on impact is byte-identical.
- **Impact → spawn wild:** server-side `onCollision` → `EggUtilities.extractProperties(getStack())` → `PokemonProperties`
  → spawn a wild ownerless `PokemonEntity` (`PokemonProperties.createEntity(world)` — Pass-1 confirms the exact
  spawn signature) at the hit point → `discard()` the projectile + particle/sound. Fail-safe (drop the egg back),
  same never-throws discipline as `CobbreedingBridge`.
- Needs: register the EntityType + a flying-item renderer (reuse a stock one). **No blockers.**

## C3 — MissingNo Easter egg  ⭐ the centerpiece
On each egg-throw (C2), a roll for the glitch:
- **1/8192** to crack a **MissingNo** instead of the normal wild mon → spawns wild. _(Rotom asked 1/64; final call
  1/8192 to mirror the classic full-odds shiny rate.)_
- **1/8192 shiny** on top → a shiny MissingNo ≈ **1-in-67-million** = cryptid tier. The mass-breeding engine
  ("endlessly filling the BioBank") is what makes it reachable — that's the whole point.
- **The mon:** a custom Cobblemon species whose **rendered model randomly cycles the WHOLE Pokédex every ~5s**
  (Deuce: not a fixed list — Char→Zoro→Entei were just examples; it shuffles fully-evolved mons). The "can't decide
  what it is" glitch.
- **Boost augment / soul tether:** add `MISSINGNO_CHANCE` + `MISSINGNO_SHINY` to `AugmentFunction` — a fed tether
  amplifies + drains Data, **exactly like Shiny/IV Floor. One constant each, zero new architecture.** For the void-farmers.

### ✅ RESEARCH DONE — the cycling glitch is TIER A (clean), zero client code
The make-or-break (cycle the rendered model through the Pokédex every ~5s) is **fully feasible, pure server-side —
no mixin, no custom renderer, no resourcepack work for the cycling itself** (base-game species already ship every
needed asset). Cobblemon syncs `SPECIES` + `ASPECTS` via the entity DataTracker *every tick*
(`PokemonServerDelegate.tick → updateTrackedValues`), and the client live-swaps the model on that sync
(`PokemonClientDelegate.onSyncedDataUpdated → setCurrentModel`).
- **Glitch effect (recommended): Cobblemon's OWN illusion system** (Zoroark's disguise mechanic — thematically
  perfect: a glitch that's literally an illusion that can't hold a form). On a ~100-tick server timer, cycle a
  random fully-evolved species: `entity.getEffects().setMockEffect(new IllusionEffect(PokemonProperties.parse(
  "species=<x> shiny=<flag>"), scale))`. The real entity keeps its identity/stats; only the *rendered* model
  flickers. Pool = `PokemonSpecies.getImplemented()` filtered to no-evolutions.
- **Guaranteed fallback (A1):** `pokemon.setSpecies(x); pokemon.setShiny(flag); pokemon.updateAspects()` — swaps the
  real species too (stats/hitbox), but the sync chain is verified rock-solid. Use if the illusion packet ever
  doesn't push during QA.
- **Custom species (optional):** purely **data-driven, zero Java** — one `data/<ns>/species/missingno.json` +
  resolver/poser/geo/texture in a resourcepack. Only needed if we want a distinct dex entry / a static base glitch
  texture; the cycling itself doesn't require it.

### Spawn API (shared with C2 — verified)
`PokemonProperties.Companion.parse("<species> level=N shiny=<flag>", " ", "=").create()` → `Pokemon` →
`pokemon.sendOut(serverWorld, vec3dPos, illusionEffect_or_null, null)` spawns a **wild** entity (and can take the
`IllusionEffect` *at spawn*, so MissingNo can spawn already-glitching). Same call powers the C2 throw's
impact→wild-mon. (Build-only variant: `PokemonProperties.createEntity(world)` then `world.spawnEntity`.)

### 🧩 RB form sprites (brainstorm — authenticity)
Instead of / alongside random Pokédex cycling, optionally weight the **canonical RB glitch forms** as sprites:
RB Normal block · Ghost (Lavender) · Aerodactyl fossil · Kabutops fossil · Yellow Normal · a Bulbasaur Gen-3
placeholder nod (gold-tinted if customizable). Deuce's lean: easy swaps since they're glitch 2D images.

### 💎 The item-duplication Easter egg  (THE thematic keystone)
MissingNo's real claim to fame: encountering it **adds +128 to the quantity of the 6th bag item** (RAM garbage).
Mapped to MC: **encounter the void mon → hotbar slot 6 dupes.** This is *perfectly* on-theme — the mod's whole
thesis is conjuring matter/Data from nothing to feed the Renderer/Daemon; MissingNo literally **conjures matter
from the void.** ⚠ **Balance/config:** item-dup is the one thing that can wreck a server economy, so it MUST be
**admin-config-gated** (on/off, amount, maybe an item allow/block-list). Two flavors to pick from:
- **Faithful:** dupes whatever's in slot 6 (+128 or a config N) — chaotic, authentic, gated by the 1/8192 rarity.
- **Themed:** scopes the dupe to **eggs / Data** (matter for the Renderer) → MissingNo becomes a literal "matter
  fountain" feeding the Daemon. Even more on-theme; far less exploitable.

## C4 — Cake crafting
Eggs usable as a **crafting ingredient for cakes** (Wevic req, confirmed). A recipe (datapack); likely a custom
recipe that accepts any Cobblemon egg item. **Smallest of the set** — a near-pure resource add.

---

## Proposed build order
1. ✅ **Research pass — DONE** (both passes clean): C2 throw is ready; C3 cycling is **Tier A** via the illusion
   system, no client code; the spawn API (`sendOut`) is verified + shared by both.
2. **C2 throw** — `ThrownItemEntity` subclass carrying the egg stack + `UseItemCallback` (sneak-throw) + impact→`sendOut`.
3. **C3 core** — the 1/8192 crack/shiny logic (pure, testable) + the `MISSINGNO_CHANCE/SHINY` augment/tether
   (slots into our system) + the illusion-cycle server timer.
4. **C3 species (optional)** — a tiny data-only `missingno.json` if we want a distinct base + the RB-form sprite
   weighting (assets; Deuce's lean = easy 2D swaps). The cycling works without it.
5. **C3 item-dup** — once Deuce picks faithful-slot-6 vs themed-eggs/Data + the config shape.
6. **C1 ball economy** (core now, UI publish-phase) · **C4 cake** (trivial, anytime).

## Decisions Deuce owes (no rush)
- **Item-dup:** faithful slot-6 vs themed eggs/Data; default on/off; config knobs.
- **Ball cost:** eggs or Data; unlock-once or per-batch.
- **MissingNo model:** go for the cycling glitch (pending research) or ship the static-texture fallback first.

## MissingNo lore (for sprite + mechanic authenticity)
MissingNo isn't a real mon — it's an empty Pokédex index the game renders by reading garbage RAM. The RB forms
come from the Old Man / Cinnabar-coast glitch: the player NAME is written into wild-encounter RAM and specific
letters decide the form (Ghost / Aerodactyl / Kabutops / scrambled block). The item-dup (+128 to the 6th item) is
its most famous side effect — the basis for the keystone Easter egg above.
