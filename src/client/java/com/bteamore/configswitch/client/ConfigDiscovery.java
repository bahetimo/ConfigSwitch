package com.bteamore.configswitch.client;

import com.bteamore.configswitch.discovery.ConfigClassifier;
import com.bteamore.configswitch.discovery.ConfigScanner;
import com.bteamore.configswitch.discovery.GlobalConfigScanner;
import com.bteamore.configswitch.discovery.ModGroup;
import com.bteamore.configswitch.repo.RepoPaths;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigDiscovery {
    private final Path gameDir;
    private final Path configDir;
    private final Path optionsFile;

    private final ConfigScanner scanner = new ConfigScanner();
    private final ConfigClassifier classifier = new ConfigClassifier();
    private final GlobalConfigScanner gScanner = new GlobalConfigScanner();

    public ConfigDiscovery(Path dir) {
        gameDir = dir;
        configDir = gameDir.resolve("config");
        optionsFile = gameDir.resolve("options.txt");
    }

    private Set<String> cachedModIds;

    public List<ModGroup> discover(String name) {
        return switch (name) {
            case "fetch" -> discoverGlobal();
            case "push" -> discoverLocal();
            default -> new ArrayList<>();
        };
    }

    private List<ModGroup> discoverGlobal() {
        List<ModGroup> groups = gScanner.scan(RepoPaths.COMMON_DIR);

        return groups.stream()
                .filter(group -> (getModIds().contains(group.getModId())) || (group.getModId().equals(ModGroup.VANILLA_ID)))
                .toList();
    }

    private List<ModGroup> discoverLocal() {
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
