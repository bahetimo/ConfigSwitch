package com.bteamore.configswitch.repo;

import java.nio.file.Path;

public class ConfigPathResolver {
    // Game Repo
    private final LocalLayout localLayout;


    public ConfigPathResolver(LocalLayout localLayout) {
        this.localLayout = localLayout;
    }

    public ConfigPaths resolve(String modId, String relativePath, String timestamp, String operation) {
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
            // case "uncategorized" -> {
            //     return null; // Uncategorized不处理
            // }
        }
        Path active = localLayout.activeFor(modId, relativePath);
        Path global = RepoPaths.commonDir().resolve(modDir).resolve(relativePath);
        Path backupRoot;
        Path backup;
        switch (operation) {
            case "fetch" -> {
                backupRoot = this.localLayout.localBackupRoot();
                backup = backupRoot.resolve(timestamp).resolve(cfgDir).resolve(relativePath);
            }
            case "push" -> {
                backupRoot = RepoPaths.backupDir();
                backup = backupRoot.resolve(timestamp).resolve(modDir).resolve(relativePath);
            }
            default -> {
                return null;
            }
        }

        return new ConfigPaths(relativePath, active, global, backup, backupRoot, modId);
    }
}
