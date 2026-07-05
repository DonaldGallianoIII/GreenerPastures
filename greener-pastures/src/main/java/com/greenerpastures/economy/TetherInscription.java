package com.greenerpastures.economy;

/**
 * The Compiler's Soul-Tether inscription, Minecraft-free + unit-tested. Writing {@code [function, tier]}
 * onto a tether costs Data upfront ({@link TetherEconomics#inscribeCost}); re-inscribing or wiping
 * recovers only part ({@link TetherEconomics#REFUND_RATE}), so you can <b>never profit by flipping</b> -
 * the upfront cost is always real (book-style re-inscription, Deuce 2026-06-28).
 *
 * <p>Re-inscribing = wipe the current tier (refund) then inscribe the new one (cost): net =
 * {@code newCost − oldRefund} (can be negative on a downgrade/wipe → the player is credited). The
 * Compiler GUI just calls {@link #inscribe}/{@link #wipe} and applies {@link Result#dataDelta()}.
 */
public final class TetherInscription {
    private TetherInscription() {}

    /** {@code tether} = the result; {@code dataDelta} = Data to charge (positive) or credit (negative);
     *  {@code ok} = whether it happened (false ⇒ couldn't afford, nothing changed). */
    public record Result(Tether tether, long dataDelta, boolean ok) {}

    /**
     * Inscribe {@code current} to {@code [function, newTier]}, paid from {@code balance}. Refunds the
     * current tier's wipe value first, then charges the new tier; fails (unchanged) if the net cost isn't
     * affordable. {@code newTier ≤ 0} is a {@link #wipe}.
     */
    public static Result inscribe(Tether current, String function, int newTier, long balance) {
        long refund = (current == null || current.isBlank()) ? 0L : TetherEconomics.wipeRefund(current.tier());
        long cost = TetherEconomics.inscribeCost(newTier);
        long net = cost - refund;                       // negative = net credit (downgrade/wipe)
        if (net > balance) return new Result(current, 0L, false);   // can't cover the upfront cost
        Tether out = (newTier <= 0 || function == null || function.isBlank())
                ? Tether.blank()
                : new Tether(function, newTier);
        return new Result(out, net, true);
    }

    /** Wipe a tether back to blank, recovering its partial refund (always &lt; what it cost to inscribe). */
    public static Result wipe(Tether current, long balance) {
        return inscribe(current, "", 0, balance);
    }
}
