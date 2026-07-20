package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → server: a tagged console action (INTERACTIVE_SPEC §2.1). Slice 2 uses {@link #PULL_ONE} (withdraw
 * one stack of {@code arg}) and {@link #PULL_ID} (withdraw all of {@code arg}); later actions extend the
 * {@code action} code space. The server validates, mutates the store, then re-pushes status + the affected
 * tab. Costs are deferred (INTERACTIVE_SPEC §7.5) - no GPU/Data gate yet.
 */
public record NotebookActionC2S(int action, String arg, int amount) implements CustomPayload {
    public static final int PULL_ONE = 0;        // withdraw one stack of arg (a registry id)
    public static final int PULL_ID  = 1;        // withdraw all of arg
    public static final int SET_BUFF = 2;        // Compiler: arg = buff id, amount = tier (≤0 removes)
    public static final int TOGGLE_DAEMON = 3;   // Compiler: flip the held Daemon ON/OFF
    public static final int APPLY_AUGMENT = 4;   // Augmenter: arg = AugmentType name
    public static final int REMOVE_AUGMENT = 5;  // Augmenter: arg = AugmentType name
    public static final int WITHDRAW = 6;        // BioBank: amount = flat egg index → materialize into inventory
    public static final int PULL_STACK = 7;      // Storage: arg = item id → take up to one stack (space-aware)
    public static final int DISMISS_NOTE = 8;    // Inbox: arg = note id, or "all"
    public static final int WRITE_DISK = 9;      // Disks: arg = denomination item id - blank + balance → written disk
    public static final int RITUAL_PULL = 10;    // Rituals: arg = item id, amount = mode (0 one · 1 stack · 2 all)
    public static final int CORRUPT_KERNEL = 11; // Augmenter: consume an Illicit Data Disk → Vaal-roll the held Kernel
    public static final int SET_KERNEL_TARGET = 12; // Augmenter: amount = inventory slot (1000 = offhand) to operate on
    public static final int SET_DAEMON_TARGET = 13; // Compiler: amount = inventory slot of the Daemon to operate on
    public static final int RENAME_HELD_KERNEL = 14; // arg = new display name for the MAIN-HAND Kernel (empty clears)
    public static final int COMPRESS_MON = 15;      // Specimens: amount = party slot → archive onto a blank Specimen Disk
    public static final int SUMMON_MISSINGNO = 16;  // Dashboard: claim one owed MissingNo. (1 per million lifetime Data)
    public static final int ARCADE_NEW = 17;        // Game Corner: deal a fresh board at the player's level
    public static final int ARCADE_FLIP = 18;       // Game Corner: amount = tile index 0..24
    public static final int ARCADE_CASHOUT = 19;    // Game Corner: bank the pot (level holds), end the round
    public static final int TREELINE_NEW = 20;      // Game Corner cabinet 2: deal a fresh forest
    public static final int TREELINE_SEARCH = 21;   // Game Corner cabinet 2: amount = tree id to sweep
    public static final int SHOP_BUY = 22;         // Game Corner shop: amount = shelf slot to redeem for Coins
    public static final int TOPDECK_NEW = 23;      // Game Corner cabinet 3: amount = wager, debited on deal
    public static final int TOPDECK_FLIP = 24;     // Game Corner cabinet 3: amount = card position to flip
    public static final int TOPDECK_CASHOUT = 25;  // Game Corner cabinet 3: bank the completed rung
    public static final int SLOTS_SPIN = 26;       // Game Corner cabinet 4: amount = bet per pull
    public static final int TOPDECK_MERCY = 27;    // Game Corner cabinet 3: start the free memory check
    public static final int TOPDECK_MERCY_PICK = 28; // Game Corner cabinet 3: arg = chosen emotion
    public static final int VIBE_NEW = 29;         // Game Corner cabinet 5: shuffle the free deck
    public static final int VIBE_DRAW = 30;        // Game Corner cabinet 5: flip the next card
    public static final int VIBE_CASH = 31;        // Game Corner cabinet 5: bank the pot
    public static final int TAG_NEW = 32;          // Game Corner cabinet 6: release the crowd
    public static final int TAG_CLICK = 33;        // Game Corner cabinet 6: the player clicked the target
    public static final int HR_BUY = 34;           // High Roller Room: amount = fixed shelf slot
    public static final int COMPRESS_EGGS = 35;    // BioBank press: arg = species key → 100 worst non-shiny eggs → permanent +5% drops
    public static final int COMPRESS_SERVER = 36;  // BioBank press: arg = species key → donate 100 eggs to the communal pool (1000 = +1% for everyone)

    public static final Id<NotebookActionC2S> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_action"));

    public static final PacketCodec<RegistryByteBuf, NotebookActionC2S> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, NotebookActionC2S::action,
            PacketCodecs.STRING, NotebookActionC2S::arg,
            PacketCodecs.VAR_INT, NotebookActionC2S::amount,
            NotebookActionC2S::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
