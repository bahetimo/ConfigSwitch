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
    private static final Path GLOBAL_BACKUP_DIR = CONFIGSWITCH_DIR.resolve("backup");

    // fetch 的 backup 在游戏目录下（拉取前备份本地旧值）
    private Path fetchBackup(String... more) {
        return gameDir.resolve(Path.of("config", "switch", "backup")).resolve(Path.of("", more));
    }

    // minecraft 组：options.txt 位于 gameDir 根，无 config 前缀、无 mod 子目录
    @Test
    void testResolveMinecraftOptions() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("minecraft", "options.txt", "ts", "fetch");

        assertNotNull(paths);
        assertEquals("options.txt", paths.fileName());
        assertEquals(gameDir.resolve("options.txt"), paths.active());
        assertEquals(COMMON_DIR.resolve("options.txt"), paths.global());
        // fetch backup 固定落在 gameDir/config/switch/backup/<ts>/ 下，不带 mod 子目录
        assertEquals(fetchBackup("ts", "options.txt"), paths.backup());
    }

    // 普通 mod 组：active 带 config 前缀，global/backup 带 mod 子目录
    @Test
    void testResolveModConfig() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("sodium", "sodium-options.json", "ts", "fetch");

        assertNotNull(paths);
        assertEquals("sodium-options.json", paths.fileName());
        assertEquals(gameDir.resolve("config").resolve("sodium-options.json"), paths.active());
        assertEquals(COMMON_DIR.resolve("sodium").resolve("sodium-options.json"), paths.global());
        assertEquals(fetchBackup("ts", "sodium-options.json"), paths.backup());
    }

    // uncategorized 组不处理，返回 null
    @Test
    void testResolveUncategorizedReturnsNull() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        assertNull(resolver.resolve("uncategorized", "foo.cfg", "ts", "fetch"));
    }

    // null modId 返回 null
    @Test
    void testResolveNullModIdReturnsNull() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        assertNull(resolver.resolve(null, "foo.cfg", "ts", "fetch"));
    }

    // 文件名带子目录时，三段路径都保留嵌套结构
    @Test
    void testResolveNestedFileName() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("sodium", "sub/options.json", "ts", "fetch");

        assertNotNull(paths);
        assertEquals(gameDir.resolve("config").resolve("sub/options.json"), paths.active());
        assertEquals(COMMON_DIR.resolve("sodium").resolve("sub/options.json"), paths.global());
        assertEquals(fetchBackup("ts", "sub/options.json"), paths.backup());
    }

    // 不同 timestamp 对应不同备份目录，避免多次推送互相覆盖
    @Test
    void testResolveBackupUsesTimestamp() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("sodium", "a.cfg", "ts2", "fetch");

        assertEquals(fetchBackup("ts2", "a.cfg"), paths.backup());
    }

    // push 的 backup 在全局仓库下（推送前备份全局旧值）
    @Test
    void testResolvePushBackupGoesToGlobalRepo() {
        ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

        ConfigPaths paths = resolver.resolve("sodium", "a.cfg", "ts", "push");

        assertNotNull(paths);
        assertEquals(GLOBAL_BACKUP_DIR.resolve("ts").resolve("sodium").resolve("a.cfg"), paths.backup());
    }
}
