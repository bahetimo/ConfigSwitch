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
        Path global = RepoPaths.commonDir().resolve(modDir).resolve(fileName);
        Path backupRoot;
        Path backup;
        switch (operation) {
            case "fetch" -> {
                backupRoot = RepoPaths.localBackupDir(gameDir);
                backup = backupRoot.resolve(timestamp).resolve(cfgDir).resolve(fileName);
            }
            case "push" -> {
                backupRoot = RepoPaths.backupDir();
                backup = backupRoot.resolve(timestamp).resolve(modDir).resolve(fileName);
            }
            default -> {
                return null;
            }
        }

        return new ConfigPaths(fileName, active, global, backup, backupRoot, modId);
    }
}
