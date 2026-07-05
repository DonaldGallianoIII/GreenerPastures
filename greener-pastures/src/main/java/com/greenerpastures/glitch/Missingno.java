package com.greenerpastures.glitch;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.greenerpastures.core.GpLog;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Random;

/**
 * <b>MissingNo.</b> - the capstone cosmetic (Deuce, 2026-07-05): earned once per MILLION lifetime Data
 * rendered, summoned from the Notebook Dashboard. It is a real party Pokémon whose species GLITCHES every
 * ~5 seconds among the classic five (Ditto · Aerodactyl · Gastly · Marowak · Kabutops - the fossils and the
 * ghost, as the original sprite corruption intended), never landing on the same one twice. Deliberately
 * NOT battleable: any battle whose players carry one is refused ("it distorts the battlefield") - box it
 * or Specimen-Disk it to fight. Flag + true identity live in the mon's persistentData, so it survives
 * PC storage, trading, and Specimen Disks; rotation only runs for LOADED party mons (data at rest sleeps).
 */
public final class Missingno {
    private Missingno() {}

    public static final String FLAG = "gp_missingno";
    public static final List<String> SPECIES = List.of("ditto", "aerodactyl", "gastly", "marowak", "kabutops");
    private static final int ROTATE_TICKS = 100;   // ~5 s
    private static final Random RNG = new Random();
    private static int tick = 0;

    public static void init() {
        // The glitch ticker: rotate every loaded flagged party mon. ~1 pass / 5s over online parties - cheap.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tick % ROTATE_TICKS != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                try {
                    var party = PlayerExtensionsKt.party(player);
                    for (int i = 0; i < 6; i++) {
                        Pokemon mon = party.get(i);
                        if (mon == null || !isMissingno(mon)) continue;
                        if (mon.getEntity() != null && mon.getEntity().isBattling()) continue;   // belt-and-suspenders
                        rotate(mon);
                    }
                } catch (Throwable ignored) {
                    // one player's glitch failing must never break the tick
                }
            }
        });

        // NOT battleable: refuse any battle whose players carry the glitch. Cosmetic means cosmetic.
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.NORMAL, event -> {
            try {
                for (ServerPlayerEntity p : event.getBattle().getPlayers()) {
                    var party = PlayerExtensionsKt.party(p);
                    for (int i = 0; i < 6; i++) {
                        Pokemon mon = party.get(i);
                        if (mon != null && isMissingno(mon)) {
                            event.cancel();
                            p.sendMessage(Text.literal("§d§kAB§r§5 MissingNo. distorts the battlefield - box it to battle. §d§kAB§r"), false);
                            return kotlin.Unit.INSTANCE;
                        }
                    }
                }
            } catch (Throwable ignored) { }
            return kotlin.Unit.INSTANCE;
        });
    }

    public static boolean isMissingno(Pokemon mon) {
        try {
            return mon.getPersistentData().getBoolean(FLAG);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Build the glitch: random starting sprite, flagged forever, nicknamed, never shiny, never battles. */
    public static Pokemon create() {
        Pokemon mon = new Pokemon();
        Species sp = PokemonSpecies.getByName(SPECIES.get(RNG.nextInt(SPECIES.size())));
        if (sp != null) mon.setSpecies(sp);
        mon.setShiny(false);
        mon.setNickname(Text.literal("MissingNo.").copy());
        mon.getPersistentData().putBoolean(FLAG, true);
        return mon;
    }

    private static void rotate(Pokemon mon) {
        try {
            int cur = SPECIES.indexOf(mon.getSpecies().getName().toLowerCase(java.util.Locale.ROOT));
            int next = MissingnoMath.pickNext(Math.max(0, cur), SPECIES.size(), RNG.nextDouble());
            Species sp = PokemonSpecies.getByName(SPECIES.get(next));
            if (sp == null) return;
            mon.setSpecies(sp);
            mon.setNickname(Text.literal("MissingNo.").copy());   // re-assert - species swap may reset display
            if (GpLog.on(GpLog.Level.DEBUG)) GpLog.d("missingno", "rotate", "to", SPECIES.get(next));
        } catch (Throwable ignored) {
            // the glitch must never crash the server; a stuck sprite is thematically acceptable
        }
    }
}
