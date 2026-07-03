package com.greenerpastures.notebook.net;

import com.greenerpastures.GreenerPastures;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client: the focused pasture's <b>extras</b> that don't fit {@link NotebookPastureConfigS2C}'s
 * maxed tuple — as one JSON blob (the established tuple-6 dodge): the health strip (#37) + the slotted
 * Kernel's breeding-meta loadout (nature/ball/EV/ability/egg-moves — #34/#35 display). Rides with every
 * {@code pushPastureConfig}; pos-keyed so the client caches it alongside the config (stale-while-revalidate).
 *
 * <p>Shape: {@code {"health":[{"id","icon","text"}...], "kernel":{"nature","ball","ev","ha","moves"}|null}}
 */
public record NotebookPastureExtraS2C(long pos, String json) implements CustomPayload {
    public static final Id<NotebookPastureExtraS2C> ID =
            new Id<>(Identifier.of(GreenerPastures.MOD_ID, "notebook_pasture_extra"));

    public static final PacketCodec<RegistryByteBuf, NotebookPastureExtraS2C> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_LONG, NotebookPastureExtraS2C::pos,
            PacketCodecs.STRING, NotebookPastureExtraS2C::json,
            NotebookPastureExtraS2C::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
