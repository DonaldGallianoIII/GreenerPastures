package com.shedmon.shedscope;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.EmptyChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Periodically sweeps loaded chunks for target blocks. Runs on the client thread. */
public final class Scanner {
    public record Hit(BlockPos pos, Target target, double distSq) {}

    public static int radius = 80;   // blocks
    public static int maxHits = 400; // cap on rendered/listed hits
    public static int scanEveryTicks = 8;

    private static int tick = 0;
    private static List<Hit> latest = List.of();

    private Scanner() {}

    public static List<Hit> hits() { return latest; }

    public static void tick(MinecraftClient mc) {
        if (!State.enabled || mc.world == null || mc.player == null) {
            latest = List.of();
            return;
        }
        if (++tick < scanEveryTicks) return;
        tick = 0;
        latest = scan(mc.world, mc.player);
    }

    private static List<Hit> scan(ClientWorld world, ClientPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        int pcx = p.getBlockX() >> 4;
        int pcz = p.getBlockZ() >> 4;
        int rc = (radius >> 4) + 1;
        double r2 = (double) radius * radius;
        int bottomY = world.getBottomY();
        List<Hit> out = new ArrayList<>();

        for (int cx = pcx - rc; cx <= pcx + rc; cx++) {
            for (int cz = pcz - rc; cz <= pcz + rc; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk instanceof EmptyChunk) continue;
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection sec = sections[i];
                    if (sec == null || sec.isEmpty()) continue;
                    if (!sec.hasAny(st -> Targets.BY_BLOCK.containsKey(st.getBlock()))) continue;

                    int baseX = cx << 4;
                    int baseZ = cz << 4;
                    int baseY = bottomY + (i << 4);
                    for (int ly = 0; ly < 16; ly++) {
                        for (int lx = 0; lx < 16; lx++) {
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState st = sec.getBlockState(lx, ly, lz);
                                Target t = Targets.BY_BLOCK.get(st.getBlock());
                                if (t == null || !State.isEnabled(t.category)) continue;
                                double dx = baseX + lx + 0.5 - eye.x;
                                double dy = baseY + ly + 0.5 - eye.y;
                                double dz = baseZ + lz + 0.5 - eye.z;
                                double d2 = dx * dx + dy * dy + dz * dz;
                                if (d2 > r2) continue;
                                out.add(new Hit(new BlockPos(baseX + lx, baseY + ly, baseZ + lz), t, d2));
                            }
                        }
                    }
                }
            }
        }

        out.sort(Comparator.comparingDouble(Hit::distSq));
        if (out.size() > maxHits) out = new ArrayList<>(out.subList(0, maxHits));
        return out;
    }
}
