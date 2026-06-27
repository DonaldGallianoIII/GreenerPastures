package com.greenerpastures.pasture.breeding.compiler;

import com.greenerpastures.pasture.breeding.Augments;
import com.greenerpastures.pasture.breeding.BreedingUpgradeItem;
import com.greenerpastures.pasture.breeding.GpComponents;
import net.minecraft.item.ItemStack;

/**
 * The augment "packages" a {@link CompilerBlock} can install onto a Kernel (a Pasture Upgrade item).
 * v1 ships {@link #SHINY} only — the bounded shiny proc (repo ground truth: additive/capped, NOT the
 * mockup's "×2"). Each type knows how to detect whether it's already installed and how to apply itself
 * by merging into the {@code greenerpastures:augments} data component. Adding an augment later = one
 * new constant + a branch in {@link #installedOn} and {@link #apply}.
 */
public enum AugmentType {
    /** Bounded shiny proc: each non-shiny egg gets a {@code value}% chance of one extra reroll. */
    SHINY("shiny-boost", "1.0", 30);

    public final String pkgName;
    public final String version;
    /** SHINY: the proc percent this augment writes onto the Kernel. */
    public final int value;

    AugmentType(String pkgName, String version, int value) {
        this.pkgName = pkgName;
        this.version = version;
        this.value = value;
    }

    /** pip-style package id, e.g. {@code shiny-boost==1.0}. */
    public String pkg() {
        return pkgName + "==" + version;
    }

    /** Only Kernels (Pasture Upgrade items) can receive augments. */
    public boolean appliesTo(ItemStack kernel) {
        return !kernel.isEmpty() && kernel.getItem() instanceof BreedingUpgradeItem;
    }

    /** True if this augment is already on the Kernel (so re-compiling is a no-op). */
    public boolean installedOn(ItemStack kernel) {
        Augments a = kernel.get(GpComponents.AUGMENTS);
        if (a == null) return false;
        return switch (this) {
            case SHINY -> a.shinyProcPercent() >= value;
        };
    }

    /** A copy of the Kernel with this augment merged into its augments component. */
    public ItemStack apply(ItemStack kernel) {
        ItemStack out = kernel.copy();
        Augments base = out.get(GpComponents.AUGMENTS);
        if (base == null) base = Augments.NONE;
        Augments merged = switch (this) {
            case SHINY -> base.withShiny(value);
        };
        out.set(GpComponents.AUGMENTS, merged);
        return out;
    }
}
