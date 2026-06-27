package com.greenerpastures.egg.oracle.cull;

/**
 * What we managed to read off a Cobbreeding egg.
 * {@code ivsKnown == false} means the IVs couldn't be read (e.g. Cobbreeding API absent);
 * {@code shiny} may still be known from the ★ in the egg's name.
 */
public record EggInfo(boolean shiny, boolean ivsKnown, int ivTotal, int perfectCount) {}
