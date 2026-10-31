package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public class RepoMetaStore {
    public static final String META_FILE = "meta.json";
    public static final String GLOBAL_ENTRY = "global/" + META_FILE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FIELD_FORMAT = "format";
    private static final String FIELD_MODS = "mods";
    private static final String FIELD_MINECRAFT = "minecraft";
    private static final String FIELD_LOADER = "loader";
    private static final String FIELD_AT = "at";

    private final Path metaPath;

    public RepoMetaStore(Path metaPath) {
        this.metaPath = metaPath;
    }

    public static RepoMetaStore global() {
        return new RepoMetaStore(RepoPaths.globalDir().resolve(META_FILE));
    }

    public RepoMeta load() {
        String text;
        try {
            if (Files.notExists(metaPath)) {
                Log.warn("Repo meta {} is missing, treated as empty", metaPath);
                return RepoMeta.empty();
            }
            text = Files.readString(metaPath);
        } catch (IOException | RuntimeException e) {
            Log.warn("Failed to read repo meta {}: {}", metaPath, e);
            return RepoMeta.empty();
        }
        return parse(text);
    }

    public void save(RepoMeta meta) {
        rewrite(toJson(meta == null ? RepoMeta.empty() : meta));
    }

    public void update(Consumer<RepoMeta> mutator) {
        RepoMeta meta = load();
        if (mutator != null) {
            mutator.accept(meta);
        }
        save(meta);
    }

    public void delete() {
        try {
            Files.deleteIfExists(metaPath);
        } catch (IOException e) {
            Log.warn("Failed to delete repo meta {}", metaPath, e);
        }
    }

    private RepoMeta parse(String text) {
        if (text.isBlank()) {
            Log.warn("Repo meta {} is empty, treated as empty", metaPath);
            return RepoMeta.empty();
        }

        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            Log.warn("Repo meta {} is not valid JSON: {}", metaPath, e);
            return RepoMeta.empty();
        }
        if (root == null || !root.isJsonObject()) {
            Log.warn("Repo meta {} is not a JSON object, treated as empty", metaPath);
            return RepoMeta.empty();
        }

        try {
            return readRoot(root.getAsJsonObject());
        } catch (RuntimeException e) {
            Log.warn("Repo meta {} has unrecognized fields: {}", metaPath, e);
            return RepoMeta.empty();
        }
    }

    private RepoMeta readRoot(JsonObject root) {
        int format = requireInt(root, FIELD_FORMAT);
        if (format != RepoMeta.FORMAT) {
            throw new IllegalArgumentException("unsupported " + FIELD_FORMAT + ": " + format);
        }
        JsonElement mods = root.get(FIELD_MODS);
        if (mods == null || !mods.isJsonObject()) {
            throw new IllegalArgumentException(FIELD_MODS + " is missing or not an object");
        }

        Map<String, ModSource> table = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : mods.getAsJsonObject().entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonObject()) {
                throw new IllegalArgumentException(FIELD_MODS + "." + entry.getKey() + " is not an object");
            }
            JsonObject record = value.getAsJsonObject();
            table.put(entry.getKey(), new ModSource(
                    optionalText(record, FIELD_MINECRAFT),
                    optionalText(record, FIELD_LOADER),
                    optionalText(record, FIELD_AT)));
        }
        return new RepoMeta(format, table);
    }

    private static int requireInt(JsonObject obj, String field) {
        JsonElement value = obj.get(field);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException(field + " is missing");
        }
        return value.getAsInt();
    }

    // 条目内缺字段只落成空串：结构仍认得，不该牵连整份文件
    private static String optionalText(JsonObject obj, String field) {
        JsonElement value = obj.get(field);
        return (value == null || value.isJsonNull()) ? "" : value.getAsString();
    }

    private String toJson(RepoMeta meta) {
        JsonObject mods = new JsonObject();
        // modId 排序后落盘，同一份表写出同一文本，便于比对与diff
        new TreeMap<>(meta.mods()).forEach((modId, record) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty(FIELD_MINECRAFT, record.minecraft());
            entry.addProperty(FIELD_LOADER, record.loader());
            entry.addProperty(FIELD_AT, record.at());
            mods.add(modId, entry);
        });

        JsonObject root = new JsonObject();
        root.addProperty(FIELD_FORMAT, meta.format());
        root.add(FIELD_MODS, mods);
        return GSON.toJson(root);
    }

    private void rewrite(String text) {
        Path parent = metaPath.getParent();
        if (parent == null) {
            Log.error("Repo meta path has no parent directory: {}", metaPath);
            return;
        }

        Path temp = null;
        try {
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, META_FILE, ".tmp");
            Files.writeString(temp, text);
            Files.move(temp, metaPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temp = null;
        } catch (IOException e) {
            Log.error("Failed to write repo meta {}: {}", metaPath, e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    Log.warn("Failed to delete temp repo meta file {}", temp, e);
                }
            }
        }
    }
}
