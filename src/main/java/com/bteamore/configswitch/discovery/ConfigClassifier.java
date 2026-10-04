package com.bteamore.configswitch.discovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 文件按规则归组：
 * 1. options.txt -> 原版组
 * 2. 逐文件匹配：映射表 → L2（已安装）→ B（仓库）→ 收件箱
 * 3. 二趟：整组按安装状态分流，未安装的组 → 残留组（不渲染）
 */
public class ConfigClassifier {
    private final Set<String> installedModIds;
    private final MappingTable mapping;
    private final Set<String> repoModIds;

    public ConfigClassifier(Set<String> installedModIds, MappingTable mapping, Set<String> repoModIds){
        this.installedModIds = installedModIds;
        this.mapping = mapping;
        this.repoModIds = repoModIds;
    }

    public List<ModGroup> classify(Path optionsFile, Path configDir, List<Path> configFiles) {
        List<ModGroup> groups = new ArrayList<>();

        // 原版组（options.txt 存在才加）
        if (optionsFile != null && Files.exists(optionsFile)) {
            ModGroup vanilla = new ModGroup(ModGroup.VANILLA_ID);
            vanilla.addFile(optionsFile);
            groups.add(vanilla);
        }

        // 按长度降序排序，确保长的modid先匹配
        List<String> sortedInstalledModIds = getSortedModIds(installedModIds);
        List<String> sortedRepoModIds = getSortedModIds(repoModIds);

        ModGroup uncategorized = null;
        for (Path file : configFiles) {
            boolean ignored = false;
            String target = null;
            for (String candidate : candidatesOf(file, configDir)){
                // 匹配seed表
                Optional<String> targetModId = mapping.lookup(candidate);
                if (targetModId.isPresent()) {
                    if (MappingTable.IGNORE.equals(targetModId.get())) {
                        ignored = true;
                        break;
                    }
                    target = targetModId.get();
                    break;
                }

                // 匹配已安装mod
                String modId = matchModId(candidate, sortedInstalledModIds);
                if (modId != null) {
                    target = modId;
                    break;
                }

                // 匹配repo中的mod
                modId = matchModId(candidate, sortedRepoModIds);
                if (modId != null) {
                    target = modId;
                    break;
                }
            }
            if (ignored) {
                continue;
            }
            if (target != null) {
                findOrCreate(groups, target).addFile(file);
                continue;
            }

            // 未分类
            if (uncategorized == null) {
                uncategorized = new ModGroup(ModGroup.UNCATEGORIZED_ID);
                groups.add(uncategorized);
            }
            uncategorized.addFile(file);
        }

        ModGroup residue = new ModGroup(ModGroup.RESIDUE_ID);
        Iterator<ModGroup> it = groups.iterator();
        while (it.hasNext()) {
            ModGroup g = it.next();
            if (!isVisibleGroup(g.getModId()) && !installedModIds.contains(g.getModId())) {
                residue.getFiles().addAll(g.getFiles());
                it.remove();
            }
        }
        if (!residue.getFiles().isEmpty()) {
            groups.add(residue);
        }
        return groups;
    }

    private static boolean isVisibleGroup(String modId) {
        return ModGroup.VANILLA_ID.equals(modId) ||
                ModGroup.UNCATEGORIZED_ID.equals(modId);
    }

    private List<String> getSortedModIds(Set<String> modIds) {
        return modIds.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
    }

    private static List<String> candidatesOf(Path file, Path configDir) {
        String stem = stemOf(file.getFileName().toString());
        Path parent = file.getParent();
        if (parent != null && !parent.equals(configDir)) {
            String firstDir = configDir.relativize(parent).getName(0).toString();
            if (!firstDir.equalsIgnoreCase(stem)) {
                return List.of(firstDir, stem);
            }
        }
        return List.of(stem);
    }

    private String matchModId(String name, List<String> modIds) {
        String stem = stemOf(name);
        for (String modId : modIds) {
            if (StemMatches.matches(modId, stem)) {
                return modId;
            }
        }
        return null;
    }

    private static String stemOf(String name) {
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? name : name.substring(0, dot);
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
