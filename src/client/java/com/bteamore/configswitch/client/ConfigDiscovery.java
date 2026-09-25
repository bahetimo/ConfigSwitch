package com.bteamore.configswitch.client;

import com.bteamore.configswitch.discovery.*;
import com.bteamore.configswitch.repo.RepoPaths;
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
    private ConfigClassifier classifier;

    public ConfigDiscovery(Path dir) {
        gameDir = dir;
        configDir = gameDir.resolve("config");
        optionsFile = gameDir.resolve("options.txt");
    }

    private Set<String> cachedModIds;

    public List<ModGroup> discoverLocal() {
        List<Path> configFiles = scanner.scan(configDir);

        List<ModGroup> classified = classifier().classify(optionsFile, configFiles);
        Log.debug("discovered {} files -> {} groups({} files uncategorized)", configFiles.size(), classified.size(), classified.stream().filter(g -> g.getModId().equals(ModGroup.UNCATEGORIZED_ID)).map(ModGroup::getFiles).mapToLong(List::size).sum());
        for (ModGroup g : classified) {
            Log.debug("{}={}", g.getModId(), g.getFiles().stream().map(Path::getFileName).toList());
        }

        // 原版置顶，未分类置底，其余按 modId 字母序
        return classified.stream()
                .sorted(Comparator.comparing((ModGroup g) -> !g.getModId().equals(ModGroup.VANILLA_ID))
                        .thenComparing((ModGroup g) -> g.getModId().equals(ModGroup.UNCATEGORIZED_ID))
                        .thenComparing(ModGroup::getModId))
                .collect(Collectors.toList());
    }

    private ConfigClassifier classifier() {
        if (classifier == null) {
            classifier = new ConfigClassifier(getModIds(), MappingTable.loadBundled(), new RepoModScanner().scan(RepoPaths.commonDir()));
        }
        return classifier;
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
