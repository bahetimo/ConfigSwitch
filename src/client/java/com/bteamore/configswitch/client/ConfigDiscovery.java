package com.bteamore.configswitch.client;

import com.bteamore.configswitch.discovery.ConfigClassifier;
import com.bteamore.configswitch.discovery.ConfigScanner;
import com.bteamore.configswitch.discovery.GlobalConfigScanner;
import com.bteamore.configswitch.discovery.ModGroup;
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

    // global path 暂时不知道可以放在哪个通用的文件中，故先放这
    private static final Path root = Path.of(System.getProperty("user.home"),"AppData", "Roaming", ".minecraft", "configswitch");
    private static final Path globalDir = Path.of(root.toString(), "global");
    private static final Path commonDir = Path.of(globalDir.toString(), "common");

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
        List<ModGroup> groups = gScanner.scan(commonDir);

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
