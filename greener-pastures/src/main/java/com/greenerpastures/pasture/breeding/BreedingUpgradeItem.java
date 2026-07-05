package com.greenerpastures.pasture.breeding;

import com.greenerpastures.economy.AugmentFunction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * A "Pasture Upgrade" item (copper→greener) - slotted into a pasture via the wand GUI's slot 0. It
 * scales the pasture's breeding pairs and unlocks functional-upgrade slots (see {@link BreedingTier}).
 * It also carries the player's compiled {@link Augments} as the {@code greenerpastures:augments} data
 * component (this is the "Kernel" in the locked lexicon). No right-click behaviour: installation is
 * handled entirely through the Pasture Wand GUI.
 */
public class BreedingUpgradeItem extends Item {
    /** Client hook: set by GreenerPasturesClient to open the rename screen (common code can't touch client
     *  classes). Null on a dedicated server → right-click is a no-op there (rename is client-initiated). */
    public static java.util.function.Consumer<ItemStack> renameScreenOpener = null;

    @Override
    public net.minecraft.util.TypedActionResult<ItemStack> use(net.minecraft.world.World world,
            net.minecraft.entity.player.PlayerEntity user, net.minecraft.util.Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != net.minecraft.util.Hand.MAIN_HAND) return net.minecraft.util.TypedActionResult.pass(stack);
        if (world.isClient() && renameScreenOpener != null) renameScreenOpener.accept(stack);
        return net.minecraft.util.TypedActionResult.success(stack, world.isClient());
    }

    /** The per-tier base drop-rate INCREMENT, in centipercent ({@code 50} = +0.50%/tier): a Kernel's base drop
     *  rate is {@code BASE_DROP_RATE × tier-level} (copper +0.50% … greener +3.00% - see
     *  {@link BreedingTier#baseDropRateCentipercent()}). Added to the Harvester's per-mon proc; a base augment,
     *  so a Drop Rate tether amplifies it. Set as the item's default {@code augments} component in
     *  {@code BetterPasture.registerItems}. Doubled from 25 (Deuce, 2026-07-03 - drop-rate QA pass).
     *  <b>NB:</b> a Kernel the Augmenter has touched carries an explicit component with the OLD value baked in -
     *  use a freshly crafted Kernel to see the new base. */
    public static final int BASE_DROP_RATE = 50;

    private final BreedingTier tier;

    public BreedingUpgradeItem(BreedingTier tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    public BreedingTier tier() {
        return tier;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.literal(tier.maxPairs + " pairs · " + tier.slots + " slots").formatted(Formatting.GRAY));
        if (Boolean.TRUE.equals(stack.get(GpComponents.CORRUPTED))) {
            Integer cp = stack.get(GpComponents.CORRUPT_PAIRS);
            tooltip.add(Text.literal("⛧ corrupted - beyond modification"
                    + (cp != null && cp > 0 ? " · +" + cp + " pair" : "")).formatted(Formatting.DARK_PURPLE));
        }
        Augments a = stack.get(GpComponents.AUGMENTS);
        if (a == null) return;
        int n = a.toLevels().size();
        if (n > 0) tooltip.add(Text.literal(n + " augment" + (n == 1 ? "" : "s") + " installed").formatted(Formatting.GRAY));
        for (AugmentFunction f : AugmentFunction.values()) {
            int lvl = a.level(f);
            if (lvl <= 0) continue;
            tooltip.add(switch (f) {
                case SHINY -> Text.literal("✦ +" + lvl + "% shiny proc").formatted(Formatting.AQUA);
                case SPEED -> Text.literal("⚡ speed " + (lvl == 1 ? "×1.5" : lvl == 2 ? "×2" : lvl == 3 ? "×3" : "lvl " + lvl))
                        .formatted(Formatting.YELLOW);
                case IV_FLOOR -> Text.literal("▲ IV floor " + lvl).formatted(Formatting.AQUA);
                case EV -> {
                    EvSpread ev = stack.get(GpComponents.EV_SPREAD);
                    yield Text.literal("EV " + (ev != null && !ev.isEmpty()
                            ? ev.hp() + "/" + ev.atk() + "/" + ev.def() + "/" + ev.spa() + "/" + ev.spd() + "/" + ev.spe()
                            : "primer")).formatted(Formatting.AQUA);
                }
                case ENRICHMENT -> Text.literal("◈ enrichment +" + lvl).formatted(Formatting.GREEN);
                case DROP_RATE -> Text.literal("⛏ +" + String.format("%.2f", lvl / 100.0) + "% drop rate").formatted(Formatting.GREEN);
                case DROP_YIELD -> Text.literal("⛏ +" + lvl + " drop yield").formatted(Formatting.GREEN);
                case NATURE -> Text.literal("🧬 " + pretty(NatureCatalog.byIndex(lvl), "nature " + lvl)).formatted(Formatting.LIGHT_PURPLE);
                case BALL -> Text.literal("◉ " + pretty(stripNs(BallCatalog.byIndex(lvl)), "ball " + lvl)).formatted(Formatting.LIGHT_PURPLE);
                case ABILITY -> Text.literal("✦ hidden ability").formatted(Formatting.LIGHT_PURPLE);
                case EGG_MOVE -> Text.literal("📖 egg moves").formatted(Formatting.LIGHT_PURPLE);
                case HATCH -> Text.literal("🐣 hatch ×" + HatchHaste.factorLabel(lvl)).formatted(Formatting.YELLOW);
            });
        }
    }

    /** "master_ball" → "Master Ball"; falls back to {@code alt} when the catalog index doesn't resolve. */
    private static String pretty(String id, String alt) {
        if (id == null || id.isEmpty()) return alt;
        StringBuilder sb = new StringBuilder(id.length());
        boolean up = true;
        for (char c : id.toCharArray()) {
            if (c == '_') { sb.append(' '); up = true; }
            else { sb.append(up ? Character.toUpperCase(c) : c); up = false; }
        }
        return sb.toString();
    }

    private static String stripNs(String id) {
        if (id == null) return null;
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }
}
