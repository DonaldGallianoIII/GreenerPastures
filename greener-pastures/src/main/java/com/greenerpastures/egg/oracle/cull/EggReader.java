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
    private static Method getEvs;              // PokemonProperties.getEvs() -> EVs (Iterable) - may be absent
    private static Method getNature;           // PokemonProperties.getNature() -> Nature/String
    private static Method getGender;           // PokemonProperties.getGender() -> Gender/String
    private static Method getAbility;          // PokemonProperties.getAbility() -> Ability/String

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
            getEvs = tryMethod(props, "getEvs");
            getNature = tryMethod(props, "getNature");
            getGender = tryMethod(props, "getGender");
            getAbility = tryMethod(props, "getAbility");
            LOGGER.info("[EggOracle] Cobbreeding egg API found - IV culling enabled.");
        } catch (Throwable t) {
            extractProperties = null;
            LOGGER.warn("[EggOracle] Cobbreeding egg API unavailable ({}); shiny-by-name fallback only.", t.toString());
        }
    }

    /** True once Cobbreeding's egg API resolved - i.e. IV/quality reading is live. */
    public static boolean apiAvailable() {
        init();
        return extractProperties != null;
    }

    /** Any Cobbreeding Pokémon egg (any type, shiny or not). Pure id check - never throws. */
    public static boolean isEgg(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id.getPath().contains("pokemon_egg")
                || (id.getNamespace().contains("cobb") && id.getPath().contains("egg"));
    }

    // Per-ItemStack identity cache (an egg's baked properties are immutable once bred). The Renderer
    // re-reads the same tray stacks every cull tick and BioBank bulk-deposits many at once - without this,
    // each call re-ran Cobbreeding's reflective decrypt. Synchronized access-order LRU, bounded (re-audit N1).
    private static final int CACHE_MAX = 4096;
    private static final Map<ItemStack, Decoded> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<ItemStack, Decoded>(256, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<ItemStack, Decoded> e) {
                    return size() > CACHE_MAX;
                }
            });

    /** One egg's decoded properties (species + IV/shiny summary + the full competitive card), one decrypt. */
    private record Decoded(String species, EggInfo info, EggCard card) {}

    private static Decoded decode(ItemStack stack) {
        Decoded cached = CACHE.get(stack);
        if (cached != null) return cached;
        Decoded d = decodeUncached(stack);
        CACHE.put(stack, d);
        return d;
    }

    /** Read everything off an egg in ONE reflective decrypt - the only place that invokes Cobbreeding. */
    private static Decoded decodeUncached(ItemStack stack) {
        init();
        boolean shiny = false, ivsKnown = false;
        int ivTotal = 0, perfect = 0;
        int[] ivs = new int[6], evs = new int[6];
        String species = null, nature = "", gender = "", ability = "";
        if (extractProperties != null) {
            try {
                Object props = extractProperties.invoke(eggUtils, stack);
                if (props != null) {
                    Object sh = getShiny.invoke(props);
                    if (sh instanceof Boolean b) shiny = b;
                    Object ivObj = getIvs.invoke(props);
                    if (ivObj instanceof Iterable<?> it) {
                        for (Object e : it) {
                            if (e instanceof Map.Entry<?, ?> me && me.getValue() instanceof Integer v) {
                                ivTotal += v;
                                if (v >= 31) perfect++;
                                ivsKnown = true;
                                int slot = statSlot(me.getKey());
                                if (slot >= 0) ivs[slot] = v;
                            }
                        }
                    }
                    if (getEvs != null && getEvs.invoke(props) instanceof Iterable<?> evIt) {
                        for (Object e : evIt) {
                            if (e instanceof Map.Entry<?, ?> me && me.getValue() instanceof Integer v) {
                                int slot = statSlot(me.getKey());
                                if (slot >= 0) evs[slot] = v;
                            }
                        }
                    }
                    nature = readString(getNature, props);
                    gender = readString(getGender, props);
                    ability = readString(getAbility, props);
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
        return new Decoded(species, new EggInfo(shiny, ivsKnown, ivTotal, perfect),
                new EggCard(species, shiny, ivs, evs, nature, gender, ability, ivsKnown));   // unreadable decrypt -> the graph MUST keep (sacred rule, review C1)
    }

    /** Read what we can off an egg. Returns null if it isn't an egg. Never throws. Cached per stack. */
    public static EggInfo read(ItemStack stack) {
        if (!isEgg(stack)) return null;
        return decode(stack).info();
    }

    /**
     * Best-effort species key for an egg (server-safe). Cobbreeding's baked {@code getSpecies()} with a
     * display-name fallback, normalized to a lowercase namespace-free token (e.g. "charmander"). Never
     * throws; "unknown" if undetermined. Cached per stack - shares the one decrypt with {@link #read}.
     */
    public static String species(ItemStack stack) {
        if (!isEgg(stack)) return "unknown";
        return decode(stack).species();
    }

    /** The full competitive card for an egg (per-stat IVs/EVs · nature · gender · ability); null if not an egg. */
    public static EggCard card(ItemStack stack) {
        if (!isEgg(stack)) return null;
        return decode(stack).card();
    }

    private static Method tryMethod(Class<?> c, String name) {
        try { return c.getMethod(name); } catch (Throwable t) { return null; }
    }

    /** Invoke a no-arg getter and return a trimmed, namespace-stripped string ("" on any failure/null). */
    private static String readString(Method getter, Object props) {
        if (getter == null) return "";
        try {
            Object v = getter.invoke(props);
            if (v == null) return "";
            String s = v.toString().trim();
            int colon = s.indexOf(':');
            if (colon >= 0) s = s.substring(colon + 1);   // cobblemon:adamant -> adamant
            return s;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Map a Cobblemon Stat key to a slot in HP · Atk · Def · SpA · SpD · Spe order (−1 if unrecognized). */
    private static int statSlot(Object stat) {
        if (stat == null) return -1;
        String n = stat.toString().toLowerCase(java.util.Locale.ROOT);
        if (n.contains("hp") || n.contains("health")) return 0;
        if (n.contains("special") && n.contains("att")) return 3;
        if (n.contains("special") && n.contains("def")) return 4;
        if (n.contains("speed")) return 5;
        if (n.contains("att")) return 1;
        if (n.contains("def")) return 2;
        return -1;
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
