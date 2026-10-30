package com.bteamore.configswitch.client;

import com.bteamore.configswitch.discovery.*;
import com.bteamore.configswitch.platform.Platforms;
import com.bteamore.configswitch.repo.LocalLayout;
import com.bteamore.configswitch.repo.RepoPaths;
import com.bteamore.configswitch.util.Log;

import java.nio.file.Path;
import java.util.ArrayList;
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

    public ConfigDiscovery(LocalLayout localLayout) {
        gameDir = localLayout.gameDir();
        configDir = localLayout.configDir();
        optionsFile = gameDir.resolve("options.txt");
    }

    private Set<String> cachedModIds;

    public List<ModGroup> discoverLocal() {
        List<Path> configFiles = scanner.scan(configDir);

        SubdirReport report = analyzeSubdirs(configFiles);
        List<Path> subPaths = extractAdoptedFiles(report);

        List<Path> allPaths = new ArrayList<>();
        allPaths.addAll(configFiles);
        allPaths.addAll(subPaths);

        List<ModGroup> classified = classifier().classify(optionsFile, configDir, allPaths);
        Log.debug("discovered {} files -> {} groups({} files uncategorized)", allPaths.size(), classified.size(), classified.stream().filter(g -> g.getModId().equals(ModGroup.UNCATEGORIZED_ID)).map(ModGroup::getFiles).mapToLong(List::size).sum());
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

    private List<Path> extractAdoptedFiles(SubdirReport report) {
        List<Path> adoptedFiles = new ArrayList<>();
        List<Path> paths = report.entries().stream()
                .filter(entry -> entry.verdict() == SubdirReport.Verdict.ADOPTED)
                .map(entry -> configDir.resolve(entry.relativePath()))
                .toList();
        for (Path path : paths) {
            scanner.scan(path).forEach(p -> {
                if (ConfigFlavor.isConfigLike(p.getFileName().toString(), path.getFileName().toString())){
                    adoptedFiles.add(p);
                }
            });
        }
        return adoptedFiles;
    }

    private SubdirReport analyzeSubdirs(List<Path> flatFiles) {
        long startTime = System.nanoTime();
        Set<String> flatStems = flatFiles.stream()
                .map(p -> FileNames.stemOf(p.getFileName().toString()))
                .collect(Collectors.toSet());

        SubdirReport report = SubdirAnalyzer.analyze(configDir, flatStems);
        Log.debug("SubdirAnalyzer took {}ms", (System.nanoTime() - startTime) / 1000000);
        if (report.entries().isEmpty()){
            return report;
        }

        logSubdirTable(report);
        return report;
    }

    private static void logSubdirTable(SubdirReport report) {
        List<SubdirReport.Entry> adopted = byCountDesc(report, SubdirReport.Verdict.ADOPTED);
        List<SubdirReport.Entry> skipped = byCountDesc(report, SubdirReport.Verdict.SKIPPED);
        List<SubdirReport.Entry> unrecognized = byCountDesc(report, SubdirReport.Verdict.UNRECOGNIZED);

        Log.Table table = Log.table("Subdir analysis: adopted " + adopted.size()
                + " / skipped " + skipped.size()
                + " / unrecognized " + unrecognized.size());
        adopted.forEach(entry -> addRow(table, "\t", "ADOPTED", entry));
        skipped.forEach(entry -> addRow(table, "\t", "SKIPPED", entry));
        if (!unrecognized.isEmpty()) {
            table.row("\t", "UNRECOGNIZED (by file count desc, worth a look):");
            unrecognized.forEach(entry -> addRow(table, "\t\t", "", entry));
        }

        Log.debug("{}", table);
    }

    private static List<SubdirReport.Entry> byCountDesc(SubdirReport report, SubdirReport.Verdict verdict) {
        return report.entries().stream()
                .filter(entry -> entry.verdict() == verdict)
                .sorted(Comparator.comparingInt(SubdirReport.Entry::fileCount).reversed()
                        .thenComparing(SubdirReport.Entry::relativePath))
                .toList();
    }

    private static void addRow(Log.Table table, String indent, String verdict, SubdirReport.Entry entry) {
        String path = entry.relativePath();
        String count = entry.fileCount() + " files";
        String reason = reasonOf(entry);
        if (verdict.isEmpty()) {
            table.row(indent, path, count, reason);
        } else {
            table.row(indent, verdict, path, count, reason);
        }
    }

    private static String reasonOf(SubdirReport.Entry entry) {
        return switch (entry.reason()) {
            case BLACKLIST -> "blacklist: " + entry.detail();
            case FLAT_FOUND -> "flat stem: " + entry.detail();
            case PARENT_FLAVOR -> "parent config-like: " + entry.detail();
            case PARENT_SUBDIR -> "skipped: adopted via: " + entry.detail();
            case FLAVOR -> "config-like: " + entry.detail();
            case SUBDIR -> "via subdir: " + entry.detail();
            case NONE -> "none recognized";
        };
    }

    private ConfigClassifier classifier() {
        if (classifier == null) {
            classifier = new ConfigClassifier(getModIds(), MappingConfig.mapping(), new RepoModScanner().scan(RepoPaths.commonDir()));
        }
        return classifier;
    }

    public void invalidate(){
        this.classifier = null;
    }

    public Set<String> getModIds() {
        if (cachedModIds == null) {
            cachedModIds = Set.copyOf(Platforms.get().modIds());
        }
        return cachedModIds;
    }
}
