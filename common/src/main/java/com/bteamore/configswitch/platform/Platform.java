package com.bteamore.configswitch.platform;

import com.bteamore.configswitch.Constants;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public interface Platform {
    Path gameDir();
    Path configDir();
    List<ModEntry> mods();
    boolean isDevelopmentEnv();

    default Set<String> modIds() {
        return mods().stream()
            .map(ModEntry::id)
            .collect(Collectors.toUnmodifiableSet());
    }


    default Optional<String> versionOf(String modId) {
        return mods().stream()
                .filter(mod -> mod.id().equals(modId))
                .map(ModEntry::version)
                .findFirst();
    }

    default String selfVersion() {
        return versionOf(Constants.MOD_ID).orElse("unknown");
    }
}
