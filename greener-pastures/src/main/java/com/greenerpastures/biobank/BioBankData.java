package com.greenerpastures.biobank;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One BioBank's contents: eggs (lossless ItemStacks) bucketed by species key. Lives in
 * {@link BioBankStore} (a per-world save), NOT in block-entity chunk NBT — so it scales to
 * thousands without a chunk-save / reload spike (the principle from DAEMON_AND_TETHERS.md).
 */
public final class BioBankData {
    private final Map<String, List<ItemStack>> bySpecies = new LinkedHashMap<>();
    private int total = 0;

    public int total() { return total; }

    public void add(String species, ItemStack egg) {
        bySpecies.computeIfAbsent(species, s -> new ArrayList<>()).add(egg);
        total++;
    }

    /** species -> count, in first-seen order. */
    public Map<String, Integer> speciesCounts() {
        Map<String, Integer> out = new LinkedHashMap<>();
        bySpecies.forEach((s, l) -> out.put(s, l.size()));
        return out;
    }

    /** Every stored egg, materialized (for scatter-on-break). */
    public List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>(total);
        bySpecies.values().forEach(out::addAll);
        return out;
    }

    // --- persistence (egg ItemStacks are stored losslessly, one NbtList per species) ---

    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound sp = new NbtCompound();
        bySpecies.forEach((species, eggs) -> {
            NbtList list = new NbtList();
            for (ItemStack egg : eggs) if (!egg.isEmpty()) list.add(egg.encode(lookup));
            sp.put(species, list);
        });
        nbt.put("species", sp);
        return nbt;
    }

    public static BioBankData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        BioBankData d = new BioBankData();
        NbtCompound sp = nbt.getCompound("species");
        for (String species : sp.getKeys()) {
            NbtList list = sp.getList(species, NbtElement.COMPOUND_TYPE);
            for (NbtElement el : list) {
                ItemStack.fromNbt(lookup, el).ifPresent(st -> d.add(species, st));
            }
        }
        return d;
    }
}
