package com.bteamore.configswitch.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 文件按规则归组：
 * 1. options.txt -> 原版组
 * 2. 文件名等于某 modid（大小写不敏感）-> 归该 mod
 * 3. 否则 -> 未分类组
 */
public class ConfigClassifier {

    public List<ModGroup> classify(Path optionsFile, List<Path> configFiles, Set<String> modIds) {
        List<ModGroup> groups = new ArrayList<>();

        // 原版组（options.txt 存在才加）
        if (optionsFile != null && Files.exists(optionsFile)) {
            ModGroup vanilla = new ModGroup(ModGroup.VANILLA_ID);
            vanilla.addFile(optionsFile);
            groups.add(vanilla);
        }

        ModGroup uncategorized = null;
        for (Path file : configFiles) {
            String modId = matchModId(file, modIds);
            if (modId == null) {
                if (uncategorized == null) {
                    uncategorized = new ModGroup(ModGroup.UNCATEGORIZED_ID);
                    groups.add(uncategorized);
                }
                uncategorized.addFile(file);
            } else {
                findOrCreate(groups, modId).addFile(file);
            }
        }

        return groups;
    }

    private String matchModId(Path file, Set<String> modIds) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = (dot < 0) ? name : name.substring(0, dot);
        for (String modId : modIds) {
            if (modId.equalsIgnoreCase(stem)) {
                return modId;
            }
        }
        return null;
    }

    private ModGroup findOrCreate(List<ModGroup> groups, String modId) {
        for (ModGroup group : groups) {
            if (group.getModId().equals(modId)) {
                return group;
            }
        }
        ModGroup group = new ModGroup(modId);
        groups.add(group);
        return group;
    }
}
