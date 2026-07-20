package com.greenerpastures.biobank;

/**
 * BioBank capacity - the <b>per-unique-species</b> egg cap that {@link BioBankData} enforces. The BioBank
 * <i>block</i> is retired (block-free Notebook model): eggs auto-ingest into the owner's per-player BioBank as
 * data (EGG_PIPELINE_SPEC), read + withdrawn through the Notebook console. This just holds the cap constant now.
 */
public final class BioBank {
    private BioBank() {}

    /** Per-unique-species cap - each species holds up to this many eggs. 256 → 1024 (Deuce, 2026-07-19):
     *  the Compression press eats 100 per pull, so a bucket must hold serious stock. */
    public static final int DEFAULT_CAP = 1024;

    public static int capacity() { return DEFAULT_CAP; }
}
