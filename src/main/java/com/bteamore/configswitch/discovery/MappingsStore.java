package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public class MappingsStore {
    private final Path mappingPath;

    public MappingsStore(Path mappingPath) {
        this.mappingPath = mappingPath;
    }


    public MappingTable injectSeed(Map<String, String> seed) {
        if (seed == null || seed.isEmpty()){
            return this.load();
        }

        Map<String, String> loaded = this.load().entries();
        Map<String, String> toAdd = new LinkedHashMap<>();
        for (var e : seed.entrySet()) {
            if (!loaded.containsKey(e.getKey())) {
                toAdd.put(e.getKey(), e.getValue());
            }
        }
        if (!toAdd.isEmpty()){
            this.append(toAdd);
        }
        return this.load();
    }

    public boolean append(Map<String, String> mappings) {
        try {
            Files.createDirectories(mappingPath.getParent());
            try (var writer = Files.newBufferedWriter(mappingPath, StandardOpenOption.APPEND, StandardOpenOption.CREATE)){
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    writer.write("\n" + entry.getKey() + "=" + entry.getValue());
                }
            }
        } catch (IOException e) {
            Log.error("Failed to append mappings to {}", mappingPath, e);
            return false;
        }
        return true;
    }

    public MappingTable load() {
        if (Files.notExists(mappingPath)) {
            return MappingTable.empty();
        }

        try {
            return MappingTable.load(Files.newBufferedReader(mappingPath));
        } catch (IOException e) {
            Log.error("Failed to load mappings from {}", mappingPath, e);
            return MappingTable.empty();
        }
    }
}
