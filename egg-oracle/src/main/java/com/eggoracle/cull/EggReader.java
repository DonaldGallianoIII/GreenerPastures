package com.eggoracle.cull;

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

    /** Read what we can off an egg. Returns null if it isn't an egg. Never throws. */
    public static EggInfo read(ItemStack stack) {
        if (!isEgg(stack)) return null;
        init();
        boolean shiny = false, ivsKnown = false;
        int ivTotal = 0, perfect = 0;

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
                }
            } catch (Throwable t) {
                // fall through to the name-based shiny check below
            }
        }
        // Shiny ALWAYS wins: a ★ in the name flags it even when the API's shiny flag is missing,
        // so a shiny is never mistinted as keeper/cull. Cached per stack, so this runs ~once per egg.
        if (!shiny) shiny = shinyByName(stack);
        return new EggInfo(shiny, ivsKnown, ivTotal, perfect);
    }

    private static boolean shinyByName(ItemStack stack) {
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
