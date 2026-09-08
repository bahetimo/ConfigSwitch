package com.bteamore.configswitch.repo;

import java.nio.file.Path;

public class ConfigPathResolver {
    // Game Repo
    private final Path gameDir;

    public ConfigPathResolver(Path gameDir) {
        this.gameDir = gameDir;
    }

    public ConfigPaths resolve(String modId, String fileName, String timestamp, String operation) {
        if (modId == null) {
            return null;
        }

        String modDir = modId;
        String cfgDir = "config";
        switch (modId) {
            case "minecraft" -> {
                modDir = "";
                cfgDir = "";
            }
            case "uncategorized" -> {
                return null; // Uncategorized不处理
            }
        }
        Path active = gameDir.resolve(cfgDir).resolve(fileName);
        Path global = commonDir.resolve(modDir).resolve(fileName);
        Path global = RepoPaths.COMMON_DIR.resolve(modDir).resolve(fileName);
        Path backup;
        switch (operation) {
            case "fetch" -> backup = gameDir.resolve("config").resolve("switch").resolve("backup").resolve(timestamp).resolve(fileName);
            case "push" -> backup = backupDir.resolve(timestamp).resolve(modDir).resolve(fileName);
            default -> {
                return null;
            }
        }

        return new ConfigPaths(fileName, active, global, backup);
    }
}
