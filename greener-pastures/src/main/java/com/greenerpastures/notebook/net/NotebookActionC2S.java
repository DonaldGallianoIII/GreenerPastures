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
 * tab. Costs are deferred (INTERACTIVE_SPEC §7.5) — no GPU/Data gate yet.
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
    public static final int WRITE_DISK = 9;      // Disks: arg = denomination item id — blank + balance → written disk
    public static final int RITUAL_PULL = 10;    // Rituals: arg = item id, amount = mode (0 one · 1 stack · 2 all)

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
