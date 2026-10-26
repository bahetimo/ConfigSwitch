package com.bteamore.configswitch.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

public final class FabricPlatform implements Platform {

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public List<ModEntry> mods() {
        return FabricLoader.getInstance().getAllMods().stream()
            .map(mod -> new ModEntry(
                mod.getMetadata().getId(),
                mod.getMetadata().getName(),
                mod.getMetadata().getVersion().getFriendlyString()))
            .toList();
    }

    @Override
    public boolean isDevelopmentEnv() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
