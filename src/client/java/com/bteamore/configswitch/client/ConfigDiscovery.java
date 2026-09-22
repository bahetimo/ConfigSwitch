package com.bteamore.configswitch.client;

import com.bteamore.configswitch.discovery.ConfigClassifier;
import com.bteamore.configswitch.discovery.ConfigScanner;
import com.bteamore.configswitch.discovery.ModGroup;
import com.bteamore.configswitch.util.Log;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Comparator;
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

    public List<ModGroup> discoverLocal() {
        List<Path> configFiles = scanner.scan(configDir);

        List<ModGroup> classified = classifier.classify(optionsFile, configFiles, getModIds());
        Log.debug("discovered {} files -> {} groups({} files uncategorized)", configFiles.size(), classified.size(), classified.stream().filter(g -> g.getModId().equals(ModGroup.UNCATEGORIZED_ID)).map(ModGroup::getFiles).mapToLong(List::size).sum());

        // 原版置顶，未分类置底，其余按 modId 字母序
        return classified.stream()
                .sorted(Comparator.comparing((ModGroup g) -> !g.getModId().equals(ModGroup.VANILLA_ID))
                        .thenComparing((ModGroup g) -> g.getModId().equals(ModGroup.UNCATEGORIZED_ID))
                        .thenComparing(ModGroup::getModId))
                .collect(Collectors.toList());
    }

    public Set<String> getModIds() {
        if (cachedModIds == null) {
            cachedModIds = FabricLoader.getInstance().getAllMods().stream()
                    .map(mod -> mod.getMetadata().getId())
                    .collect(Collectors.toSet());
        }
        return cachedModIds;
    }
}
