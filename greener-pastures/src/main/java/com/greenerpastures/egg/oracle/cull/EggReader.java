package com.greenerpastures.egg.oracle.cull;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Cobbreeding eggs WITHOUT a compile-time dependency on Cobbreeding/Cobblemon.
 *
 * Cobbreeding bakes the hatchling's full PokemonProperties (IVs, shiny, nature, ...) into the egg
 * at breed time and exposes it publicly:
 *   ludichat.cobbreeding.EggUtilities.extractProperties(ItemStack) -> PokemonProperties   (decrypts internally)
 *   PokemonProperties.getShiny() -> Boolean
 *   PokemonProperties.getIvs()   -> IVs  (extends PokemonStats implements Iterable<Map.Entry<Stat,Integer>>)
 *
 * We resolve those by reflection once and degrade gracefully (shiny-by-name only) if the mod
 * isn't present or its API changed. Nothing here ever throws to the caller.
 */
public final class EggReader {
    private EggReader() {}

    public static final Logger LOGGER = LoggerFactory.getLogger("eggoracle");
    private static final char SHINY_STAR = '★';   // Cobbreeding appends U+2605 to shiny egg names

    // Reflection handles, resolved lazily (all-or-nothing).
    private static boolean initDone;
    private static Object eggUtils;            // EggUtilities.INSTANCE (receiver; ignored if method is static)
    private static Method extractProperties;   // (ItemStack) -> PokemonProperties
    private static Method getShiny;            // PokemonProperties.getShiny() -> Boolean
    private static Method getIvs;              // PokemonProperties.getIvs() -> IVs (Iterable)
    private static Method getSpecies;          // PokemonProperties.getSpecies() -> String

    private static synchronized void init() {
        if (initDone) return;
        initDone = true;
        try {
            Class<?> utils = Class.forName("ludichat.cobbreeding.EggUtilities");
            extractProperties = utils.getMethod("extractProperties", ItemStack.class);
            try {
                Field inst = utils.getField("INSTANCE");
                eggUtils = inst.get(null);
            } catch (NoSuchFieldException nsf) {
                eggUtils = null;   // plain-static method, no INSTANCE receiver needed
            }
            Class<?> props = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            getShiny = props.getMethod("getShiny");
            getIvs = props.getMethod("getIvs");
            try { getSpecies = props.getMethod("getSpecies"); } catch (Throwable t) { getSpecies = null; }
            LOGGER.info("[EggOracle] Cobbreeding egg API found — IV culling enabled.");
        } catch (Throwable t) {
            extractProperties = null;
            LOGGER.warn("[EggOracle] Cobbreeding egg API unavailable ({}); shiny-by-name fallback only.", t.toString());
        }
    }

    /** True once Cobbreeding's egg API resolved — i.e. IV/quality reading is live. */
    public static boolean apiAvailable() {
        init();
        return extractProperties != null;
    }

    /** Any Cobbreeding Pokémon egg (any type, shiny or not). Pure id check — never throws. */
    public static boolean isEgg(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id.getPath().contains("pokemon_egg")
                || (id.getNamespace().contains("cobb") && id.getPath().contains("egg"));
    }

    // Per-ItemStack identity cache (an egg's baked properties are immutable once bred). The Renderer
    // re-reads the same tray stacks every cull tick and BioBank bulk-deposits many at once — without this,
    // each call re-ran Cobbreeding's reflective decrypt. Synchronized access-order LRU, bounded (re-audit N1).
    private static final int CACHE_MAX = 4096;
    private static final Map<ItemStack, Decoded> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<ItemStack, Decoded>(256, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<ItemStack, Decoded> e) {
                    return size() > CACHE_MAX;
                }
            });

    /** One egg's decoded properties (species + IV/shiny), from a single decrypt. */
    private record Decoded(String species, EggInfo info) {}

    private static Decoded decode(ItemStack stack) {
        Decoded cached = CACHE.get(stack);
        if (cached != null) return cached;
        Decoded d = decodeUncached(stack);
        CACHE.put(stack, d);
        return d;
    }

    /** Read everything off an egg in ONE reflective decrypt — the only place that invokes Cobbreeding. */
    private static Decoded decodeUncached(ItemStack stack) {
        init();
        boolean shiny = false, ivsKnown = false;
        int ivTotal = 0, perfect = 0;
        String species = null;
        if (extractProperties != null) {
            try {
                Object props = extractProperties.invoke(eggUtils, stack);
                if (props != null) {
                    Object sh = getShiny.invoke(props);
                    if (sh instanceof Boolean b) shiny = b;
                    Object ivs = getIvs.invoke(props);
                    if (ivs instanceof Iterable<?> it) {
                        for (Object e : it) {
                            if (e instanceof Map.Entry<?, ?> me && me.getValue() instanceof Integer v) {
                                ivTotal += v;
                                if (v >= 31) perfect++;
                                ivsKnown = true;
                            }
                        }
                    }
                    if (getSpecies != null) {
                        Object sp = getSpecies.invoke(props);
                        if (sp instanceof String s && !s.isBlank()) species = normalizeSpecies(s);
                    }
                }
            } catch (Throwable t) {
                // fall through to the name-based fallbacks
            }
        }
        if (!shiny) shiny = shinyByName(stack);            // ★ in the name flags a shiny even if the API missed it
        if (species == null) species = speciesByName(stack);
        return new Decoded(species, new EggInfo(shiny, ivsKnown, ivTotal, perfect));
    }

    /** Read what we can off an egg. Returns null if it isn't an egg. Never throws. Cached per stack. */
    public static EggInfo read(ItemStack stack) {
        if (!isEgg(stack)) return null;
        return decode(stack).info();
    }

    /**
     * Best-effort species key for an egg (server-safe). Cobbreeding's baked {@code getSpecies()} with a
     * display-name fallback, normalized to a lowercase namespace-free token (e.g. "charmander"). Never
     * throws; "unknown" if undetermined. Cached per stack — shares the one decrypt with {@link #read}.
     */
    public static String species(ItemStack stack) {
        if (!isEgg(stack)) return "unknown";
        return decode(stack).species();
    }

    private static String normalizeSpecies(String s) {
        int c = s.indexOf(':');
        if (c >= 0) s = s.substring(c + 1);
        return s.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String speciesByName(ItemStack stack) {
        String n = stack.getName().getString().replace(String.valueOf(SHINY_STAR), "").trim();
        int idx = n.toLowerCase(java.util.Locale.ROOT).lastIndexOf("egg");
        if (idx > 0) n = n.substring(0, idx).trim();
        return n.isBlank() ? "unknown" : normalizeSpecies(n);
    }

    private static boolean shinyByName(ItemStack stack) {
        // MinecraftClient is client-only; on a dedicated server this method must never touch it. Guard first
        // so read()/species() stay server-safe even if a future caller invokes them off the client (bug-hunt #6).
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        try {
            List<Text> lines = stack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
            for (Text t : lines) {
                if (t.getString().indexOf(SHINY_STAR) >= 0) return true;
            }
        } catch (Exception ignored) {
            // some modded tooltips can throw; treat as non-shiny
        }
        return false;
    }
}
