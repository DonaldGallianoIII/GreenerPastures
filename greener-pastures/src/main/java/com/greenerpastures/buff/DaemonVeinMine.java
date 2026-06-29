package com.greenerpastures.buff;

import com.greenerpastures.core.GpLog;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Daemon HOOK buff — vein-miner. When a fed Daemon holder with the {@code VEIN_MINE} buff breaks a <b>veinable</b>
 * block (an ore, or a log), break the connected run of the SAME block in one go, each dropping its proper loot
 * (the Fortune + auto-smelt boosts apply per block, since we route each through {@code Block.getDroppedStacks}
 * with the real tool). The Data rental is the cost — no tool durability is consumed in v1.
 *
 * <p><b>Safeguards (this is the one buff that can chew terrain, so it's deliberately fenced):</b>
 * <ul>
 *   <li>only ores ({@code c:ores}) and logs ({@code #minecraft:logs}) are veinable — never dirt/stone/etc.;</li>
 *   <li>only the SAME block as the one broken, flood-filled from it, hard-capped at {@link #HARD_CAP};</li>
 *   <li>only for a non-creative/non-spectator {@link ServerPlayerEntity} whose tool {@code isSuitableFor} it;</li>
 *   <li>a re-entrancy guard so the cascade can never recurse (and {@code World.breakBlock} doesn't fire the
 *       Fabric break event anyway, so the cascade is invisible to this handler).</li>
 * </ul>
 */
public final class DaemonVeinMine {
    private DaemonVeinMine() {}

    /** Per-tier reach; the absolute ceiling fences a runaway flood-fill no matter the tier/config. */
    private static final int PER_TIER = 32;
    private static final int HARD_CAP = 96;

    /** Belt-and-suspenders: never let a vein break re-trigger vein mining on the same thread. */
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    public static void init() {
        PlayerBlockBreakEvents.AFTER.register(DaemonVeinMine::onBreak);
        GpLog.i("buff", "veinmine_init", "cap", HARD_CAP);
    }

    private static void onBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be) {
        if (world.isClient || ACTIVE.get()) return;
        if (!(player instanceof ServerPlayerEntity sp) || sp.isCreative() || sp.isSpectator()) return;
        if (!(world instanceof ServerWorld sw)) return;
        if (!isVeinable(state)) return;

        ResolvedBuffs paid = DaemonBuffs.paidBuffs(sp);
        if (paid == null) return;
        int tier = paid.tier(BuffId.VEIN_MINE);
        if (tier <= 0) return;

        ItemStack tool = sp.getMainHandStack();
        if (!tool.isSuitableFor(state)) return;          // need the right tool — no bare-hand ore veining

        veinMine(sw, sp, pos, state.getBlock(), tool, Math.min(HARD_CAP, tier * PER_TIER));
    }

    private static void veinMine(ServerWorld world, ServerPlayerEntity player, BlockPos origin,
                                 Block block, ItemStack tool, int limit) {
        ACTIVE.set(true);
        int broken = 0;
        try {
            Set<BlockPos> seen = new HashSet<>();
            Deque<BlockPos> frontier = new ArrayDeque<>();
            seen.add(origin.toImmutable());
            enqueueNeighbors(origin, seen, frontier);

            while (!frontier.isEmpty() && broken < limit) {
                BlockPos p = frontier.poll();
                BlockState s = world.getBlockState(p);
                if (!s.isOf(block)) continue;

                BlockEntity be = s.hasBlockEntity() ? world.getBlockEntity(p) : null;
                // route through getDroppedStacks with the real tool so Fortune + auto-smelt apply per block
                List<ItemStack> drops = Block.getDroppedStacks(s, world, p, be, player, tool);
                world.breakBlock(p, false, player, 512);     // remove + particles; we drop manually
                for (ItemStack drop : drops) spawn(world, p, drop);

                broken++;
                enqueueNeighbors(p, seen, frontier);
            }
        } finally {
            ACTIVE.set(false);
        }
        if (broken > 0) GpLog.d("buff", "vein_mine", "block", block.toString(), "broken", broken, "limit", limit);
    }

    private static void enqueueNeighbors(BlockPos p, Set<BlockPos> seen, Deque<BlockPos> frontier) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos n = p.add(dx, dy, dz);
                    if (seen.add(n.toImmutable())) frontier.add(n);
                }
            }
        }
    }

    private static void spawn(ServerWorld world, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        item.setToDefaultPickupDelay();
        world.spawnEntity(item);
    }

    private static boolean isVeinable(BlockState state) {
        return state.isIn(ConventionalBlockTags.ORES) || state.isIn(BlockTags.LOGS);
    }
}
