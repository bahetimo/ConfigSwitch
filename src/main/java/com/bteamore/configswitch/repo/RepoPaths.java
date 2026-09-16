package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.config.ModConfig;
import com.bteamore.configswitch.config.ModSettings;

import java.nio.file.Path;

public final class RepoPaths {
    public static Path root() {
        return ModSettings.DEFAULT_ROOT;
    }

    public static Path repoRoot() {
        return Path.of(ModConfig.settings().repoRoot());
    }

    public static Path globalDir() {
        return repoRoot().resolve("global");
    }
    public static Path commonDir() {
        return globalDir().resolve("common");
    }
    public static Path backupDir() {
        return repoRoot().resolve("backup");
    }

    public static Path defaultConfigPath() {
        return root().resolve("configswitch-config.json");
    }

    public static Path localBackupDir(Path gameDir) {
        return gameDir.resolve("config").resolve("switch").resolve("backup");
    }
}
