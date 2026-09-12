package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.IConfigFileService;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.util.Time;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

public class ConfigHandler implements IConfigFileService {
    public static final int MAX_BACKUP_COUNT = 10;

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
                backupRoot = target.backupRoot();
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
            if (!Files.exists(target.global())) {
                continue;
            }
            // 一致性规则:拉取前先备份即将被覆盖的本地配置
            copy(target.active(), target.backup());
            if (backupRoot == null) {
                backupRoot = target.backupRoot();
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
                    .filter(file -> Time.BACKUP_FILE_PATTERN.matcher(file.getFileName().toString()).matches())
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

    private void copy(Path from, Path to) {
        Path parent = to.getParent();
        if (parent == null) {
            Configswitch.LOGGER.error("Failed to copy {} -> {}: {}", from, to, "Parent directory is null");
            return;
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
            Configswitch.LOGGER.error("Failed to copy {} -> {}: {}", from, to, e.getMessage());
        }
    }
}
