package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.IConfigFileService;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.repo.ConfigTargets;
import com.bteamore.configswitch.repo.IConfigTarget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class ConfigHandler implements IConfigFileService {
    private static final int MAX_BACKUPS = 3;
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    @Override
    public void pushActiveToGlobal() {
        IConfigTarget target = ConfigTargets.get();
        if (target == null) {
            Configswitch.LOGGER.error("Push failed - no config target registered");
            return;
        }
        // 一致性规则:推送前先备份即将被覆盖的全局配置
        if (Files.exists(target.globalFile())) {
            backup(target.globalFile(), target.backupFile());
        }
        copy(target.activeFile(), target.globalFile());
    }

    @Override
    public void pushActiveToGlobal(List<ConfigPaths> paths) {
        for (ConfigPaths target : paths) {
            if (target == null) {
                Configswitch.LOGGER.error("Push failed - no config target registered");
                continue;
            }
            // 一致性规则:推送前先备份即将被覆盖的全局配置
            if (Files.exists(target.global())) {
                copy(target.global(), target.backup());
            }
            copy(target.active(), target.global());
            // 不加try-catch是因为copy和backup方法内部已经处理了异常，后面再处理
        }
    }

    @Override
    public void fetchGlobalToActive() {
        IConfigTarget target = ConfigTargets.get();
        if (target == null) {
            Configswitch.LOGGER.error("Fetch failed - no config target registered");
            return;
        }
        copy(target.globalFile(), target.activeFile());
    }

    private void copy(Path from, Path to) {
        try {
            if (to.getParent() != null) {
                Files.createDirectories(to.getParent());
            }
            if (Files.exists(to)) {
                Files.delete(to);
            }
            Files.copy(from, to);
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to copy {} -> {}: {}", from, to, e.getMessage());
        }
    }

    // backup 备份到指定路径,按时间戳做备份,备份数量最多3个
    private void backup(Path source, Path backupFile) {
        try {
            if (backupFile.getParent() == null) {
                Configswitch.LOGGER.error("Backup file path is invalid: {}", backupFile);
            }
            Files.createDirectories(backupFile.getParent());

            String fileName = backupFile.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
            String ext = dot > 0 ? fileName.substring(dot) : "";

            Path dated = backupFile.getParent().resolve(stem + "-" + LocalDateTime.now().format(BACKUP_TIME) + ext);
            Files.copy(source, dated);

            prune(backupFile.getParent(), stem, ext);
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to backup {}: {}", source, e.getMessage());
        }
    }
    
    // 时间戳固定宽度,yyyyMMdd-HHmmss-SSS 按文件名排序即按时间排序,保留最新的 MAX_BACKUPS 个
    private void prune(Path backupDir, String stem, String ext) {
        try (var stream = Files.list(backupDir)) {
            List<Path> backups = stream
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(stem + "-") && name.endsWith(ext);
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .toList();
            for (int i = MAX_BACKUPS; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
            }
        } catch (IOException e) {
            Configswitch.LOGGER.warn("Failed to prune backups in {}: {}", backupDir, e.getMessage());
        }
    }
}
