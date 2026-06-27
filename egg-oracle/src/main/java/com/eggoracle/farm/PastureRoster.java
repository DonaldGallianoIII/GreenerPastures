package com.eggoracle.farm;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's pasture roster: which species are being bred and how many pastures of each.
 * Persisted as plain text (species,count per line) to config/eggoracle_farm.txt so it
 * survives sessions and can be hand-edited. No external deps.
 */
public class PastureRoster {
    public static final class Entry {
        public String species;
        public int count;
        public Entry(String s, int c) { species = s; count = c; }
    }

    public final List<Entry> entries = new ArrayList<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("eggoracle_farm.txt");
    }

    public int total() {
        int t = 0;
        for (Entry e : entries) t += Math.max(0, e.count);
        return t;
    }

    /** Add a species (merging into an existing row, case-insensitive) or adjust its count. */
    public void add(String species, int count) {
        if (species == null) return;
        species = species.trim();
        if (species.isEmpty() || count == 0) return;
        for (Entry e : entries) {
            if (e.species.equalsIgnoreCase(species)) {
                e.count = Math.max(0, e.count + count);
                save();
                return;
            }
        }
        entries.add(new Entry(species, Math.max(0, count)));
        save();
    }

    public void remove(int idx) {
        if (idx >= 0 && idx < entries.size()) {
            entries.remove(idx);
            save();
        }
    }

    public void load() {
        entries.clear();
        Path f = file();
        try {
            if (!Files.exists(f)) {
                seedDefaults();
                save();
                return;
            }
            for (String line : Files.readAllLines(f)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int comma = line.lastIndexOf(',');
                if (comma <= 0) continue;
                String sp = line.substring(0, comma).trim();
                try {
                    int c = Integer.parseInt(line.substring(comma + 1).trim());
                    if (!sp.isEmpty()) entries.add(new Entry(sp, Math.max(0, c)));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            if (entries.isEmpty()) seedDefaults();
        }
    }

    public void save() {
        try {
            Files.createDirectories(file().getParent());
            StringBuilder sb = new StringBuilder("# EggOracle pasture roster  (species,count)\n");
            for (Entry e : entries) sb.append(e.species).append(',').append(e.count).append('\n');
            Files.writeString(file(), sb.toString());
        } catch (IOException ignored) {}
    }

    /** First-run seed = the known active roster. */
    private void seedDefaults() {
        entries.clear();
        String[][] seed = {
            {"Eevee", "60"}, {"Charmander", "5"}, {"Tepig", "4"}, {"Absol", "3"}, {"Ponyta", "3"},
            {"Vulpix", "3"}, {"Litwick", "2"}, {"Scorbunny", "2"}, {"Ralts", "2"}, {"Magikarp", "2"},
            {"Swablu", "1"}, {"Cyndaquil", "1"}, {"Minccino", "1"}, {"Duraludon", "1"}, {"Patrat", "1"},
            {"Dreepy", "1"}, {"Froakie", "1"}
        };
        for (String[] s : seed) entries.add(new Entry(s[0], Integer.parseInt(s[1])));
    }
}
