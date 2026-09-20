package com.bteamore.configswitch.config;

import com.bteamore.configswitch.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;

    public SettingsStore(Path configPath) {
        this.configPath = configPath;
    }

    public ModSettings load() {
        if (Files.notExists(configPath)) {
            return ModSettings.DEFAULT;
        }
        try(var reader = Files.newBufferedReader(configPath)){
            ModSettings loaded = GSON.fromJson(reader, ModSettings.class);
            return (loaded != null) ? loaded : ModSettings.DEFAULT;
        } catch (IOException | JsonParseException e) {
            Log.error("Failed to load settings from {}", configPath, e);
            return ModSettings.DEFAULT;
        }
    }

    public boolean save(ModSettings settings) {
        try{
            Files.createDirectories(configPath.getParent());
            try(var writer = Files.newBufferedWriter(configPath)){
                GSON.toJson(settings, writer);
            }
        } catch (IOException e) {
            Log.error("Failed to save settings to {}", configPath, e);
            return false;
        }
        return true;
    }
}
