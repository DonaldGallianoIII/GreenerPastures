package com.greenerpastures.display;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * The MC-side NBT seam for a resident's patrol config (Display Suite v2 §3). Keeps the {@link PatrolPath}
 * core free of Minecraft (the {@link StatueTransform} pattern): this writes/reads the primitive fields and
 * hands the sanitizing back to {@link PatrolPath}'s canonical constructor. Only the AUTHORED config is
 * persisted - the transient {@link PatrolPath.Progress} cursor is re-derived at {@code start()} on reload.
 */
final class PatrolNbt {
    private PatrolNbt() {}

    private static final String MODE = "PatrolMode";
    private static final String PING = "PingPong";
    private static final String DWELL = "Dwell";
    private static final String SPEED = "Speed";
    private static final String WAYPOINTS = "Waypoints";

    /** Write mode + path onto a resident's NBT compound (nothing written when defaults - keeps saves lean). */
    static void write(NbtCompound into, PatrolMode mode, PatrolPath path) {
        if (mode != PatrolMode.WANDER) into.putString(MODE, mode.name());
        if (path.isEmpty() && !path.pingPong() && path.dwellTicks() == 0) return;   // nothing authored
        into.putBoolean(PING, path.pingPong());
        into.putInt(DWELL, path.dwellTicks());
        into.putDouble(SPEED, path.speed());
        NbtList list = new NbtList();
        for (RelPos w : path.waypoints()) {
            NbtCompound c = new NbtCompound();
            c.putDouble("x", w.dx());
            c.putDouble("y", w.dy());
            c.putDouble("z", w.dz());
            list.add(c);
        }
        into.put(WAYPOINTS, list);
    }

    static PatrolMode readMode(NbtCompound nbt) {
        return PatrolMode.fromId(nbt.getString(MODE));   // absent → "" → WANDER
    }

    static PatrolPath readPath(NbtCompound nbt) {
        List<RelPos> waypoints = new ArrayList<>();
        for (NbtElement e : nbt.getList(WAYPOINTS, NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            waypoints.add(new RelPos(c.getDouble("x"), c.getDouble("y"), c.getDouble("z")));
        }
        double speed = nbt.contains(SPEED) ? nbt.getDouble(SPEED) : PatrolPath.DEFAULT_SPEED;
        return new PatrolPath(waypoints, nbt.getBoolean(PING), nbt.getInt(DWELL), speed);
    }
}
