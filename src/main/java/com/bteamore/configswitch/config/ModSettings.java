package com.bteamore.configswitch.config;

import com.bteamore.configswitch.Configswitch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public record ModSettings(String repoRoot, int maxBackupCount) {
    public static final Path DEFAULT_ROOT = Path.of(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft", "configswitch");

    public static List<Integer> ALLOWED_BACKUP_COUNTS = List.of(5, 10, 15, 20, 30, 50);
    public static final int DEFAULT_MAX_BACKUP_COUNT = 10;

    public static final ModSettings DEFAULT = new ModSettings(defaultRepoRoot(), DEFAULT_MAX_BACKUP_COUNT);

    public ModSettings {
        if (repoRoot == null || repoRoot.isBlank()) repoRoot = defaultRepoRoot();
        if (maxBackupCount <= 0) maxBackupCount = DEFAULT_MAX_BACKUP_COUNT;
    }

    public static String validateRepoRoot(String raw) {
        if (raw == null || raw.isBlank())
            return "路径不能为空";

        Path path;
        try {
            path = Path.of(raw);
            if (!path.isAbsolute()) {
                return "请填写完整路径";
            }
        } catch (InvalidPathException e) {
            return "路径包含非法字符";
        }

        Path ancestor = path;
        while (ancestor != null) {
            try {
                // 只校验不创建
                if (Files.readAttributes(ancestor, BasicFileAttributes.class).isDirectory()) {
                    // 存在且是目录还不够：不可写的话保存能过、重启后 push/fetch 全失败
                    return Files.isWritable(ancestor) ? null : "路径不可写";
                }
                return ancestor.equals(path) ? "该路径已存在同名文件" : "上级路径不是文件夹";
            } catch (NoSuchFileException e) {
                // 这一级还没建过：继续向上找最近的已存在目录，不创建任何东西
                ancestor = ancestor.getParent();
            } catch (IOException e) {
                Configswitch.LOGGER.error("路径不可访问: {}", e.getMessage());
                return "路径不可访问";
            }
        }
        return "路径不可访问";
    }

    public static boolean isSyntacticallyValid(ModSettings s) {
        if (s.repoRoot() == null || s.repoRoot().isBlank()){
            return false;
        }
        try {
            if (!Path.of(s.repoRoot()).isAbsolute()){
                return false;
            }
        } catch (InvalidPathException e) {
            return false;
        }
        return ALLOWED_BACKUP_COUNTS.contains(s.maxBackupCount());
    }

    private static String defaultRepoRoot() {
        return DEFAULT_ROOT.toString();
    }
}
