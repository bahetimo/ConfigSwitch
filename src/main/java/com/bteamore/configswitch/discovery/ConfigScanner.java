package com.bteamore.configswitch.discovery;

import com.bteamore.configswitch.Configswitch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 扫描 config 目录下的扁平文件，应用白名单（扩展名）与排除规则
 * 纯文件系统操作，不依赖 Minecraft，可单测
 */
public class ConfigScanner {

    // 配置文件白名单
    private static final Set<String> WHITELIST = Set.of(
            "json", "json5", "toml", "properties", "ini", "cfg", "conf", "config", "txt"
    );

    // 按文件名整体排除（Fabric Loader 生成物，不是 mod 配置）
    private static final Set<String> EXCLUDE_FILE_NAMES = Set.of(
            "fabric_loader_dependencies.json"
    );

    public List<Path> scan(Path configDir) {
        List<Path> result = new ArrayList<>();
        if (configDir == null || Files.notExists(configDir)) {
            return result; // 首次运行 空结果，不报错
        }
        try (Stream<Path> entries = Files.list(configDir)) {
            entries.filter(Files::isRegularFile)
                    .filter(this::isCandidate)
                    .forEach(result::add);
        } catch (IOException e) {
            Configswitch.LOGGER.error("扫描 config 目录失败 {}: {}", configDir, e.getMessage());
        }
        return result;
    }

    private boolean isCandidate(Path file) {
        String name = file.getFileName().toString();
        if (EXCLUDE_FILE_NAMES.contains(name)) {
            return false;
        }
        if (name.endsWith(".backup")) {
            return false;
        }
        String ext = extensionOf(name);
        return ext != null && WHITELIST.contains(ext);
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
