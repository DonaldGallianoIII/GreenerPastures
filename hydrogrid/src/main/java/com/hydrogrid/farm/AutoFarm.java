package com.hydrogrid.farm;

import com.hydrogrid.Field;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.NetherWartBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client-side auto harvest + replant within vanilla reach. Turn it on (',') and walk your rows.
 *
 * Targets (by exact registry id, so no Cobblemon compile dependency — properties are read generically):
 *   - Vivichoke   ({@code cobblemon:vivichoke_seeds}, age 7) — break, then replant from vivichoke seeds.
 *   - Hearty Grains ({@code cobblemon:hearty_grains}, half=upper & age 6) — break ONLY the top; the
 *     lower half stays and regrows, so no replant. Breaking the base is deliberately never done.
 *   - Vanilla single-block crops at max age (wheat/carrot/potato/beetroot) — break + replant.
 *
 * Harvesting uses your HELD item ({@link Farm#homeSlot}, captured when you start — e.g. your Fortune
 * pickaxe). Replanting switches to the seed for one action, then switches your hotbar straight back.
 *
 * Everything is an ordinary in-reach interaction packet, so a vanilla server accepts it with no mixin.
 */
public final class AutoFarm {
    private AutoFarm() {}

    private static final String VIVICHOKE_BLOCK = "cobblemon:vivichoke_seeds";
    private static final String VIVICHOKE_SEED_ITEM = "cobblemon:vivichoke_seeds";
    private static final String HEARTY_GRAINS = "cobblemon:hearty_grains";
    private static final String BERRY_CLASS = "com.cobblemon.mod.common.block.BerryBlock";   // all 70 berry types

    private static final long COOLDOWN_MS = 1500;          // per-block, stops double-firing
    private static final int VERTICAL_BAND = 2;            // scan a few layers around the feet

    private static long lastActionMs = 0L;
    private static final Map<Long, Long> cooldown = new HashMap<>();

    public static void tick(MinecraftClient mc) {
        if (!Farm.active) return;
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        if (p == null || w == null || mc.interactionManager == null || mc.currentScreen != null) return;
        if (p.isSpectator()) return;

        long now = System.currentTimeMillis();
        if (now - lastActionMs < Farm.INTERVAL_MS) return;   // rate-limit before we even scan
        expire(now);

        double reach = p.getBlockInteractionRange();
        double reachSq = reach * reach;
        Vec3d eye = p.getEyePos();
        BlockPos feet = p.getBlockPos();
        boolean scoped = Field.isSet();
        boolean reap = Farm.mode == Farm.Mode.REAP;
        int r = (int) Math.ceil(reach) + 1;

        List<BlockPos> breaks = new ArrayList<>();
        List<BlockPos> picks = new ArrayList<>();
        List<BlockPos> plants = new ArrayList<>();
        List<BlockPos> tills = new ArrayList<>();

        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = feet.getX() + dx, z = feet.getZ() + dz;
                if (scoped && (x < Field.minX || x > Field.maxX || z < Field.minZ || z > Field.maxZ)) continue;
                for (int dy = -VERTICAL_BAND; dy <= 1; dy++) {
                    m.set(x, feet.getY() + dy, z);
                    if (eye.squaredDistanceTo(Vec3d.ofCenter(m)) > reachSq) continue;
                    if (cooldown.containsKey(m.asLong())) continue;
                    BlockState s = w.getBlockState(m);
                    if (s.isAir()) continue;
                    if (reap) {
                        if (shouldBreak(s)) breaks.add(m.toImmutable());
                        else if (isHarvestableBerry(s)) picks.add(m.toImmutable());
                        else if (isEmptyFarmland(s, w, m)) plants.add(m.toImmutable());
                    } else {
                        if (isTillable(s) && w.getBlockState(m.up()).isAir()) tills.add(m.toImmutable());
                        else if (isEmptyFarmland(s, w, m)) plants.add(m.toImmutable());
                    }
                }
            }
        }

        boolean touched;
        if (reap) {
            if (!breaks.isEmpty()) touched = doBreak(mc, p, nearest(breaks, eye), Farm.homeSlot);
            else if (!picks.isEmpty()) touched = doPick(mc, p, nearest(picks, eye));
            else touched = !plants.isEmpty() && doPlant(mc, p, nearest(plants, eye), Farm.homeSlot);
        } else {
            touched = !tills.isEmpty() ? doTill(mc, p, nearest(tills, eye))
                    : !plants.isEmpty() && doPlant(mc, p, nearest(plants, eye), -1);
        }
        if (touched) lastActionMs = now;
    }

    // ---- classification -------------------------------------------------

    private static boolean isEmptyFarmland(BlockState s, ClientWorld w, BlockPos pos) {
        return s.isOf(Blocks.FARMLAND) && w.getBlockState(pos.up()).isAir();
    }

    /** A Cobblemon berry tree (any of the 70 types) at its fruiting age — harvested by right-click. */
    private static boolean isHarvestableBerry(BlockState s) {
        if (!s.getBlock().getClass().getName().equals(BERRY_CLASS)) return false;
        int age = ageOf(s);
        return age >= 0 && age >= maxAgeOf(s);     // fruit is ready at max age (5)
    }

    private static boolean isTillable(BlockState s) {
        return s.isOf(Blocks.DIRT) || s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.DIRT_PATH);
    }

    /** What this engine is allowed to break: mature Cobblemon crops + mature vanilla single-block crops. */
    private static boolean shouldBreak(BlockState s) {
        String id = idOf(s);
        // Hearty Grains: ONLY the fully-grown top half (the base stays + regrows). Never the lower half.
        if (id.equals(HEARTY_GRAINS)) return "upper".equals(halfOf(s)) && ageOf(s) >= 6;
        // Vivichoke: mature at age 7.
        if (id.equals(VIVICHOKE_BLOCK)) return ageOf(s) >= 7;
        // Vanilla single-block crops at max age (skip anything with a half — i.e. tall plants).
        Block b = s.getBlock();
        if ((b instanceof CropBlock || b instanceof NetherWartBlock) && halfOf(s) == null) {
            int age = ageOf(s);
            return age >= 0 && age >= maxAgeOf(s);
        }
        return false;
    }

    private static String idOf(BlockState s) {
        return Registries.BLOCK.getId(s.getBlock()).toString();
    }

    private static int ageOf(BlockState s) {
        for (Property<?> prop : s.getProperties())
            if (prop instanceof IntProperty ip && prop.getName().equals("age")) return s.get(ip);
        return -1;
    }

    private static int maxAgeOf(BlockState s) {
        for (Property<?> prop : s.getProperties())
            if (prop instanceof IntProperty ip && prop.getName().equals("age")) {
                int mx = 0;
                for (int v : ip.getValues()) mx = Math.max(mx, v);
                return mx;
            }
        return -1;
    }

    private static String halfOf(BlockState s) {
        for (Property<?> prop : s.getProperties())
            if (prop.getName().equals("half")) return propName(prop, s);
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propName(Property prop, BlockState s) {
        return prop.name(s.get(prop));
    }

    // ---- actions (each returns true when it "uses" this interval) -------

    private static boolean doBreak(MinecraftClient mc, ClientPlayerEntity p, BlockPos crop, int harvestSlot) {
        if (harvestSlot >= 0) select(p, p.getInventory(), harvestSlot);   // swing with your held tool
        mc.interactionManager.attackBlock(crop, Direction.UP);            // crops are instabreak
        p.swingHand(Hand.MAIN_HAND);
        if (mc.world.getBlockState(crop).isAir()) {                       // only cool down if it actually broke
            cooldown.put(crop.asLong(), System.currentTimeMillis() + COOLDOWN_MS);
            Farm.harvested++;
        }
        Farm.lastWarn = null;
        return true;
    }

    /** Pick a berry by right-clicking it — the tree stays and regrows. Never broken, never replanted. */
    private static boolean doPick(MinecraftClient mc, ClientPlayerEntity p, BlockPos berry) {
        if (Farm.homeSlot >= 0) select(p, p.getInventory(), Farm.homeSlot);   // right-click with your tool, not shears/mulch/seed
        interactTop(mc, p, berry);
        cooldown.put(berry.asLong(), System.currentTimeMillis() + COOLDOWN_MS);
        Farm.picked++;
        Farm.lastWarn = null;
        return true;
    }

    private static boolean doPlant(MinecraftClient mc, ClientPlayerEntity p, BlockPos farmland, int restoreSlot) {
        PlayerInventory inv = p.getInventory();
        int slot = findHotbar(inv, st -> isSeed(st.getItem()));
        if (slot < 0) { Farm.lastWarn = "hold seeds in your hotbar to replant"; return true; }
        select(p, inv, slot);
        interactTop(mc, p, farmland);
        if (!mc.world.getBlockState(farmland.up()).isAir()) {           // only cool down if it actually planted
            long t = System.currentTimeMillis() + COOLDOWN_MS;
            cooldown.put(farmland.asLong(), t);
            cooldown.put(farmland.up().asLong(), t);
            Farm.planted++;
        }
        if (restoreSlot >= 0) select(p, inv, restoreSlot);              // back to your harvest tool
        Farm.lastWarn = null;
        return true;
    }

    private static boolean doTill(MinecraftClient mc, ClientPlayerEntity p, BlockPos ground) {
        PlayerInventory inv = p.getInventory();
        int slot = findHotbar(inv, st -> st.getItem() instanceof HoeItem);
        if (slot < 0) { Farm.lastWarn = "hold a hoe in your hotbar to till"; return true; }
        select(p, inv, slot);
        interactTop(mc, p, ground);
        if (mc.world.getBlockState(ground).isOf(Blocks.FARMLAND)) {     // only cool down if it actually tilled
            cooldown.put(ground.asLong(), System.currentTimeMillis() + COOLDOWN_MS);
            Farm.tilled++;
        }
        Farm.lastWarn = null;
        return true;
    }

    /** Right-click the top face of a block with whatever's held (plant seed / use hoe). */
    private static void interactTop(MinecraftClient mc, ClientPlayerEntity p, BlockPos pos) {
        Vec3d top = Vec3d.ofCenter(pos).add(0, 0.5, 0);
        BlockHitResult hit = new BlockHitResult(top, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
    }

    // ---- hotbar / helpers ----------------------------------------------

    private static boolean isSeed(Item it) {
        if (it == Items.WHEAT_SEEDS || it == Items.BEETROOT_SEEDS || it == Items.CARROT || it == Items.POTATO)
            return true;
        String id = Registries.ITEM.getId(it).toString();
        return id.equals(VIVICHOKE_SEED_ITEM) || id.equals(HEARTY_GRAINS);   // the hearty-grain item IS its own seed
    }

    private static int findHotbar(PlayerInventory inv, java.util.function.Predicate<net.minecraft.item.ItemStack> test) {
        if (test.test(inv.getStack(inv.selectedSlot))) return inv.selectedSlot;   // already held
        for (int i = 0; i < 9; i++) if (test.test(inv.getStack(i))) return i;
        return -1;
    }

    private static void select(ClientPlayerEntity p, PlayerInventory inv, int slot) {
        if (slot < 0 || slot > 8 || inv.selectedSlot == slot) return;
        inv.selectedSlot = slot;
        p.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private static BlockPos nearest(List<BlockPos> list, Vec3d eye) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos b : list) {
            double d = eye.squaredDistanceTo(Vec3d.ofCenter(b));
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    private static void expire(long now) {
        Iterator<Map.Entry<Long, Long>> it = cooldown.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() <= now) it.remove();
    }
}
