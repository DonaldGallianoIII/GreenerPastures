package com.greenerpastures.biobank;

import com.greenerpastures.core.GpLog;
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

    /** Bank one egg under its species key. The cap is <b>per unique species</b> (Deuce, 2026-07-01): each species
     *  holds up to {@link BioBank#capacity()} eggs, so many species coexist. Enforced here as the single source
     *  of truth, so a migrated/hand-edited save can't load a species past its cap. Returns false when THAT
     *  species' bucket is full — the breeder then falls back to the tray for that egg. */
    public boolean add(String species, ItemStack egg) {
        List<ItemStack> bucket = bySpecies.computeIfAbsent(species, s -> new ArrayList<>());
        if (bucket.size() >= BioBank.capacity()) return false;
        bucket.add(egg);
        total++;
        return true;
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

    /** The eggs banked under one species key (empty if none) — for the console's per-species drill-in. */
    public List<ItemStack> entries(String species) {
        return bySpecies.getOrDefault(species, List.of());
    }

    /** Remove + return the egg at {@code flat} in species-grouped order (matches the console's flattened list);
     *  EMPTY if out of range. Empties the species bucket when its last egg leaves. */
    public ItemStack removeAt(int flat) {
        if (flat < 0) return ItemStack.EMPTY;
        int i = 0;
        var it = bySpecies.entrySet().iterator();
        while (it.hasNext()) {
            List<ItemStack> eggs = it.next().getValue();
            if (flat < i + eggs.size()) {
                ItemStack removed = eggs.remove(flat - i);
                total--;
                if (eggs.isEmpty()) it.remove();
                return removed;
            }
            i += eggs.size();
        }
        return ItemStack.EMPTY;
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
        int dropped = 0;
        for (String species : sp.getKeys()) {
            NbtList list = sp.getList(species, NbtElement.COMPOUND_TYPE);
            for (NbtElement el : list) {
                ItemStack st = ItemStack.fromNbt(lookup, el).orElse(null);
                if (st == null) continue;
                if (!d.add(species, st)) dropped++;   // over-cap (migrated/edited save) — clamp, don't load past it
            }
        }
        if (dropped > 0) GpLog.w("biobank", "load_over_cap", "dropped", dropped, "cap", BioBank.capacity());
        return d;
    }
}
