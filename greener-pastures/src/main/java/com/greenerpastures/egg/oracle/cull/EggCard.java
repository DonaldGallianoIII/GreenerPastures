package com.greenerpastures.egg.oracle.cull;

/**
 * The full-ish competitive read off a Cobbreeding egg (INTERACTIVE_SPEC §3.3, slice 6b) — everything the
 * egg's baked {@code PokemonProperties} spec exposes. {@code ivs}/{@code evs} are per-stat in
 * <b>HP · Atk · Def · SpA · SpD · Spe</b> order; {@code nature}/{@code gender}/{@code ability} are best-effort
 * (empty string if the API didn't provide them). Tera / ball / OT / moves are <b>post-hatch</b> and not part
 * of an egg's spec, so they aren't here (BioBank stores eggs, not hatched mons — Deuce §7.3).
 */
public record EggCard(String species, boolean shiny, int[] ivs, int[] evs,
                      String nature, String gender, String ability) {

    public int ivTotal() {
        int t = 0;
        for (int v : ivs) t += v;
        return t;
    }

    public int perfectIvs() {
        int n = 0;
        for (int v : ivs) if (v >= 31) n++;
        return n;
    }
}
