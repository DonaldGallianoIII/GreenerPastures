package com.greenerpastures.arcade;

import java.util.List;
import java.util.Random;

/**
 * <b>SLOTS</b> - Game Corner cabinet #4 (Deuce, 2026-07-06: "the classic"). Three reels of Pokemon
 * portraits, one spin per pull, server-rolled. Uniform independent reels over {@link #SYMBOLS}
 * (8 symbols, 512 outcomes) - the paytable below puts the return-to-player at exactly
 * 457/512 ≈ 89.3%, which is honest-casino territory and enumerable in a unit test.
 *
 * <p>Paytable (× bet): three Voltorb = {@link #PAY_JACKPOT}; any other three-of-a-kind =
 * {@link #PAY_TRIPLE}; two Voltorb = {@link #PAY_TWO_VOLT}; any other pair = {@link #PAY_PAIR}
 * (bet back). Voltorb is the jackpot face because of course it is.
 */
public final class SlotMachine {
    private SlotMachine() {}

    /** Reel faces - names must exist in the client's portrait pools (voltorb = the angry portrait). */
    public static final List<String> SYMBOLS = List.of(
            "voltorb", "lechonk", "applin", "snom", "yamper", "dedenne", "mimikyu", "morpeko");
    public static final int JACKPOT_INDEX = 0;

    public static final long MIN_BET = 5;
    public static final long MAX_BET = 100;
    public static final long PAY_JACKPOT = 100;
    public static final long PAY_TRIPLE = 15;
    public static final long PAY_TWO_VOLT = 5;
    public static final long PAY_PAIR = 1;

    /** One pull: three independent uniform faces. */
    public static int[] spin(Random rng) {
        return new int[]{rng.nextInt(SYMBOLS.size()), rng.nextInt(SYMBOLS.size()), rng.nextInt(SYMBOLS.size())};
    }

    /** The paytable multiplier for a spin result (0 = the house keeps the bet). */
    public static long multiplier(int[] r) {
        if (r == null || r.length != 3) return 0;
        int volts = 0;
        for (int f : r) if (f == JACKPOT_INDEX) volts++;
        if (volts == 3) return PAY_JACKPOT;
        if (r[0] == r[1] && r[1] == r[2]) return PAY_TRIPLE;
        if (volts == 2) return PAY_TWO_VOLT;
        if (r[0] == r[1] || r[1] == r[2] || r[0] == r[2]) return PAY_PAIR;
        return 0;
    }

    /** Coins paid for a spin at {@code bet} (0 on a loss; {@code bet} itself was already debited). */
    public static long payout(int[] r, long bet) {
        return multiplier(r) * bet;
    }

    public static boolean betValid(long bet) {
        return bet >= MIN_BET && bet <= MAX_BET;
    }
}
