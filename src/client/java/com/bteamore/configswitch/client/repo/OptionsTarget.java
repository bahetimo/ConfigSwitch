package com.bteamore.configswitch.client.repo;

import com.bteamore.configswitch.repo.IConfigTarget;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;

public class OptionsTarget implements IConfigTarget {
    public static final Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();

    @Override
    public Path activeFile() {
        return gameDir.resolve("options.txt");
    }

    @Override
    public Path globalFile() {
        return commonDir.resolve("options.txt");
    }

    @Override
    public Path backupFile() {
        return gameDir.resolve("configswitch/backup/options.txt");
    }
}
