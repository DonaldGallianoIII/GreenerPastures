package com.greenerpastures.display;

import java.util.List;
import java.util.UUID;

/**
 * The dupe-safety + courtesy rails for the <b>Exhibit Pen</b> (Display Suite - Deuce, 2026-07-14). Pure
 * decision core in the {@link com.greenerpastures.specimen.SpecimenRules} mold: every refusal is a REASON
 * STRING (null = allowed) so the block can chat it verbatim and tests can assert exact gates. Order
 * matters - the first violated gate is the one reported.
 *
 * <p>The projection principle backs every gate (see {@code docs/dev/DISPLAY_SPEC.md} §0-1): the disk in
 * the pen is the single source of truth, the roaming mon is a projection, and eject always returns the
 * WRITTEN disk exactly as inserted (contrast {@code SpecimenDiskItem} release, which consumes the payload
 * and hands back a blank).
 */
public final class ExhibitRules {
    private ExhibitRules() {}

    /** v1 slot count - a pasture-sized herd per pen (config {@code exhibitSlots} can override later). */
    public static final int DEFAULT_SLOTS = 6;

    /**
     * Insert gates, in refusal-priority order: the disk must carry a specimen, the glitch never enters a
     * pen (same reasoning as the pasture tether refusal - a confined MissingNo. freezes on one species and
     * stops being cosmetic), and there must be a free slot.
     */
    public static String insertRefusal(boolean hasSpecimen, boolean isGlitch, int usedSlots, int maxSlots) {
        if (!hasSpecimen) return "This disk is blank - archive a party mon from the Notebook's Specimens tab.";
        if (isGlitch) return "MissingNo. refuses confinement - it is a trophy, not livestock.";
        if (usedSlots >= maxSlots) return "This pen is full (" + maxSlots + " residents).";
        return null;
    }

    /** Eject gates: something must be stored, and the requester must be allowed to take at least one disk. */
    public static String ejectRefusal(int usedSlots, boolean anyTakeable) {
        if (usedSlots <= 0) return "This pen is empty.";
        if (!anyTakeable) return "Only the donor or the pen's owner can take these disks.";
        return null;
    }

    /** Shared-zoo permission (v1-simple): the slot's inserter or the block's placer may take a disk. */
    public static boolean canTake(UUID requester, UUID inserter, UUID owner) {
        if (requester == null) return false;
        return requester.equals(inserter) || requester.equals(owner);
    }

    /**
     * Which slot a sneak-right-click ejects: the LAST-inserted disk the requester is allowed to take
     * (skip over other donors' disks rather than refusing outright), or -1 if none.
     *
     * @param insertersInOrder per-slot inserter UUIDs, index 0 = oldest insert
     */
    public static int ejectIndex(List<UUID> insertersInOrder, UUID requester, UUID owner) {
        for (int i = insertersInOrder.size() - 1; i >= 0; i--) {
            if (canTake(requester, insertersInOrder.get(i), owner)) return i;
        }
        return -1;
    }
}
