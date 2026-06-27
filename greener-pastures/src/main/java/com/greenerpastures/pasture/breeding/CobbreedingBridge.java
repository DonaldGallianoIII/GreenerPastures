package com.greenerpastures.pasture.breeding;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.greenerpastures.GreenerPastures;
import com.greenerpastures.pasture.breeding.gui.MonEntry;
import ludichat.cobbreeding.BreedingUtilities;
import ludichat.cobbreeding.Cobbreeding;
import ludichat.cobbreeding.Config;
import ludichat.cobbreeding.CustomProperties;
import ludichat.cobbreeding.EggUtilities;
import ludichat.cobbreeding.PastureBreedingData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * The ONE place Greener Pastures touches Cobbreeding. Its public breeding API, its per-pasture data
 * registry, its breeding/egg blockstate, and its egg-item component format are all isolated and
 * version-guarded here, so the rest of Better Pasture stays clean and the mod <b>fails safe</b>
 * (disables the feature with a log) instead of crashing on an unsupported Cobbreeding version.
 *
 * <p>Coupling surface is entirely PUBLIC Cobbreeding/Cobblemon API — no private reflection, no
 * synthetic lambdas.
 */
public final class CobbreedingBridge {
    private CobbreedingBridge() {}

    private static final String COBBREEDING = "cobbreeding";
    private static final String EGG_VERSION = "2.2.1";
    private static final Random RANDOM = new Random();

    private static boolean available = false;

    // Cobbreeding's egg item-components, resolved by registry id (not by class) so a version bump
    // that keeps the ids keeps working.
    private static ComponentType<String> cName;
    private static ComponentType<Integer> cTimer;
    private static ComponentType<String> cEggInfo;
    private static ComponentType<String> cPokemonProps;
    private static ComponentType<String> cVersion;

    /** Probe Cobbreeding once; on any mismatch stay unavailable and log. Call from module init. */
    @SuppressWarnings("unchecked")
    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded(COBBREEDING)) {
            GreenerPastures.LOG.warn("[better-pasture] Cobbreeding not present — multi-pair breeding disabled.");
            return;
        }
        try {
            cName = (ComponentType<String>) component("name");
            cTimer = (ComponentType<Integer>) component("timer");
            cEggInfo = (ComponentType<String>) component("egg_info");
            cPokemonProps = (ComponentType<String>) component("pokemon_properties");
            cVersion = (ComponentType<String>) component("version");
            if (cName == null || cTimer == null || cEggInfo == null || cPokemonProps == null || cVersion == null) {
                throw new IllegalStateException("Cobbreeding egg components not found by id");
            }
            available = true;
            GreenerPastures.LOG.info("[better-pasture] Cobbreeding bridge ready (egg format {}).", EGG_VERSION);
        } catch (Throwable t) {
            available = false;
            GreenerPastures.LOG.warn("[better-pasture] Cobbreeding API not as expected — multi-pair breeding disabled.", t);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    private static ComponentType<?> component(String path) {
        return Registries.DATA_COMPONENT_TYPE.get(Identifier.of(COBBREEDING, path));
    }

    /** This pasture's egg list from Cobbreeding's registry, or null if it has none yet. */
    public static DefaultedList<ItemStack> eggsAt(BlockPos pos) {
        PastureBreedingData data = PastureBreedingData.registry.get(pos);
        return data == null ? null : data.getEggs();
    }

    /** Drop an egg into the first empty slot of this pasture's list; false if unavailable or full. */
    public static boolean addEgg(BlockPos pos, ItemStack egg) {
        DefaultedList<ItemStack> eggs = eggsAt(pos);
        if (eggs == null || egg == null || egg.isEmpty()) return false;
        for (int i = 0; i < eggs.size(); i++) {
            if (eggs.get(i).isEmpty()) {
                eggs.set(i, egg);
                return true;
            }
        }
        return false; // full
    }

    /** True if the pasture's breeding toggle (Cobbreeding's BREEDING_ACTIVATED state) is on. */
    public static boolean isBreedingActivated(BlockState state) {
        try {
            return state.contains(CustomProperties.BREEDING_ACTIVATED)
                    && state.get(CustomProperties.BREEDING_ACTIVATED);
        } catch (Throwable t) {
            return false;
        }
    }

    /** A fresh breeding interval (ticks) drawn from Cobbreeding's own min/max, so our rate matches. */
    public static int nextBreedingInterval() {
        try {
            Config c = Cobbreeding.INSTANCE.getConfig();
            int min = c.getMinBreedingTimeInTicks();
            int max = c.getMaxBreedingTimeInTicks();
            return min + RANDOM.nextInt(Math.max(1, max - min + 1));
        } catch (Throwable t) {
            return 24000; // ~20 min fallback if config isn't readable on this side
        }
    }

    /**
     * Hold off Cobbreeding's <b>own</b> breeding ticker for a pasture WE manage, so only our
     * configured pairs lay eggs — no rogue random egg from its all-tethered random-pair pick.
     * Public-API only: we keep resetting the pasture's breeding timer so Cobbreeding's
     * "time since last egg" never reaches its interval (it bails before laying). Call this every
     * scan for managed + breeding-active pastures.
     */
    public static void suppressNativeBreeding(BlockPos pos, long now) {
        try {
            PastureBreedingData data = PastureBreedingData.registry.get(pos);
            if (data != null) data.setTime(now);
        } catch (Throwable ignored) {
            // non-fatal; worst case Cobbreeding lays an occasional egg of its own
        }
    }

    /** Sync the pasture's HAS_EGG state to whether it currently holds any egg (visual/GUI cue). */
    public static void refreshHasEgg(World world, BlockPos pos) {
        try {
            DefaultedList<ItemStack> eggs = eggsAt(pos);
            if (eggs == null) return;
            boolean has = eggs.stream().anyMatch(s -> !s.isEmpty());
            BlockState st = world.getBlockState(pos);
            if (st.contains(CustomProperties.HAS_EGG) && st.get(CustomProperties.HAS_EGG) != has) {
                world.setBlockState(pos, st.with(CustomProperties.HAS_EGG, has));
            }
        } catch (Throwable t) {
            // non-fatal; leave state as-is
        }
    }

    /**
     * The result of breeding one pair: the egg item plus shiny telemetry. {@code shiny} is the egg's
     * final shiny state; {@code procShiny} is true only when OUR upgrade's bonus reroll is what made
     * it shiny (powers the dashboard's "shinies this upgrade earned you" stat).
     */
    public record BredEgg(ItemStack stack, boolean shiny, boolean procShiny) {}

    /**
     * Build one egg for a single breeding pair (the two given tethered slots), using Cobbreeding's
     * own egg-gen so the egg is byte-identical to a naturally bred one. Returns null if the pair is
     * incompatible or the bridge is unavailable.
     *
     * <p>The trick: {@code getPossibleEggs} on just these two mons yields only this pair's outcomes,
     * so {@code chooseEgg} (which picks randomly across the collection) is forced to this pair's egg.
     *
     * <p>{@code shinyProcChance} (0..1, from the slotted upgrade's augment) then applies our bounded
     * bonus shiny reroll — see {@link #maybeProcShiny}.
     */
    public static BredEgg buildEggForPair(List<? extends PokemonPastureBlockEntity.Tethering> pairSlots,
                                          double shinyProcChance) {
        if (!available) return null;
        try {
            List<Pokemon> pokemon = BreedingUtilities.getPokemon(pairSlots);
            var possible = BreedingUtilities.getPossibleEggs(pokemon);
            if (possible.isEmpty()) return null;
            PokemonProperties eggData = BreedingUtilities.chooseEgg(possible);
            if (eggData == null || eggData.getSpecies() == null) return null;
            boolean procShiny = maybeProcShiny(eggData, pokemon, shinyProcChance);
            boolean shiny = Boolean.TRUE.equals(eggData.getShiny());
            ItemStack stack = assembleEgg(eggData);
            if (stack == null) return null;
            return new BredEgg(stack, shiny, procShiny);
        } catch (Throwable t) {
            GreenerPastures.LOG.error("[better-pasture] egg build failed; disabling to stay safe.", t);
            available = false;
            return null;
        }
    }

    /**
     * Greener Pastures' ONLY shiny contribution: a bounded, scale-safe bonus. After Cobbreeding has
     * computed the egg's shiny normally (fully honoring server config), if the egg is NOT already
     * shiny we fire — with probability {@code procChance} (the upgrade's augment) — exactly ONE extra
     * shiny reroll at the SAME effective rate Cobbreeding would use (so it rewards boosted-server
     * grinders too). Per egg the boost is ×(1+procChance); because each egg sees only its own
     * pasture's augment, the aggregate across any number of pastures is also just ×(1+procChance) — it
     * is mathematically incapable of exploding. Uses {@code nextDouble()} so fractional odds are exact
     * (no int-floor). Returns true iff this reroll is what made the egg shiny.
     */
    private static boolean maybeProcShiny(PokemonProperties eggData, List<Pokemon> parents, double procChance) {
        try {
            if (procChance <= 0.0 || parents.size() < 2) return false;
            if (Boolean.TRUE.equals(eggData.getShiny())) return false;   // already shiny — nothing to add
            if (RANDOM.nextDouble() >= procChance) return false;         // the proc didn't fire
            double odds = effectiveShinyOdds(parents.get(0), parents.get(1));
            double p = (odds < 1.0) ? 1.0 : 1.0 / odds;                  // mirror Cobbreeding's <1 ⇒ guaranteed
            if (RANDOM.nextDouble() < p) {
                eggData.setShiny(true);
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;   // a bonus roll must NEVER break egg-gen
        }
    }

    /**
     * The effective shiny denominator for this pair, replicated from Cobbreeding's {@code calcShiny}
     * using only public config: base {@code Cobblemon.shinyRate}, divided by the server's configured
     * {@code always} / {@code crystal} (per shiny parent) / {@code masuda} (differing OT) multipliers.
     * Returns +∞ when a configured multiplier is 0 (server intent: "never shiny" for that path) so the
     * bonus contributes nothing. Falls back to the raw base rate if the multipliers are unreadable.
     */
    private static double effectiveShinyOdds(Pokemon a, Pokemon b) {
        double odds;
        try {
            odds = Cobblemon.INSTANCE.getConfig().getShinyRate();
        } catch (Throwable t) {
            return Double.POSITIVE_INFINITY;   // can't read the base rate ⇒ no bonus
        }
        try {
            Map<String, Float> m = Cobbreeding.INSTANCE.getConfig().getShinyMethod();
            if (m == null) return odds;
            if (m.containsKey("always")) {
                float f = m.get("always");
                if (f == 0f) return Double.POSITIVE_INFINITY;
                odds /= f;
            }
            if (m.containsKey("crystal")) {
                float f = m.get("crystal");
                if (a.getShiny()) { if (f == 0f) return Double.POSITIVE_INFINITY; odds /= f; }
                if (b.getShiny()) { if (f == 0f) return Double.POSITIVE_INFINITY; odds /= f; }
            }
            if (m.containsKey("masuda") && !sameTrainer(a, b)) {
                float f = m.get("masuda");
                if (f == 0f) return Double.POSITIVE_INFINITY;
                odds /= f;
            }
        } catch (Throwable t) {
            // partial read — use whatever we have (at worst the base rate); never amplify wrongly
        }
        return odds;
    }

    /** Two parents count as "same trainer" exactly as Cobbreeding's masuda check compares them. */
    private static boolean sameTrainer(Pokemon a, Pokemon b) {
        return Objects.equals(trainerOf(a), trainerOf(b));
    }

    private static String trainerOf(Pokemon p) {
        try {
            String ot = p.getOriginalTrainer();
            if (ot != null) return ot;
            ServerPlayerEntity owner = p.getOwnerPlayer();
            return owner != null ? owner.getUuidAsString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Mirrors Cobbreeding's egg-item construction so its hatching recognises our egg. */
    private static ItemStack assembleEgg(PokemonProperties eggData) {
        String speciesName = eggData.getSpecies();
        if (speciesName.equals("random")) {
            return null; // v1: leave 'random' eggs to Cobbreeding's own ticker (avoids extra coupling)
        }
        Species species = PokemonSpecies.INSTANCE.getByName(speciesName);
        if (species == null) return null;

        FormData form = species.getStandardForm();
        if (eggData.getForm() != null) {
            form = species.getFormByShowdownId(eggData.getForm());
        }
        ItemStack egg = EggUtilities.selectEggItem(form);

        String displayName = speciesName;
        if (eggData.getForm() != null && !form.getName().equals("Normal")) {
            displayName = speciesName + " " + form.getName();
        }
        egg.set(cName, displayName);
        egg.set(cTimer, EggUtilities.calculateTimer(species));
        if (!eggEncryptionEnabled()) {
            egg.set(cPokemonProps, eggData.asString(" "));
        }
        egg.set(cEggInfo, EggUtilities.encrypt(eggData));
        egg.set(cVersion, EGG_VERSION);
        return egg;
    }

    /** Snapshot this pasture's tethered mons for the wand GUI (server-side read of Cobblemon data). */
    public static java.util.List<MonEntry> rosterOf(PokemonPastureBlockEntity be, PastureData pd) {
        java.util.List<MonEntry> out = new java.util.ArrayList<>();
        if (be == null) return out;
        try {
            for (PokemonPastureBlockEntity.Tethering t : be.getTetheredPokemon()) {
                java.util.UUID id = t.getTetheringId();
                String species;
                try {
                    Pokemon pkm = t.getPokemon();
                    species = (pkm != null) ? pkm.getSpecies().getName() : "?";
                } catch (Throwable ex) {
                    species = "?";
                }
                int bucket = pd.pairings.getOrDefault(id, 0);
                out.add(new MonEntry(id, species, species, bucket));
            }
        } catch (Throwable t) {
            GreenerPastures.LOG.warn("[better-pasture] could not read pasture roster", t);
        }
        return out;
    }

    private static boolean eggEncryptionEnabled() {
        try {
            return Cobbreeding.INSTANCE.getConfig().getEggEncryptionEnabled();
        } catch (Throwable t) {
            return false; // default to plaintext like Cobbreeding does when config isn't initialised
        }
    }
}
