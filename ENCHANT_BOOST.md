# ⚡ Beyond-Vanilla Enchant Boost — implementation spec (Daemon ENCHANT buffs)

_How the Daemon grants enchant levels **past the vanilla max** (Fortune VI, Efficiency VIII, …) while held +
fed. Verified against the real 1.21.1 yarn jar (CFR/javap on the loom-remapped Minecraft jar). This is the
hardest buff category — read before writing the mixin. Companion to `~/pokemonthink/AUGMENTS_AND_BUFFS.md`._

> **Status:** researched + locked, **not yet built**. The pure cores already resolve ENCHANT tiers; what's
> missing is the MC adapter (a mixin) that puts the resolved `+tier` onto the player's gear. One timing detail
> (does equipment-sync run inside the tick window?) is being confirmed before the mixin is written — see
> **Open question** at the bottom.

## The decision: edit the stack's enchantment component, NOT a `getLevel` mixin
In 1.21.1's data-driven enchantment system, **`EnchantmentHelper.getLevel` is NOT the central runtime path.**
Only **Fortune** and **Looting** actually flow through it at runtime (via loot functions/conditions). The other
nine worker enchants are driven by **attribute modifiers** or **`EnchantmentValueEffect`s**, both of which read
the **raw stored level int** through `forEachEnchantment(ItemStack, Consumer)` / `applyAttributeModifiers(...)`
— and those injection points **have no entity/player parameter** (verified descriptors), so you can't tell whose
item it is from inside them. A `getLevel` mixin would therefore cover **2 of 11** enchants and would also
wrongly inflate **anvil/grindstone/repair** costs (they call `getLevel` too).

**The one number every path consumes is the stored level** — `ItemEnchantmentsComponent.getLevel(entry)` just
returns `enchantments.getInt(entry)` with **no max clamp**. The component constructor validates `0 ≤ level ≤
255`, so "Fortune VI" / "Efficiency VIII" are **legal component values that take full effect at runtime**. So:

> **Transiently rewrite the held + equipped stacks' `ItemEnchantmentsComponent`, bumping each worker enchant by
> the Daemon tier, then restore the originals — bracketed to a single server player tick.** Non-destructiveness
> comes from the **tick discipline** (symmetric, unconditional restore), not from the engine.

Attribute-only (approach c) cleanly covers Efficiency/Respiration/Swift Sneak/Soul Speed but has **no analog**
for Fortune/Looting/Unbreaking/Lure/Luck of the Sea/Feather Falling — so it can't be the single mechanism.

## Verified 1.21.1 signatures (from the remapped yarn jar)
```
net.minecraft.enchantment.EnchantmentHelper
  static int getLevel(RegistryEntry<Enchantment>, ItemStack)
      (Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/item/ItemStack;)I
  static int getEquipmentLevel(RegistryEntry<Enchantment>, LivingEntity)
      (Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/entity/LivingEntity;)I
  static ItemEnchantmentsComponent apply(ItemStack stack, Consumer<ItemEnchantmentsComponent.Builder> applier)

net.minecraft.enchantment.ItemEnchantmentsComponent
  int getLevel(RegistryEntry<Enchantment>)   // return this.enchantments.getInt(entry); NO clamp
  // ctor throws if any level < 0 or > 255
```
Edit via the engine's own builder path (never hand-mutate the shared component):
```java
EnchantmentHelper.apply(stack, b -> b.set(enchantEntry, Math.min(255, original + tier)));   // apply
EnchantmentHelper.apply(stack, b -> b.set(enchantEntry, original));                           // restore exact
```

## Mixin shape (sketch — write the real thing carefully)
```java
@Mixin(ServerPlayerEntity.class)
abstract class DaemonEnchantBoostMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void gp$boost(CallbackInfo ci) {
        DaemonEnchantBoost.applyIfFed((ServerPlayerEntity)(Object)this);   // reads DaemonBuffs.lastPaid tiers
    }
    @Inject(method = "tick", at = @At("RETURN"))
    private void gp$restore(CallbackInfo ci) {
        DaemonEnchantBoost.restore((ServerPlayerEntity)(Object)this);      // ALWAYS, symmetric, by saved level
    }
}
```
`applyIfFed`: read the player's paid ENCHANT tiers from `DaemonBuffs.lastPaid` (already billed there — the
enchant boost is delivered, not separately charged, once `ENCHANT` ids join `DaemonBuffs.SUPPORTED`); for each
target equipped stack (main hand + tool + the four armor pieces, per which enchant lives where), cache the
**exact original level by stack identity**, then `apply(stack, b -> b.set(entry, min(255, original+tier)))`.
`restore`: write the **saved level back** (don't subtract tier — subtraction drifts if something else changed
the stack mid-tick); restore unconditionally even on early-out/exception.

## Coverage — one mechanism (component edit) covers all 11
| Enchant | Runtime path (verified) | Covered? | Note |
|---|---|---|---|
| Fortune | `ApplyBonusLootFunction`/`TableBonusLootCondition` → `getLevel` | ✅ | ore/uniform formulas scale past vanilla; `TableBonus` *chance list* clamps to its size (leaf/flint-style) |
| Looting | `EnchantedCountIncreaseLootFunction` → `getEquipmentLevel` | ✅ | scales linearly (in catalog only as a gathering example; combat loot is out of design scope) |
| Efficiency | `attributes` → `PLAYER_MINING_EFFICIENCY` (levels²) | ✅ | client-predicted (see desync gotcha) |
| Unbreaking | `item_damage` value effect (RemoveBinomial) | ✅ | |
| Respiration | `attributes` → `OXYGEN_BONUS` | ✅ | |
| Feather Falling | `damage_protection` value effect (3.0/lvl) | ✅ | |
| Frost Walker | `location_changed` ReplaceDisk | ✅* | radius **hard-clamped to 16** — boost helps only up to that |
| Soul Speed | `location_changed` → `MOVEMENT_SPEED` | ✅ | |
| Swift Sneak | `attributes` → `SNEAKING_SPEED` (0.15/lvl) | ✅ | |
| Lure | `fishing_time_reduction` (5.0/lvl) | ✅ | vanilla soft-caps practical effect; engine still scales |
| Luck of the Sea | `fishing_luck_bonus` (1.0/lvl) | ✅ | |

## Gotchas (the expensive ones — dupe/desync)
- **Restore must be symmetric + unconditional.** Always run on RETURN, even on early returns/exceptions, or you
  strand a boosted component (= a duped enchant on a live server). Cache the exact original level **by stack
  identity**; restore by writing the saved value, not by subtracting tier. Clamp `original+tier` to 255 (ctor
  throws above it).
- **Never let the boosted component reach a save or an inventory-sync as the "real" stack.** The tick-wrap is
  chosen precisely because save + the entity-tracker sync are claimed to run **outside** the `tick()` window —
  **this is the one thing being confirmed (Open question).**
- **Client desync.** Mining-speed animation + enchant tooltips are client-side. `mining_efficiency` is a synced
  attribute; a server-only boost ⇒ client predicts a slower break, server allows the fast one (jittery, server
  wins). Tooltips read the component client-side and will show vanilla numbers unless the client's copy is also
  boosted. For a clean feel, optionally also boost the client's held stack (cosmetic), or accept the mismatch.
- **Attribute modifiers are diff-cached** (`getEquipmentChanges()` re-applies only when `!ItemStack.areEqual`).
  Editing the component makes the stack compare unequal ⇒ a remove/re-add of the modifier (stable ids, updates
  correctly) **every apply and every restore**. Symmetric restore keeps it consistent; watch perf if many
  Daemon holders. If the per-tick churn is too heavy, the alternative is a **persistent-while-held** boost
  removed only on unequip/unfeed/logout — but that needs a save-strip hook (more moving parts, more dupe
  surface). Start with the tick-wrap.

## Wiring when built
1. Add the ENCHANT `BuffId`s to `DaemonBuffs.SUPPORTED` (so they're billed when delivered).
2. `DaemonEnchantBoost` helper: map `BuffId → RegistryKey<Enchantment>` + which equipment slot(s) to touch;
   read paid tiers from `DaemonBuffs.lastPaid`; apply/restore caches keyed by player → (stack identity → level).
3. The mixin (above) in `greenerpastures.mixins.json` (server-side mixin list).
4. GpLog `buff enchant_boost` lines; QA row (⚠ dupe/desync: validate restore-safety in a throwaway world —
   boost, relog, drop/swap the tool mid-boost, die — before any live deploy).

## Open question (being confirmed before the mixin is written)
Does the player's equipment-change **client sync / NBT save** happen **inside** `ServerPlayerEntity.tick()`
(within HEAD→RETURN, exposing the boosted component) or in a **later, separate pass** (entity tracker /
`ServerEntity.sendChanges`, after all entity ticks — safe)? If inside, use a narrower seam that brackets only
the loot/attribute consumption, not the sync. Answer pending from the jar investigation; do not write the mixin
until resolved.
