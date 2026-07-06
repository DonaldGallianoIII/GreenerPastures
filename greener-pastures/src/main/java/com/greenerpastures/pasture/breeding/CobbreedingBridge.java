package com.greenerpastures.pasture.breeding;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.abilities.CommonAbilityType;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity;
import com.cobblemon.mod.common.pokemon.EVs;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.IVs;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.greenerpastures.GreenerPastures;
import com.greenerpastures.core.GpLog;
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

import java.util.ArrayList;
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
 * <p>Coupling surface is entirely PUBLIC Cobbreeding/Cobblemon API - no private reflection, no
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
            GreenerPastures.LOG.warn("[better-pasture] Cobbreeding not present - multi-pair breeding disabled.");
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
            GreenerPastures.LOG.warn("[better-pasture] Cobbreeding API not as expected - multi-pair breeding disabled.", t);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    /** True if {@code name} is a real Cobblemon species - for breeding-goal validation. Blank = "any" = valid;
     *  if the species API is unavailable we don't block the player (return true). */
    public static boolean isSpecies(String name) {
        if (name == null || name.isBlank()) return true;
        try { return PokemonSpecies.INSTANCE.getByName(name.trim().toLowerCase()) != null; }
        catch (Throwable t) { return true; }
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

    /** Cobbreeding's configured MAX breeding interval (the best case for a Speed comparison); fallback 24000. */
    public static long maxBreedingIntervalTicks() {
        try {
            return Cobbreeding.INSTANCE.getConfig().getMaxBreedingTimeInTicks();
        } catch (Throwable t) {
            return 24000L;
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
     * configured pairs lay eggs - no rogue random egg from its all-tethered random-pair pick.
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
            boolean has = false;   // indexed, zero-alloc: this runs every produce/drain cycle per active pasture
            for (int i = 0; i < eggs.size(); i++) { if (!eggs.get(i).isEmpty()) { has = true; break; } }
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
    public record BredEgg(ItemStack stack, boolean shiny, boolean procShiny,
                          String species, int ivTotal, int perfectIvs) {}

    /** Sum of the egg's 6 IVs (0 if the spread is unknown) - for the goal tracker, computed off {@code eggData}. */
    private static int ivTotal(PokemonProperties eggData) {
        IVs ivs = eggData.getIvs();
        if (ivs == null) return 0;
        int total = 0;
        for (Stat s : Stats.Companion.getPERMANENT()) {
            Integer v = ivs.get(s);
            if (v != null) total += v;
        }
        return total;
    }

    /** Count of the egg's 31-IV stats (0..6) - for the goal tracker, computed off {@code eggData}. */
    private static int perfectIvs(PokemonProperties eggData) {
        IVs ivs = eggData.getIvs();
        if (ivs == null) return 0;
        int perfect = 0;
        for (Stat s : Stats.Companion.getPERMANENT()) {
            Integer v = ivs.get(s);
            if (v != null && v >= 31) perfect++;
        }
        return perfect;
    }

    /**
     * Build one egg for a single breeding pair (the two given tethered slots), using Cobbreeding's
     * own egg-gen so the egg is byte-identical to a naturally bred one. Returns null if the pair is
     * incompatible or the bridge is unavailable.
     *
     * <p>The trick: {@code getPossibleEggs} on just these two mons yields only this pair's outcomes,
     * so {@code chooseEgg} (which picks randomly across the collection) is forced to this pair's egg.
     *
     * <p>The effective augments then shape the egg: {@code shinyProcChance} (0..1) fires our bounded bonus
     * shiny reroll ({@link #maybeProcShiny}); {@code ivFloor} guarantees that many perfect (31) IVs
     * ({@link #applyIvFloor}); {@code evSpread} pre-sets the per-stat EV allocation
     * ({@link #applyEvSpread}). All three mutate the egg's properties BEFORE it's encrypted, so Cobbreeding
     * carries them to the hatchling.
     */
    public static BredEgg buildEggForPair(List<? extends PokemonPastureBlockEntity.Tethering> pairSlots,
                                          EggShape shape) {
        if (!available) return null;
        try {
            List<Pokemon> pokemon = BreedingUtilities.getPokemon(pairSlots);
            var possible = BreedingUtilities.getPossibleEggs(pokemon);
            if (possible.isEmpty()) {
                // Cobbreeding rejected the pair (no shared egg group / gender / Undiscovered) - a "dead pair" that
                // can't lay. Surface it (BUG-006) so a mis-wired graph pairing is visible instead of silently idle.
                if (pokemon.size() >= 2) GpLog.d("breeder", "pair_incompatible",
                        "a", pokemon.get(0).getSpecies().getName(), "b", pokemon.get(1).getSpecies().getName());
                return null;
            }
            PokemonProperties eggData = BreedingUtilities.chooseEgg(possible);
            if (eggData == null || eggData.getSpecies() == null) return null;
            boolean procShiny = maybeProcShiny(eggData, pokemon, shape.shinyProcChance());
            applyIvFloor(eggData, shape.ivFloor());          // guarantee N perfect (31) IVs - raise-only
            applyEvSpread(eggData, shape.evSpread());         // pre-set the per-stat EV allocation (BUG-002)
            applyNature(eggData, shape.nature());            // lock the egg's nature (Nature selector augment)
            applyBall(eggData, shape.ball());                // lock the egg's ball (Ball selector augment)
            applyHiddenAbility(eggData, shape.forceHiddenAbility());   // force the species' hidden ability
            applyEggMoves(eggData, shape.teachEggMoves());             // teach the species' egg moves
            boolean shiny = Boolean.TRUE.equals(eggData.getShiny());
            ItemStack stack = assembleEgg(eggData);
            if (stack == null) return null;
            applyHatchHaste(stack, shape.hatchLevel());       // scale the egg's Cobbreeding TIMER (Hatch Haste augment)
            return new BredEgg(stack, shiny, procShiny, eggData.getSpecies(), ivTotal(eggData), perfectIvs(eggData));
        } catch (Throwable t) {
            GreenerPastures.LOG.error("[better-pasture] egg build failed; disabling to stay safe.", t);
            available = false;
            return null;
        }
    }

    /**
     * Greener Pastures' ONLY shiny contribution: a bounded, scale-safe bonus. After Cobbreeding has
     * computed the egg's shiny normally (fully honoring server config), if the egg is NOT already
     * shiny we fire - with probability {@code procChance} (the upgrade's augment) - exactly ONE extra
     * shiny reroll at the SAME effective rate Cobbreeding would use (so it rewards boosted-server
     * grinders too). Per egg the boost is ×(1+procChance); because each egg sees only its own
     * pasture's augment, the aggregate across any number of pastures is also just ×(1+procChance) - it
     * is mathematically incapable of exploding. Uses {@code nextDouble()} so fractional odds are exact
     * (no int-floor). Returns true iff this reroll is what made the egg shiny.
     */
    private static boolean maybeProcShiny(PokemonProperties eggData, List<Pokemon> parents, double procChance) {
        try {
            if (procChance <= 0.0 || parents.size() < 2) return false;
            if (Boolean.TRUE.equals(eggData.getShiny())) return false;                // already shiny - nothing to add
            if (!ShinyOdds.procFires(procChance, RANDOM.nextDouble())) return false;  // the proc didn't fire
            double odds = effectiveShinyOdds(parents.get(0), parents.get(1));         // (rolled only after the proc fires)
            boolean hit = ShinyOdds.shinyHits(odds, RANDOM.nextDouble());
            if (hit) eggData.setShiny(true);
            return hit;
        } catch (Throwable t) {
            return false;   // a bonus roll must NEVER break egg-gen
        }
    }

    /** Hatch Haste (Deuce, 2026-07-05): scale a freshly built egg's Cobbreeding hatch TIMER per the augment
     *  level (HatchHaste math: ×0.5/×0.25/×0.1, 1s floor) - {@code cTimer} is the same registry-id-resolved
     *  component the rest of the bridge uses. Fail-soft: anything odd → the egg ships with its vanilla timer. */
    private static void applyHatchHaste(ItemStack stack, int level) {
        if (level <= 0 || stack == null || stack.isEmpty() || cTimer == null) return;
        try {
            int cur = stack.getOrDefault(cTimer, 600);
            int scaled = HatchHaste.scaledTimer(cur, level);
            if (scaled >= cur) return;
            stack.set(cTimer, scaled);
            GpLog.d("breeding", "hatch_haste", "level", level, "from", cur, "to", scaled);
        } catch (Throwable ignored) {
            // egg-shaping must never abort egg-gen
        }
    }

    /**
     * IV Floor augment: guarantee at least {@code count} perfect (31) IVs on the egg. A true FLOOR - IVs the
     * parents already passed at 31 count toward it, and we never lower an existing IV; we only promote enough
     * not-yet-perfect stats to reach {@code count}. Mutates {@code eggData.getIvs()} in place (Cobbreeding
     * applies it at hatch via {@code PokemonProperties.apply → setIV}). Never throws - egg-gen must not break.
     */
    private static void applyIvFloor(PokemonProperties eggData, int count) {
        if (count <= 0) return;
        try {
            IVs ivs = eggData.getIvs();
            if (ivs == null) {                              // no inherited spread (rare) → full random with N perfects
                eggData.setIvs(IVs.createRandomIVs(Math.min(6, count)));
                return;
            }
            int already = 0;
            List<Stat> candidates = new ArrayList<>();      // the not-yet-perfect stats, promotion pool
            for (Stat s : Stats.Companion.getPERMANENT()) {
                Integer v = ivs.get(s);
                if (v != null && v >= 31) already++;
                else candidates.add(s);
            }
            int need = count - already;                     // inheritance may already satisfy the floor
            if (need <= 0) return;
            java.util.Collections.shuffle(candidates);      // WHICH stats hit 31 must be random - fixed PERMANENT order made every floored egg HP/Atk/Def (Deuce, QA 2026-07-04)
            List<String> promoted = new ArrayList<>();
            for (Stat s : candidates) {
                if (need <= 0) break;
                ivs.set(s, 31); need--;
                promoted.add(s.getShowdownId());
            }
            GpLog.d("breeding", "iv_floor", "species", String.valueOf(eggData.getSpecies()),
                    "already", already, "promoted", String.join(",", promoted));
        } catch (Throwable t) {
            // egg-shaping must never abort egg-gen
        }
    }

    /**
     * EV allocation augment (BUG-002): pre-set the {@link EvSpread}'s per-stat EVs onto the egg
     * (HP/Atk/Def/SpA/SpD/Spe), replacing the old flat "+N on every stat" blanket. {@code EVs.set} is absolute and
     * silently caps the running total at Cobblemon's 510; the spread is already clamped (≤252/stat, ≤510 total).
     * Raise-only (never lowers an inherited EV). Carried at hatch via {@code apply → setEV}.
     */
    private static void applyEvSpread(PokemonProperties eggData, EvSpread spread) {
        if (spread == null || spread.isEmpty()) return;
        try {
            EVs evs = eggData.getEvs();
            if (evs == null) { evs = new EVs(); eggData.setEvs(evs); }
            setEv(evs, Stats.HP, spread.hp());
            setEv(evs, Stats.ATTACK, spread.atk());
            setEv(evs, Stats.DEFENCE, spread.def());
            setEv(evs, Stats.SPECIAL_ATTACK, spread.spa());
            setEv(evs, Stats.SPECIAL_DEFENCE, spread.spd());
            setEv(evs, Stats.SPEED, spread.spe());
            GpLog.d("breeding", "ev_spread", "species", String.valueOf(eggData.getSpecies()),
                    "evs", spread.hp() + "/" + spread.atk() + "/" + spread.def() + "/"
                            + spread.spa() + "/" + spread.spd() + "/" + spread.spe());
        } catch (Throwable t) {
            // egg-shaping must never abort egg-gen
        }
    }

    /** Raise-only EV set for one stat (Cobblemon's {@code set} no-ops if it would push the 510 total over). */
    private static void setEv(EVs evs, Stat stat, int value) {
        if (value > 0 && evs.getOrDefault(stat) < value) evs.set(stat, value);
    }

    /**
     * Nature lock (the Nature selector augment): force the bred egg's nature to {@code natureId} (a Cobblemon
     * nature spec token, e.g. {@code "adamant"}). Writes it onto the egg's {@code PokemonProperties}, exactly as
     * IV/EV are written - Cobbreeding carries it to the hatchling via {@code PokemonProperties.apply → nature},
     * overriding vanilla nature inheritance. {@code null}/blank ⇒ no lock (vanilla inheritance). Cobblemon
     * validates the token at hatch, so a bad id simply lapses rather than corrupting the egg. Never throws.
     */
    private static void applyNature(PokemonProperties eggData, String natureId) {
        if (natureId == null || natureId.isBlank()) return;
        try {
            eggData.setNature(natureId);
            GpLog.d("breeding", "nature_lock", "species", String.valueOf(eggData.getSpecies()), "nature", natureId);
        } catch (Throwable t) {
            // egg-shaping must never abort egg-gen
        }
    }

    /**
     * Ball lock (the Ball selector augment): force the bred egg to hatch in {@code ballId} (a Cobblemon ball spec
     * token, e.g. {@code "cobblemon:poke_ball"}). Same seam as nature - writes {@code PokemonProperties.setPokeball},
     * carried at hatch via {@code apply → caughtBall}, overriding vanilla "inherit mother's ball". {@code null}/blank
     * ⇒ no lock; a bad id lapses to the default ball rather than corrupting. Never throws.
     */
    private static void applyBall(PokemonProperties eggData, String ballId) {
        if (ballId == null || ballId.isBlank()) return;
        try {
            eggData.setPokeball(ballId);
            GpLog.d("breeding", "ball_lock", "species", String.valueOf(eggData.getSpecies()), "ball", ballId);
        } catch (Throwable t) {
            // egg-shaping must never abort egg-gen
        }
    }

    /**
     * Hidden Ability lock (the Ability augment): force the bred egg to the species' <b>hidden</b> ability. Looks the
     * egg's species up, scans its {@link AbilityPool} for the entry whose {@link PotentialAbility#getType()} is NOT
     * the {@code CommonAbilityType} (i.e. the hidden one), and writes its name onto the egg via
     * {@code setAbility} - carried at hatch via {@code apply → ability}. Fails safe: a species with no hidden
     * ability (or any lookup hiccup) simply isn't locked, keeping vanilla ability rolling. Never throws.
     */
    private static void applyHiddenAbility(PokemonProperties eggData, boolean force) {
        if (!force) return;
        try {
            Species species = PokemonSpecies.getByName(eggData.getSpecies());
            if (species == null) return;
            String hidden = null;
            for (PotentialAbility pa : species.getAbilities()) {     // AbilityPool is Iterable<PotentialAbility>
                if (pa.getType() != CommonAbilityType.INSTANCE) {    // non-common type == the hidden ability
                    hidden = pa.getTemplate().getName();
                    break;
                }
            }
            if (hidden == null || hidden.isBlank()) return;          // no hidden ability for this species → no lock
            eggData.setAbility(hidden);
            GpLog.d("breeding", "ability_lock", "species", String.valueOf(eggData.getSpecies()), "ability", hidden);
        } catch (Throwable t) {
            // egg-shaping must never abort egg-gen
        }
    }

    /**
     * Egg Moves teaching (the Egg Moves augment): give the hatchling the moves it can normally only inherit by
     * chain-breeding. Looks up the egg's species, reads {@code Learnset.getEggMoves()}, and writes the first few
     * (capped at the 4 move slots) onto the egg via {@code setMoves} - carried at hatch via {@code apply → moves}.
     * Fails safe: a species with no egg moves (or any hiccup) is left with its normal level-up moveset. Never throws.
     */
    private static void applyEggMoves(PokemonProperties eggData, boolean teach) {
        if (!teach) return;
        try {
            Species species = PokemonSpecies.getByName(eggData.getSpecies());
            if (species == null) return;
            List<MoveTemplate> eggMoves = species.getMoves().getEggMoves();
            if (eggMoves == null || eggMoves.isEmpty()) return;
            List<String> names = new ArrayList<>(4);
            for (MoveTemplate mt : eggMoves) {
                if (names.size() >= 4) break;                        // a Pokémon has only 4 move slots
                String n = mt == null ? null : mt.getName();
                if (n != null && !n.isBlank()) names.add(n);
            }
            if (names.isEmpty()) return;
            eggData.setMoves(names);
            GpLog.d("breeding", "egg_moves", "species", String.valueOf(eggData.getSpecies()), "moves", String.join(",", names));
        } catch (Throwable t) {
            // egg-shaping must never abort egg-gen
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
        double baseRate;
        try {
            baseRate = Cobblemon.INSTANCE.getConfig().getShinyRate();
        } catch (Throwable t) {
            return Double.POSITIVE_INFINITY;   // can't read the base rate ⇒ no bonus
        }
        try {
            Map<String, Float> m = Cobbreeding.INSTANCE.getConfig().getShinyMethod();
            Float always  = (m != null && m.containsKey("always"))  ? m.get("always")  : null;
            Float crystal = ShinyOdds.flooredCrystal((m != null && m.containsKey("crystal")) ? m.get("crystal") : null);
            Float masuda  = (m != null && m.containsKey("masuda"))  ? m.get("masuda")  : null;
            // Pure math (unit-tested in ShinyOddsTest); replicates Cobbreeding's calcShiny.
            return ShinyOdds.effectiveOdds(baseRate, always, crystal, masuda,
                    a.getShiny(), b.getShiny(), !sameTrainer(a, b));
        } catch (Throwable t) {
            return baseRate;   // partial read - fall back to the base rate; never amplify wrongly
        }
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

    private static final com.google.gson.Gson GSON_CB = new com.google.gson.Gson();

    /** A tethered mon's inspectable stats (ivs / nature / gender / shiny / OT) as JSON - for the console's parent
     *  inspector. Every field is best-effort so a reflection hiccup on one never blanks the rest. */
    private static String monStats(Pokemon pkm) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        try {
            IVs ivs = pkm.getIvs();
            com.google.gson.JsonArray iv = new com.google.gson.JsonArray();
            for (Stat s : Stats.Companion.getPERMANENT()) { Integer v = (ivs == null) ? null : ivs.get(s); iv.add(v == null ? 0 : v); }
            o.add("ivs", iv);
        } catch (Throwable t) { }
        try { o.addProperty("nature", natureName(pkm)); } catch (Throwable t) { }
        try { o.addProperty("gender", String.valueOf(pkm.getGender())); } catch (Throwable t) { }
        try { o.addProperty("shiny", Boolean.TRUE.equals(pkm.getShiny())); } catch (Throwable t) { }
        try { String ot = trainerOf(pkm); o.addProperty("ot", ot == null ? "" : ot); } catch (Throwable t) { }
        return GSON_CB.toJson(o);
    }

    private static String natureName(Pokemon pkm) {
        try { return pkm.getNature().getName().getPath(); } catch (Throwable t) { return ""; }
    }

    /** The server's shiny-method multipliers (always / crystal / masuda) - for the console's shiny-breeding indicator. */
    public static java.util.Map<String, Float> shinyMethods() {
        // Crystal is FLOORED here too, so the console badge and the odds math can never disagree
        // about what an egg actually experiences.
        try {
            java.util.Map<String, Float> m = Cobbreeding.INSTANCE.getConfig().getShinyMethod();
            java.util.Map<String, Float> out = new java.util.HashMap<>(m == null ? java.util.Map.of() : m);
            out.put("crystal", ShinyOdds.flooredCrystal(out.get("crystal")));
            return out;
        }
        catch (Throwable t) { return java.util.Map.of(); }
    }

    /** Snapshot this pasture's tethered mons for the wand GUI (server-side read of Cobblemon data). */
    public static java.util.List<MonEntry> rosterOf(PokemonPastureBlockEntity be, PastureData pd) {
        return rosterOf(be, pd, true);
    }

    /** {@code withStats=false} is the cache-prefetch shape (perf-audit R3 F6): species + buckets only, no
     *  per-mon reflective stats JSON - the parent inspector's data arrives with the focused (full) push. */
    public static java.util.List<MonEntry> rosterOf(PokemonPastureBlockEntity be, PastureData pd, boolean withStats) {
        java.util.List<MonEntry> out = new java.util.ArrayList<>();
        if (be == null) return out;
        try {
            for (PokemonPastureBlockEntity.Tethering t : new java.util.ArrayList<>(be.getTetheredPokemon())) {
                java.util.UUID id = t.getTetheringId();
                String species = "?", stats = "{}";
                try {
                    Pokemon pkm = t.getPokemon();
                    if (pkm != null) { species = pkm.getSpecies().getName(); if (withStats) stats = monStats(pkm); }
                } catch (Throwable ex) { }
                int bucket = pd.pairings.getOrDefault(id, 0);
                out.add(new MonEntry(id, species, species, bucket, stats));
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
