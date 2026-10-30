package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.util.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        loaded = this.newStem(loaded);
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

    // 读入归一化：键统一收成 stem
    private Map<String, String> newStem(Map<String, String> loaded) {
        Map<String, String> stems = new LinkedHashMap<>();
        Map<String, String> legacy = new LinkedHashMap<>();
        for (var entry : loaded.entrySet()) {
            String stem = FileNames.stemOf(entry.getKey());
            if (stem.equals(entry.getKey())) {
                stems.put(entry.getKey(), entry.getValue());
            } else {
                legacy.put(stem, entry.getValue());
            }
        }
        if (legacy.isEmpty()) {
            return stems;
        }

        // 两种写法并存时以已归一化的键为准
        legacy.forEach(stems::putIfAbsent);
        this.rewrite(stems);
        return stems;
    }

    private void rewrite(Map<String, String> loaded) {
        Path parent = mappingPath.getParent();
        Path temp = null;
        try {
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, "mappings", ".tmp");
            StringBuilder text = new StringBuilder();
            loaded.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> text.append(e.getKey()).append('=').append(e.getValue()).append('\n'));
            Files.writeString(temp, text.toString());
            Files.move(temp, mappingPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
        } catch (IOException e) {
            Log.error("Failed to rewrite mappings to {}", mappingPath, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    Log.warn("Failed to delete temp mappings file {}", temp, e);
                }
            }
        }
    }

    public boolean append(Map<String, String> mappings) {
        try {
            boolean lfFirst = false;

            Files.createDirectories(mappingPath.getParent());
            // 先读非空且最后一个字节不是\n
            if (Files.exists(mappingPath) && Files.size(mappingPath) > 0) {
                try (var reader = new RandomAccessFile(mappingPath.toFile(), "r")) {
                    reader.seek(reader.length() - 1);
                    lfFirst = reader.readByte() != '\n';
                }
            }

            try (var writer = Files.newBufferedWriter(mappingPath, StandardOpenOption.APPEND, StandardOpenOption.CREATE)){

                if (lfFirst) {
                    writer.write('\n');
                }
                for (Map.Entry<String, String> entry : mappings.entrySet()) {
                    writer.write(FileNames.stemOf(entry.getKey()) + "=" + entry.getValue() + "\n");
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

        try (var reader = Files.newBufferedReader(mappingPath)) {
            return MappingTable.load(reader);
        } catch (IOException e) {
            Log.error("Failed to load mappings from {}", mappingPath, e);
            return MappingTable.empty();
        }
    }
}
