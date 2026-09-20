package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.core.SyncOutcome;
import com.bteamore.configswitch.core.IConfigFileService;
import com.bteamore.configswitch.core.SyncReport;
import com.bteamore.configswitch.discovery.BackupSnapshot;
import com.bteamore.configswitch.discovery.ModGroup;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.util.Hash;
import com.bteamore.configswitch.util.Log;
import com.bteamore.configswitch.util.Time;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigHandler implements IConfigFileService {
    private final int maxBackupCount;
    private static final Map<SyncOutcome, Integer> PRIORITY = Map.of(
            SyncOutcome.FAILED, 3,
            SyncOutcome.SUCCESS, 2,
            SyncOutcome.SKIPPED, 1
    );

    public ConfigHandler(int maxBackupCount) {
        this.maxBackupCount = maxBackupCount;
    }

    @Override
    public SyncReport pushActiveToGlobal(List<ConfigPaths> paths) {
        Path backupRoot = null;
        Map<String, SyncOutcome> results = new LinkedHashMap<>();

        for (ConfigPaths target : paths) {
            if (target == null) {
                Log.error("Push failed - no config target registered");
                continue;
            }
            Log.debug("Pushing {} -> {}", target.active(), target.global());

            // 一致性规则:推送前先备份即将被覆盖的全局配置
            boolean backupOk = true;
            if (Files.exists(target.global())) {
                backupOk = copy(target.global(), target.backup());
            }

            if (backupRoot == null) {
                backupRoot = target.backupRoot();
            }
            boolean copyOk = copy(target.active(), target.global());
            if (!backupOk || !copyOk) {
                priorityMerge(results, target.modId(), SyncOutcome.FAILED);
            } else {
                priorityMerge(results, target.modId(), SyncOutcome.SUCCESS);
            }
            // 不加try-catch是因为copy和backup方法内部已经处理了异常，后面再处理
        }
        if (backupRoot != null) {
            prune(backupRoot);
        }

        SyncReport report = new SyncReport(results);
        Log.info("Push finished: {} mods ({} ok, {} failed, {} skipped)", report.results().size(), report.count(SyncOutcome.SUCCESS), report.count(SyncOutcome.FAILED), report.count(SyncOutcome.SKIPPED));
        return report;
    }

    @Override
    public SyncReport fetchGlobalToActive(List<ConfigPaths> paths) {
        Path backupRoot = null;
        Map<String, SyncOutcome> results = new LinkedHashMap<>();

        for (ConfigPaths target : paths) {
            if (target == null) {
                Log.error("Fetch failed - no config target registered");
                continue;
            }
            Log.debug("Fetching {} -> {}", target.global(), target.active());

            if (!Files.exists(target.global())) {
                priorityMerge(results, target.modId(), SyncOutcome.SKIPPED);
                continue;
            }
            // 一致性规则:拉取前先备份即将被覆盖的本地配置
            boolean backupOk = copy(target.active(), target.backup());

            if (backupRoot == null) {
                backupRoot = target.backupRoot();
            }

            boolean isVanilla = ModGroup.VANILLA_ID.equals(target.modId());
            int hashBefore = isVanilla ? 0 : Hash.hashOf(target.active());

            boolean copyOk = copy(target.global(), target.active());
            if (!backupOk || !copyOk) {
                priorityMerge(results, target.modId(), SyncOutcome.FAILED);
            } else {
                priorityMerge(results, target.modId(), SyncOutcome.SUCCESS);
            }
            if (copyOk && !isVanilla){
                PendingRewrites.add(target.active(), target.global(), hashBefore);
            }
        }
        if (backupRoot != null) {
            prune(backupRoot);
        }

        SyncReport report = new SyncReport(results);
        Log.info("Fetch finished: {} mods ({} ok, {} failed, {} skipped)", report.results().size(), report.count(SyncOutcome.SUCCESS), report.count(SyncOutcome.FAILED), report.count(SyncOutcome.SKIPPED));
        return report;
    }

    @Override
    public SyncReport restore(BackupSnapshot snapshot, Path targetRoot, Path backupRoot) {
        Path sourceRoot = backupRoot.resolve(snapshot.timeStamp());
        Map<String, SyncOutcome> results = new LinkedHashMap<>();
        String newTimeStamp = Time.timeString();

        for (Path relative : snapshot.relativePaths()) {
            if (relative == null) {
                Log.error("Restore failed - no config path registered");
                continue;
            }

            Path source = sourceRoot.resolve(relative);
            Path target = targetRoot.resolve(relative);

            Log.debug("Restoring {} -> {}", source, target);

            boolean backupOk = true;
            if (Files.exists(target)) {
                backupOk = copy(target, backupRoot.resolve(newTimeStamp).resolve(relative));
            }
            boolean copyOk = copy(source, target);

            if (!backupOk || !copyOk) {
                priorityMerge(results, relative.toString(), SyncOutcome.FAILED);
            } else {
                priorityMerge(results, relative.toString(), SyncOutcome.SUCCESS);
            }
        }
        prune(backupRoot);

        SyncReport report = new SyncReport(results);
        Log.info("Restore finished: {} mods ({} ok, {} failed, {} skipped)", report.results().size(), report.count(SyncOutcome.SUCCESS), report.count(SyncOutcome.FAILED), report.count(SyncOutcome.SKIPPED));
        return report;
    }

    private void priorityMerge(Map<String, SyncOutcome> result, String modId, SyncOutcome outcome) {
        result.merge(modId, outcome, (o1, o2) -> PRIORITY.get(o1) >= PRIORITY.get(o2) ? o1 : o2);
    }

    private void prune(Path backupDir) {
        try (var stream = Files.list(backupDir)) {
            List<Path> backups = stream
                    .filter(file -> Time.BACKUP_FILE_PATTERN.matcher(file.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .toList();
            for (int i = maxBackupCount; i < backups.size(); i++) {
                this.deleteRecursively(backups.get(i));
            }
        } catch (IOException e) {
            Log.warn("Failed to prune backups in {}", backupDir, e);
        }
    }

    private void deleteRecursively(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    Log.warn("Failed to delete {}", p, e);
                }
            });
        } catch (IOException e) {
            Log.warn("Failed to walk {}", path, e);
        }
    }

    private boolean copy(Path from, Path to) {
        Path parent = to.getParent();
        if (parent == null) {
            Log.error("Failed to copy {} -> {}: {}", from, to, "invalid parent directory");
            return false;
        }
        try {
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // 先写临时文件，再原子替换
            Path temp = Files.createTempFile(parent, to.getFileName().toString(), ".tmp");
            Files.copy(from, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Log.error("Failed to copy {} -> {}", from, to, e);
            return false;
        }
        return true;
    }
}
