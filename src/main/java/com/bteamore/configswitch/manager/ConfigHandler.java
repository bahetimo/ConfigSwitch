package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.IConfigFileService;
import com.bteamore.configswitch.repo.ConfigPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class ConfigHandler implements IConfigFileService {
    public static final int MAX_BACKUP_COUNT = 10;
    private static final Pattern BACKUP_FILE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}--\\d{2}-\\d{2}-\\d{2}");

    @Override
    public void pushActiveToGlobal(List<ConfigPaths> paths) {
        Path backupRoot = null;
        for (ConfigPaths target : paths) {
            if (target == null) {
                Configswitch.LOGGER.error("Push failed - no config target registered");
                continue;
            }
            // 一致性规则:推送前先备份即将被覆盖的全局配置
            if (Files.exists(target.global())) {
                copy(target.global(), target.backup());
            }

            if (backupRoot == null) {
                backupRoot = backupRootOf(target.backup());
            }
            copy(target.active(), target.global());
            // 不加try-catch是因为copy和backup方法内部已经处理了异常，后面再处理
        }
        if (backupRoot != null) {
            prune(backupRoot);
        }
    }

    @Override
    public void fetchGlobalToActive(List<ConfigPaths> paths) {
        Path backupRoot = null;
        for (ConfigPaths target : paths) {
            if (target == null) {
                Configswitch.LOGGER.error("Fetch failed - no config target registered");
                continue;
            }
            if (!Files.exists(target.active())) {
                continue;
            }
            // 一致性规则:拉取前先备份即将被覆盖的本地配置
            copy(target.active(), target.backup());
            if (backupRoot == null) {
                backupRoot = backupRootOf(target.backup());
            }

            copy(target.global(), target.active());
        }
        if (backupRoot != null) {
            prune(backupRoot);
        }
    }

    private void prune(Path backupDir) {
        try (var stream = Files.list(backupDir)) {
            List<Path> backups = stream
                    .filter(file -> BACKUP_FILE_PATTERN.matcher(file.getFileName().toString()).matches())
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .toList();
            for (int i = MAX_BACKUP_COUNT; i < backups.size(); i++) {
                this.deleteRecursively(backups.get(i));
            }
        } catch (IOException e) {
            Configswitch.LOGGER.warn("Failed to prune backups in {}: {}", backupDir, e.getMessage());
        }
    }

    private void deleteRecursively(Path path) {
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    Configswitch.LOGGER.warn("Failed to delete {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            Configswitch.LOGGER.warn("Failed to walk {}: {}", path, e.getMessage());
        }
    }

    // 暂时先这么处理
    private Path backupRootOf(Path backup) {
        Path dir = backup.getParent();
        // 向上找第一个时间戳命名的目录；getFileName() 为 null 说明已爬到文件系统根
        while (dir != null && dir.getFileName() != null
                && !BACKUP_FILE_PATTERN.matcher(dir.getFileName().toString()).matches()) {
            dir = dir.getParent();
        }
        // 没找到时间戳目录时退化为 backup 的直接父目录，避免 NPE
        return (dir == null || dir.getFileName() == null) ? backup.getParent() : dir.getParent();
    }

    private void copy(Path from, Path to) {
        try {
            if (to.getParent() != null && !Files.exists(to.getParent())) {
                Files.createDirectories(to.getParent());
            }

            // 先写临时文件，再原子替换
            Path temp = Files.createTempFile(Objects.requireNonNull(to.getParent()), to.getFileName().toString(), ".tmp");
            Files.copy(from, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to copy {} -> {}: {}", from, to, e.getMessage());
        }
    }
}
