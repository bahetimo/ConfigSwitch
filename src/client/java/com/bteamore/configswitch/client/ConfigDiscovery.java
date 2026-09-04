package com.bteamore.configswitch.client;

import com.bteamore.configswitch.discovery.ConfigClassifier;
import com.bteamore.configswitch.discovery.ConfigScanner;
import com.bteamore.configswitch.discovery.ModGroup;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigDiscovery {
    private final Path gameDir;
    private final Path configDir;
    private final Path optionsFile;

    private final ConfigScanner scanner = new ConfigScanner();
    private final ConfigClassifier classifier = new ConfigClassifier();

    public ConfigDiscovery(Path dir) {
        gameDir = dir;
        configDir = gameDir.resolve("config");
        optionsFile = gameDir.resolve("options.txt");
    }

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
