package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.discovery.ModGroup;

import java.nio.file.Path;

public record LocalLayout(Path gameDir, Path configDir) {
    public Path activeFor(String modId, String relativePath) {
        return ModGroup.VANILLA_ID.equals(modId)
            ? gameDir.resolve(relativePath)
            : configDir.resolve(relativePath);
    }

    public Path localBackupRoot() {
        return gameDir.resolve("config").resolve("switch").resolve("backup");
    }

    public Path localRestoreTarget(Path relative) {
        return relative.getNameCount() > 1 && relative.startsWith(Path.of("config"))
            ? configDir.resolve(relative.subpath(1, relative.getNameCount()))
            : gameDir.resolve(relative);
    }

    public String relativeOf(Path file) {
        if (file.startsWith(configDir)) {
            return configDir.relativize(file).toString().replace("\\", "/");
        }
        return file.getFileName().toString();
    }
}
