package com.bteamore.configswitch.repo;

import java.nio.file.Path;

public final class RepoPaths {
    public static final Path ROOT = Path.of(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft", "configswitch");
    public static final Path GLOBAL_DIR = ROOT.resolve("global");
    public static final Path COMMON_DIR = GLOBAL_DIR.resolve("common");
    public static final Path BACKUP_DIR = ROOT.resolve("backup");

    private RepoPaths() {
    }

    public static Path localBackupDir(Path gameDir) {
        return gameDir.resolve("config").resolve("switch").resolve("backup");
    }
}
