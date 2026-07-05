package com.greenerpastures.drops;

import java.util.Map;

/** Implemented onto {@code PokeSnackBlockEntity} by the repel mixin — lets static injectors (the spawner
 *  creation lambda) reach the block's live repel payload without reflection. */
public interface GpRepelHost {
    Map<String, Integer> gp$getRepels();
}
