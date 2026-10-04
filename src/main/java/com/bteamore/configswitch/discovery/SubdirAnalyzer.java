package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SubdirAnalyzer {
    private static final ConfigScanner scanner = new ConfigScanner();

    private SubdirAnalyzer() {
    }

    public static SubdirReport analyze(Path configDir, Set<String> flatStems) {
        if (!Files.exists(configDir)) {
            Log.warn("Config directory {} does not exist", configDir);
            return new SubdirReport(List.of());
        }
        flatStems = flatStems.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        List<SubdirReport.Entry> entries = new ArrayList<>();
        try (var dirs = Files.list(configDir)) {
            Set<String> finalFlatStems = flatStems;
            dirs.filter(Files::isDirectory)
                    .forEach(dir -> {
                        List<SubdirReport.Entry> found = findConfig(dir, dir.getFileName().toString(), finalFlatStems, true);
                        entries.addAll(found);
                    });
        } catch (IOException e) {
            Log.error("Error analyzing subdirectories", e);
        }

        entries.sort(Comparator.comparing(SubdirReport.Entry::relativePath));
        return new SubdirReport(entries);
    }

    private static List<SubdirReport.Entry> findConfig(Path dir, String relPath, Set<String> flatStems, boolean topLevel) {
        List<SubdirReport.Entry> entries = new ArrayList<>();
        String dirName = dir.getFileName().toString();
        // 黑名单过滤
        if (NonConfigFilter.isSkipped(relPath)) {
            addEntry(entries, new SubdirReport.Entry(relPath, countAllFiles(dir), SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.BLACKLIST, dirName));
            return entries;
        }
        // 平铺过滤
        if (topLevel && flatStems.contains(dirName.toLowerCase())) {
            addEntry(entries, new SubdirReport.Entry(relPath, countAllFiles(dir), SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.FLAT_FOUND, dirName));
            return entries;
        }

        // 是否识别为配置文件，detail为null时，表示未识别为配置文件，反之表示识别为配置文件
        String detail = null;
        for (Path file : scanner.scan(dir)) {
            if (ConfigFlavor.isConfigLike(file.getFileName().toString(), dirName)) {
                detail = file.getFileName().toString();
                break;
            }
        }

        boolean childAdopted = false;
        boolean hasOwnFile = false;
        String adoptedBy = null;
        List<SubdirReport.Entry> childrenEntries = new ArrayList<>();
        try (var children = Files.list(dir)) {
            List<Path> allFiles = new ArrayList<>(children.toList());
            hasOwnFile = allFiles.stream().anyMatch(Files::isRegularFile);

            for (var child : allFiles.stream().filter(Files::isDirectory)
                    .sorted(Comparator.comparing(a -> a.getFileName().toString()))
                    .toList()) {
                String childRelPath = relPath + "/" + child.getFileName();
                // 如果识别为配置文件，则跳过
                if (detail != null) {
                    addEntry(childrenEntries, new SubdirReport.Entry(childRelPath, countAllFiles(child), SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_FLAVOR, detail));
                    continue;
                }
                // 如果其他子目录已识别到配置文件，则跳过
                if (adoptedBy != null) {
                    addEntry(childrenEntries, new SubdirReport.Entry(childRelPath, countAllFiles(child), SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR, adoptedBy));
                    continue;
                }
                // 反之，递归检查子目录
                List<SubdirReport.Entry> childEntries = findConfig(child, childRelPath, flatStems, false);
                childrenEntries.addAll(childEntries);
                if (findAdopted(childEntries)) {
                    childAdopted = true;
                    adoptedBy = childRelPath;
                }
            }
        } catch (IOException e) {
            Log.warn("Error reading directory {}", dir, e);
        }

        if (detail != null) { // 识别为配置文件
            addEntry(entries, new SubdirReport.Entry(relPath, countAllFiles(dir), SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.FLAVOR, detail));
            entries.addAll(childrenEntries);
        } else if (childAdopted) { // 子目录中识别到配置文件
            addEntry(entries, new SubdirReport.Entry(relPath, countAllFiles(dir), SubdirReport.Verdict.ADOPTED, SubdirReport.Reason.SUBDIR, adoptedBy));

            for (SubdirReport.Entry entry : childrenEntries) {
                SubdirReport.Entry newEntry = entry;
                if (entry.verdict() == SubdirReport.Verdict.UNRECOGNIZED) {
                    newEntry = new SubdirReport.Entry(entry.relativePath(), entry.fileCount(), SubdirReport.Verdict.SKIPPED, SubdirReport.Reason.PARENT_SUBDIR, adoptedBy);
                }
                addEntry(entries, newEntry);
            }
        } else { // 未识别到配置文件
            if (hasOwnFile) {
                addEntry(entries, new SubdirReport.Entry(relPath, countAllFiles(dir), SubdirReport.Verdict.UNRECOGNIZED, SubdirReport.Reason.NONE, null));
            }
            entries.addAll(childrenEntries);
        }
        return entries;
    }

    private static void addEntry(List<SubdirReport.Entry> entries, SubdirReport.Entry entry) {
        if (entry.fileCount() > 0){
            entries.add(entry);
        }
    }

    private static boolean findAdopted(List<SubdirReport.Entry> entries) {
        return entries.stream()
                .anyMatch(entry -> entry.verdict() == SubdirReport.Verdict.ADOPTED);
    }

    private static int countAllFiles(Path dir) {
        try (var stream = Files.walk(dir)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            Log.warn("Error counting files in {}", dir, e);
            return 0;
        }
    }
}
