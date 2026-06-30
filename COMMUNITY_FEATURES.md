# 🌐 Greener Pastures — Community Feature Wave (the #minecraft-chat thread)

_Locked + brainstorm features from Deuce's open feature-request thread (2026-06-29). Companion to
`FEATURES_V2.md`. Build order + engineering read below; these are NOT started. House rules apply (logic-first,
config-driven, commit-not-deploy, every MC change → a `QA_PENDING.md` row)._

## At a glance

| # | Feature | Status | Build size | Reuses |
|---|---|---|---|---|
| C1 | **Egg → Pokéball cosmetics** (spend eggs/Data → breed in a chosen ball) | locked | M (core) + UI later | the `BALL` augment (Q32) + the Data economy |
| C2 | **Chicken-egg-style throws** (throw a Pokémon egg → spawns the wild mon) | locked | M | `EggReader`; a new thrown projectile |
| C3 | **MissingNo Easter egg** (the centerpiece) | locked | mix — see below | the augment/tether system; rides C2 |
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

## C2 — Chicken-egg-style throws  (the carrier for C3)
Right-click a Cobblemon egg → it lobs **our own thrown-egg projectile** (arcs like a chicken egg, carries the egg's
species/IVs/shiny in NBT) → on impact, spawn the **wild** version of that mon + consume the egg. **NOT** hijacking
the vanilla chicken egg (would tangle every real chicken-egg throw).
- Needs: a registered projectile entity + a thin renderer (render the egg item); the impact→`spawnWild` logic
  (read species via `EggReader`, spawn a wild `PokemonEntity`).
- ⚠ **Verify first:** the Cobbreeding egg item's current right-click behavior, so "throw" doesn't stomp hatching.

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

### ⚠ The one real unknown — RESEARCH before estimating
Cobblemon **custom-species** support (datapack + resourcepack) is real; the question is the **model-cycling** —
can we swap an entity's *rendered model* on a timer (via species/aspects/a custom renderer)? If clean → the full
glitch. If deep → fallback to a **static corrupted/scrambled MissingNo texture** (still very on-theme).

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
1. **Research pass** — Cobblemon custom-species + model-cycling feasibility (de-risks the C3 centerpiece). Read-only.
2. **C2 throw** (the projectile) — the carrier; buildable now.
3. **C3 core** — the 1/8192 crack/shiny logic (pure, testable) + the `MISSINGNO_CHANCE/SHINY` augment/tether
   (slots into our system).
4. **C3 species** — per the research (cycling model, or static glitch texture) + the RB-form sprites (assets).
5. **C3 item-dup** — once Deuce picks faithful-vs-themed + the config shape.
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
