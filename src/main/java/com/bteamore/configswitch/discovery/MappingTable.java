package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.util.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MappingTable {
    private final Map<String, String> entries = new HashMap<>();
    public static final String IGNORE = "ignore";

    private MappingTable(Map<String, String> entries) {
        this.entries.putAll(entries);
    }

    public int size() {
        return this.entries.size();
    }

    public Optional<String> lookup(String stem) {
        return this.entries.keySet().stream()
                .filter(e -> StemMatches.matches(e, stem))
                .max(Comparator.comparingInt(String::length))
                .map(this.entries::get);
    }

    public static MappingTable load(Reader reader) throws IOException {
        Map<String, String> tmp = new HashMap<>();
        BufferedReader bufferedReader = new BufferedReader(reader);
        try {
            bufferedReader.lines().map(String::trim)
                    .filter(l -> !l.isEmpty())
                    .filter(l -> !l.startsWith("#"))
                    .map(l -> l.contains("#") ? l.substring(0, l.indexOf("#")) : l)
                    .forEach(l -> parseSeed(l, tmp));
        } catch (UncheckedIOException e){
            throw e.getCause();
        }
        return new MappingTable(tmp);
    }

    public static MappingTable loadBundled() {
        try (InputStream inputStream = MappingTable.class.getResourceAsStream("/assets/configswitch/mapping/seed.txt")) {
            if (inputStream == null) {
                Log.warn("seed mapping not found");
                return empty();
            }

            return load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        } catch (UncheckedIOException | IOException e) {
            Log.warn("seed mapping load failed", e);
            return empty();
        }
    }

    private static MappingTable empty() {
        return new MappingTable(new HashMap<>());
    }

    private static void parseSeed(String seed, Map<String, String> tmp) {
        if (seed.contains("=")) {
            String[] parts = seed.split("=", 2);
            String key = parts[0].trim().toLowerCase();
            String value = parts[1].trim().toLowerCase();
            if (key.isEmpty() || value.isEmpty()) {
                Log.debug("ignoring wrong seed entry: {}", seed);
                return;
            }
            tmp.put(key, value);
        } else {
            tmp.put(seed.trim().toLowerCase(), seed.trim().toLowerCase());
        }
    }
}
