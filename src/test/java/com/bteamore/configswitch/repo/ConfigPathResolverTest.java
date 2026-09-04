package com.bteamore.configswitch.repo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigPathResolverTest {
    @TempDir
    Path gameDir;

    // 与实现中的 static 常量保持一致：root = user.home/AppData/Roaming/.minecraft/configswitch
    private static final Path CONFIGSWITCH_DIR = Path.of(
            System.getProperty("user.home"), "AppData", "Roaming", ".minecraft", "configswitch");
    private static final Path COMMON_DIR = CONFIGSWITCH_DIR.resolve("global").resolve("common");
    private static final Path BACKUP_DIR = CONFIGSWITCH_DIR.resolve("backup");

    // minecraft 组：options.txt 位于 gameDir 根，无 config 前缀、无 mod 子目录
    @Test
    void testResolveMinecraftOptions() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("minecraft", "options.txt", "ts");

        assertNotNull(paths);
        assertEquals("options.txt", paths.fileName());
        assertEquals(gameDir.resolve("options.txt"), paths.active());
        assertEquals(COMMON_DIR.resolve("options.txt"), paths.global());
        assertEquals(BACKUP_DIR.resolve("ts").resolve("options.txt"), paths.backup());
    }

    // 普通 mod 组：active 带 config 前缀，global/backup 带 mod 子目录
    @Test
    void testResolveModConfig() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("sodium", "sodium-options.json", "ts");

        assertNotNull(paths);
        assertEquals("sodium-options.json", paths.fileName());
        assertEquals(gameDir.resolve("config").resolve("sodium-options.json"), paths.active());
        assertEquals(COMMON_DIR.resolve("sodium").resolve("sodium-options.json"), paths.global());
        assertEquals(BACKUP_DIR.resolve("ts").resolve("sodium").resolve("sodium-options.json"), paths.backup());
    }

    // uncategorized 组不处理，返回 null
    @Test
    void testResolveUncategorizedReturnsNull() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        assertNull(resolver.resolve("uncategorized", "foo.cfg", "ts"));
    }

    // null modId 返回 null
    @Test
    void testResolveNullModIdReturnsNull() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        assertNull(resolver.resolve(null, "foo.cfg", "ts"));
    }

    // 文件名带子目录时，三段路径都保留嵌套结构
    @Test
    void testResolveNestedFileName() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("sodium", "sub/options.json", "ts");

        assertNotNull(paths);
        assertEquals(gameDir.resolve("config").resolve("sub/options.json"), paths.active());
        assertEquals(COMMON_DIR.resolve("sodium").resolve("sub/options.json"), paths.global());
        assertEquals(BACKUP_DIR.resolve("ts").resolve("sodium").resolve("sub/options.json"), paths.backup());
    }

    // 不同 timestamp 对应不同备份目录，避免多次推送互相覆盖
    @Test
    void testResolveBackupUsesTimestamp() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("sodium", "a.cfg", "ts2");

        assertEquals(BACKUP_DIR.resolve("ts2").resolve("sodium").resolve("a.cfg"), paths.backup());
    }
}
