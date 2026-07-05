package com.greenerpastures.specimen;

/**
 * The dupe-safety rails for mon compression (Specimen Disks - Deuce, 2026-07-05). Pure decision core:
 * every refusal is a REASON STRING (null = allowed) so the server can chat it verbatim and tests can
 * assert exact gates. Order matters - the first violated gate is the one reported.
 *
 * <p>Gates: the party must keep at least one mon (Cobblemon's own PC rule - never strand a trainer),
 * no compression mid-battle (a battle clone would desync), the slot must actually hold a mon, a blank
 * Specimen Disk must be present, and the written disk must have somewhere to land BEFORE the mon is
 * removed (mint-after-remove, but only when landing is guaranteed - the no-insertStack contract).
 */
public final class SpecimenRules {
    private SpecimenRules() {}

    public static final int PARTY_SLOTS = 6;

    public static String compressRefusal(int partySize, int slot, boolean partyBusy,
                                         boolean slotHasMon, boolean hasBlankDisk, boolean hasLandingSpot) {
        if (partyBusy) return "You can't archive a specimen mid-battle.";
        if (slot < 0 || slot >= PARTY_SLOTS) return "No such party slot.";
        if (!slotHasMon) return "That party slot is empty.";
        if (partySize <= 1) return "Your last party member stays with you.";
        if (!hasBlankDisk) return "You need a blank Specimen Disk.";
        if (!hasLandingSpot) return "No inventory room for the written disk.";
        return null;
    }

    /** Release-side: the disk must carry a specimen and SOME store must have accepted the mon. */
    public static String releaseRefusal(boolean hasSpecimen, boolean storeAccepted) {
        if (!hasSpecimen) return "This disk is blank - archive a party mon from the Notebook's Specimens tab.";
        if (!storeAccepted) return "Party and PC are both full - no room to release.";
        return null;
    }
}
