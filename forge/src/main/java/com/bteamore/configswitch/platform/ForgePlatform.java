package com.bteamore.configswitch.platform;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.nio.file.Path;
import java.util.List;

public final class ForgePlatform implements Platform {
    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public List<ModEntry> mods() {
        return ModList.get().getMods().stream()
            .map(ForgePlatform::toEntry)
            .toList();
    }

    private static ModEntry toEntry(IModInfo info) {
        return new ModEntry(info.getModId(), info.getDisplayName(), info.getVersion().toString());
    }

    @Override
    public boolean isDevelopmentEnv() {
        return !FMLLoader.isProduction();
    }
}
