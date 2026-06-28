package com.greenerpastures.economy;

/**
 * The grid readout — "is my Data net-positive?" Income (rendered eggs → Data) vs drain (tether burn +
 * global buffs), per breeding cycle. The Notebook's emotional-center plot: fuel pastures must out-earn
 * the trophy pastures they power. Pure + Minecraft-free.
 */
public record GridBalance(long incomePerCycle, long burnPerCycle) {

    public long netPerCycle() { return incomePerCycle - burnPerCycle; }

    public boolean netPositive() { return netPerCycle() > 0; }

    /** Cycles until a {@code balance} drains to zero at the current net rate; {@link Long#MAX_VALUE}
     *  when not draining (net ≥ 0). */
    public long cyclesToEmpty(long balance) {
        long net = netPerCycle();
        if (net >= 0) return Long.MAX_VALUE;
        return Math.max(0L, balance) / (-net);
    }
}
