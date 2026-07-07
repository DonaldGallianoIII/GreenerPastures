# ⚡ Beyond-Vanilla Enchant Boost — implementation spec (Daemon ENCHANT buffs)

_How the Daemon grants the **effect** of enchant levels past the vanilla max (Fortune VI, Efficiency-style
mining, …) while held + fed. Verified against the real 1.21.1 yarn jar (CFR/javap on the loom-remapped MC jar +
full tick-loop trace). This is the hardest buff category — read before writing any mixin. Companion to
`~/pokemonthink/AUGMENTS_AND_BUFFS.md`._

> **Status:** researched + **final approach locked**, not yet built. The pure cores already resolve ENCHANT
> tiers (billed once `ENCHANT` ids join `DaemonBuffs.SUPPORTED`); what's missing is the MC delivery. ⚠ This is
> the highest-risk category to QA — but the chosen approach is **non-destructive by construction**, so there is
> **no dupe vector** (it never writes an ItemStack).

## ❌ Rejected: editing the stack's enchant component on a tick wrap
The intuitive plan — bump the held/armor stacks' `ItemEnchantmentsComponent` `+tier` and restore it — is **not
viable**, verified by tracing the 1.21.1 server loop:
- `ServerPlayerEntity.tick()` (world entity pass, `tickWorlds` line 946) does **not** call `super.tick()`. The
  equipment sync + attribute re-apply happen **later**, in a separate pass: `networkIo.tick()` (line 957) →
  `ServerPlayNetworkHandler.tick()` → `ServerPlayerEntity.playerTick()` → `super.tick()` →
  `LivingEntity.tick()` → **`sendEquipmentChanges()`**. So wrapping `tick()` HEAD/RETURN is exposure-safe but
  **inert** — you restore before any consumer runs.
- Wrapping `playerTick()` reaches the consumers, **but** `sendEquipmentChanges()` → `getEquipmentChanges()`
  (deep `!ItemStack.areEqual` diff) → **builds + sends `EntityEquipmentUpdateS2CPacket` synchronously inside
  that window** (line 2365, from `stack.copy()`), and sets the `syncedHandStack`/`syncedArmorStack` fields to
  the boosted copy. So the boosted item **is broadcast to clients on the apply-tick** (flicker + a window where
  the client holds boosted NBT). No tick-bracket avoids this.
- (NBT save is safe either way — `writeCustomDataToNbt` is only called from `PlayerManager` autosave/logout,
  never from a tick — but the client-exposure above kills the approach regardless.)

## ✅ Final approach: split by consumer, never touch the ItemStack
1. **Attribute-type enchants → direct `EntityAttributeModifier`s on the player.** Efficiency
   (`PLAYER_MINING_EFFICIENCY`), Respiration (`OXYGEN_BONUS`), Swift Sneak (`PLAYER_SNEAKING_SPEED`). Add a
   modifier to the player's `AttributeContainer` while held + fed, remove when not — done right in the
   `DaemonBuffs` per-second settle loop (**no mixin**). Server-authoritative, synced via the *attribute* tracker
   (`EntityAttributesS2CPacket`, the correct channel), never rewrites a stack ⇒ zero dupe/NBT risk. _(Soul Speed
   is conditional on soul blocks and has no clean flat-attribute analog — defer. Mining-speed is also already
   covered by the **Haste** EFFECT buff, so the Efficiency modifier is optional polish.)_
2. **Fortune (and Looting) → read-only `EnchantmentHelper.getLevel` interception, loot-scoped.** Pure read
   interception — never writes the stack ⇒ inherently sync-safe + save-safe. Two small mixins:
   - **Scope mixin** on the block-drop path that *has the breaker entity* —
     `Block.getDroppedStacks(BlockState, ServerWorld, BlockPos, BlockEntity, Entity, ItemStack)`: `@Inject` HEAD,
     if the `Entity` is a `ServerPlayerEntity` holding a fed Daemon, stash their resolved tiers in a
     `ThreadLocal`; `@Inject` RETURN (and `@Inject` at any throw if practical) clears it. Server loot gen is
     single-threaded → the `ThreadLocal` is safe and self-clearing.
   - **Read mixin** on `EnchantmentHelper.getLevel(RegistryEntry<Enchantment>, ItemStack)` `@Inject(at=RETURN,
     cancellable=true)`: if the `ThreadLocal` is set and the entry is a boosted enchant, `cir.setReturnValue(orig
     + tier)`. Gated by the ThreadLocal so it fires **only** during block-drop loot resolution — never tooltips,
     never anvil/grindstone/repair (which also call `getLevel`).
   - Looting's loot uses `getEquipmentLevel(weapon, attacker)` on the **mob-death** path, not the block path, so
     the block-scope won't cover it; treat Looting as v2 (and it's combat-adjacent anyway).

## Verified 1.21.1 signatures
```
net.minecraft.enchantment.EnchantmentHelper
  static int getLevel(RegistryEntry<Enchantment>, ItemStack)
      (Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/item/ItemStack;)I   // ← the read mixin
  static int getEquipmentLevel(RegistryEntry<Enchantment>, LivingEntity)              // Looting path (v2)
net.minecraft.block.Block
  getDroppedStacks(BlockState, ServerWorld, BlockPos, BlockEntity, Entity, ItemStack) // ← the scope mixin
      // VERIFY exact descriptor/return (List<ItemStack>) against the jar before writing the @Inject
EnchantmentHelper.getLevel returns ItemEnchantmentsComponent.getLevel(entry) = enchantments.getInt(entry); no clamp.
```
_(Always re-`javap` `Block.getDroppedStacks` + `EnchantmentHelper.getLevel` from the loom-remapped jar before
authoring the mixin — descriptors over memory.)_

## Coverage (this split, v1)
| Enchant | v1 delivery | Note |
|---|---|---|
| **Fortune** | ✅ `getLevel` read-mixin (loot-scoped) | **the marquee** — more ore/block drops while mining with a fed Daemon |
| Efficiency | ✅ via **Haste** EFFECT (already shipped); optional `PLAYER_MINING_EFFICIENCY` modifier | mining speed |
| Respiration | ◐ optional `OXYGEN_BONUS` attribute modifier | easy add in the settle loop |
| Swift Sneak | ◐ optional `PLAYER_SNEAKING_SPEED` modifier | easy add |
| Looting | ⏳ v2 | mob-death path (`getEquipmentLevel`); combat-adjacent |
| Unbreaking, Lure, Luck of the Sea, Feather Falling, Frost Walker | ⏳ v2 | `EnchantmentValueEffect`s read the raw component level via `forEachEnchantment` (no entity ctx); need per-effect interception — lower value, deferred |
| Soul Speed | ✗ | conditional on soul blocks; no clean flat analog |

## Wiring when built
1. Add `BuffId.FORTUNE` (+ optional attribute ids) to `DaemonBuffs.SUPPORTED` so it's billed; expose a read
   accessor on `DaemonBuffs.lastPaid` (e.g. `paidTier(ServerPlayerEntity, BuffId)`) for the mixins — same
   thread, no locking.
2. `DaemonEnchantBoost` holder: the `ThreadLocal`, the `BuffId → RegistryKey<Enchantment>` map, the
   fed-Daemon-holder check (reuse `DaemonBuffs`/`DaemonItem.levelOf`).
3. Two mixins in `greenerpastures.mixins.json` (server list — confirm whether the project already pulls in
   MixinExtras; if not, use vanilla `@Inject ... cancellable` with `CallbackInfoReturnable`, not
   `@ModifyReturnValue`).
4. GpLog `buff enchant_boost` line on a boosted `getLevel`; QA row. **QA focus** (even though non-destructive):
   confirm Fortune drops scale ONLY while mining with a fed Daemon held, NOT in tooltips/anvil, NOT for other
   players, and that nothing leaks after the break (ThreadLocal cleared). No dupe path exists by construction.
