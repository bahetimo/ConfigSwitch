package com.bteamore.configswitch.client;

import com.bteamore.configswitch.discovery.ConfigClassifier;
import com.bteamore.configswitch.discovery.ConfigScanner;
import com.bteamore.configswitch.discovery.ModGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigDiscovery {
    private static final Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();
    private static final Path configDir = gameDir.resolve("config");
    private static final Path optionsFile = gameDir.resolve("options.txt");

    private final ConfigScanner scanner = new ConfigScanner();
    private final ConfigClassifier classifier = new ConfigClassifier();

    private Set<String> cachedModIds;

    public List<ModGroup> discover() {
        List<Path> configFiles = scanner.scan(configDir);

        return classifier.classify(optionsFile, configFiles, getModIds());
    }

    public Set<String> getModIds() {
        if (cachedModIds == null){
            cachedModIds = FabricLoader.getInstance().getAllMods().stream()
                    .map(mod -> mod.getMetadata().getId())
                    .collect(Collectors.toSet());
        }
        return cachedModIds;
    }
}
