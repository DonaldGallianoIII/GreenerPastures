package com.greenerpastures.arcade;

import java.util.List;
import java.util.Random;

/**
 * <b>SLOTS</b> - Game Corner cabinet #4 (Deuce, 2026-07-06: "the classic"). Three reels of Pokemon
 * portraits, one spin per pull, server-rolled. Uniform independent reels over {@link #SYMBOLS}
 * (8 symbols, 512 outcomes) - the paytable below puts the return-to-player at exactly
 * 977/1024 ≈ 95.4%, which is generous-casino territory and enumerable in a unit test.
 *
 * <p>Paytable (× bet): three Voltorb = {@link #PAY_JACKPOT}; any other three-of-a-kind =
 * {@link #PAY_TRIPLE}; two Voltorb = {@link #PAY_TWO_VOLT}; any other pair = 1.5x (floored to
 * whole Coins - Deuce 2026-07-07: "feels a little better... your still gonna lose alot"). The
 * pair bump alone would flip the RTP player-favored (530.5/512), so two-Voltorb paid for it
 * (5x -> 3x). Voltorb is the jackpot face because of course it is.
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
    public static final long PAY_TWO_VOLT = 3;
    /** Pairs pay 3/2 (1.5x), floored to whole Coins - see class docs for the RTP accounting. */
    public static final long PAY_PAIR_NUM = 3;
    public static final long PAY_PAIR_DEN = 2;

    /** One pull: three independent uniform faces. */
    public static int[] spin(Random rng) {
        return new int[]{rng.nextInt(SYMBOLS.size()), rng.nextInt(SYMBOLS.size()), rng.nextInt(SYMBOLS.size())};
    }

    private enum Line { NONE, PAIR, TWO_VOLT, TRIPLE, JACKPOT }

    private static Line line(int[] r) {
        if (r == null || r.length != 3) return Line.NONE;
        int volts = 0;
        for (int f : r) if (f == JACKPOT_INDEX) volts++;
        if (volts == 3) return Line.JACKPOT;
        if (r[0] == r[1] && r[1] == r[2]) return Line.TRIPLE;
        if (volts == 2) return Line.TWO_VOLT;
        if (r[0] == r[1] || r[1] == r[2] || r[0] == r[2]) return Line.PAIR;
        return Line.NONE;
    }

    /** Coins paid for a spin at {@code bet} (0 on a loss; {@code bet} itself was already debited). */
    public static long payout(int[] r, long bet) {
        return switch (line(r)) {
            case JACKPOT -> PAY_JACKPOT * bet;
            case TRIPLE -> PAY_TRIPLE * bet;
            case TWO_VOLT -> PAY_TWO_VOLT * bet;
            case PAIR -> bet * PAY_PAIR_NUM / PAY_PAIR_DEN;
            case NONE -> 0;
        };
    }

    public static boolean betValid(long bet) {
        return bet >= MIN_BET && bet <= MAX_BET;
    }
}
