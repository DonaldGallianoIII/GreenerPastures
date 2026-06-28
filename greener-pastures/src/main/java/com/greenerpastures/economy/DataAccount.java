package com.greenerpastures.economy;

/**
 * A player's <b>Data</b> balance — the one currency, which comes ONLY from rendered (culled) eggs.
 * Minecraft-free + unit-tested; the MC adapter persists one of these per player. Starvation is just an
 * inability to pay (tethers go inert, base breeding continues) — the balance is never negative and
 * never destructive.
 */
public final class DataAccount {
    private long balance;

    public DataAccount(long initial) { this.balance = Math.max(0L, initial); }

    public long balance() { return balance; }

    /** Add Data (e.g. from a Renderer). Non-positive amounts are ignored. */
    public void credit(long amount) { if (amount > 0) balance += amount; }

    /** True if the balance can cover {@code amount}. */
    public boolean canAfford(long amount) { return amount <= 0 || balance >= amount; }

    /** Pay {@code amount} iff affordable; returns whether it was paid. Never goes negative — an unpaid
     *  debit is a "starve" (the caller leaves that tether/buff inert this tick). */
    public boolean tryDebit(long amount) {
        if (amount <= 0) return true;
        if (balance < amount) return false;
        balance -= amount;
        return true;
    }
}
