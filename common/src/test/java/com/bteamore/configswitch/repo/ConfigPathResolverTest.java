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

    // fetch 的 backup 在游戏目录下（拉取前备份本地旧值），快照内结构与游戏目录同构
    private Path fetchBackup(String... more) {
        return gameDir.resolve(Path.of("config", "switch", "backup")).resolve(Path.of("", more));
    }

    // 默认布局：MC 的 configDir 就是 gameDir/config
    private LocalLayout defaultLayout() {
        return new LocalLayout(gameDir, gameDir.resolve("config"));
    }

    // 快照内的相对路径：backupRoot/<ts>/... 去掉时间戳层后的层级
    private Path snapshotRelative(ConfigPaths paths) {
        Path relative = paths.backupRoot().relativize(paths.backup());
        return relative.subpath(1, relative.getNameCount());
    }

    // minecraft 组：options.txt 位于 gameDir 根，无 config 前缀、无 mod 子目录
    @Test
    void testResolveMinecraftOptions() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths paths = resolver.resolve("minecraft", "options.txt", "ts", "fetch");

        assertNotNull(paths);
        assertEquals("options.txt", paths.relativePath());
        // modId 透传 group id：minecraft 组只有 modDir 被置空，modId 仍是 "minecraft"
        assertEquals("minecraft", paths.modId());
        assertEquals(gameDir.resolve("options.txt"), paths.active());
        assertEquals(COMMON_DIR.resolve("options.txt"), paths.global());
        // fetch backup 落在 gameDir/config/switch/backup/<ts>/ 下；minecraft 无 config 前缀
        // （原版文件本就在 gameDir 根，备份结构与游戏目录同构）
        assertEquals(fetchBackup("ts", "options.txt"), paths.backup());
        // fetch 的 backupRoot 是 gameDir/config/switch/backup
        assertEquals(gameDir.resolve("config").resolve("switch").resolve("backup"), paths.backupRoot());
    }

    // 普通 mod 组：active 带 config 前缀，global 带 mod 子目录；
    // fetch backup 与游戏目录同构：同样带 config 前缀（本地恢复按此相对路径还原）
    @Test
    void testResolveModConfig() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths paths = resolver.resolve("sodium", "sodium-options.json", "ts", "fetch");

        assertNotNull(paths);
        assertEquals("sodium-options.json", paths.relativePath());
        assertEquals("sodium", paths.modId());
        assertEquals(gameDir.resolve("config").resolve("sodium-options.json"), paths.active());
        assertEquals(COMMON_DIR.resolve("sodium").resolve("sodium-options.json"), paths.global());
        assertEquals(fetchBackup("ts", "config", "sodium-options.json"), paths.backup());
        // fetch 的 backupRoot 是 gameDir/config/switch/backup
        assertEquals(gameDir.resolve("config").resolve("switch").resolve("backup"), paths.backupRoot());
    }

    // uncategorized 的 null 分支已暂时注释：按普通 mod 组解析（若恢复该分支，本用例需改回 assertNull）
    @Test
    void testResolveUncategorizedTreatedAsMod() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths paths = resolver.resolve("uncategorized", "foo.cfg", "ts", "fetch");

        assertNotNull(paths);
        assertEquals("uncategorized", paths.modId());
        assertEquals(gameDir.resolve("config").resolve("foo.cfg"), paths.active());
        assertEquals(COMMON_DIR.resolve("uncategorized").resolve("foo.cfg"), paths.global());
        assertEquals(fetchBackup("ts", "config", "foo.cfg"), paths.backup());
    }

    // null modId 返回 null
    @Test
    void testResolveNullModIdReturnsNull() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        assertNull(resolver.resolve(null, "foo.cfg", "ts", "fetch"));
    }

    // 文件名带子目录时，三段路径都保留嵌套结构
    @Test
    void testResolveNestedFileName() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths paths = resolver.resolve("sodium", "sub/options.json", "ts", "fetch");

        assertNotNull(paths);
        assertEquals(gameDir.resolve("config").resolve("sub/options.json"), paths.active());
        assertEquals(COMMON_DIR.resolve("sodium").resolve("sub/options.json"), paths.global());
        assertEquals(fetchBackup("ts", "config", "sub/options.json"), paths.backup());
    }

    // 多级子目录：relativePath 原样透传，push 的 global 与 backup 都保留嵌套
    @Test
    void testResolveNestedPathPush() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths paths = resolver.resolve("sodium", "sub/deep/x.json", "ts", "push");

        assertNotNull(paths);
        assertEquals("sub/deep/x.json", paths.relativePath());
        assertEquals(gameDir.resolve("config").resolve("sub/deep/x.json"), paths.active());
        assertEquals(COMMON_DIR.resolve("sodium").resolve("sub/deep/x.json"), paths.global());
        assertEquals(GLOBAL_BACKUP_DIR.resolve("ts").resolve("sodium").resolve("sub/deep/x.json"), paths.backup());
        assertEquals(GLOBAL_BACKUP_DIR, paths.backupRoot());
    }

    // 核心不变量在嵌套路径下同样成立：快照内相对路径（去 <ts> 层）== active 相对 gameDir
    @Test
    void testNestedFetchBackupLayoutMirrorsGameDir() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths mod = resolver.resolve("sodium", "sub/deep/options.json", "ts", "fetch");

        assertEquals(Path.of("config", "sub", "deep", "options.json"), snapshotRelative(mod));
        assertEquals(gameDir.relativize(mod.active()), snapshotRelative(mod));
    }

    // 不同 timestamp 对应不同备份目录，避免多次推送互相覆盖
    @Test
    void testResolveBackupUsesTimestamp() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths paths = resolver.resolve("sodium", "a.cfg", "ts2", "fetch");

        assertEquals(fetchBackup("ts2", "config", "a.cfg"), paths.backup());
    }

    // 核心不变量：本地快照内相对路径（去掉 <ts> 层）== active 相对 gameDir 的路径。
    // BackupScreen 本地恢复用 LocalLayout.localRestoreTarget 按同一口径反解落点：
    // 原版文件在 gameDir 根（无前缀）、mod 文件带 config 前缀（落到 configDir，默认为 gameDir/config）。
    @Test
    void testFetchBackupLayoutMirrorsGameDir() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths vanilla = resolver.resolve("minecraft", "options.txt", "ts", "fetch");
        assertEquals(Path.of("options.txt"), snapshotRelative(vanilla));
        assertEquals(gameDir.relativize(vanilla.active()), snapshotRelative(vanilla));

        ConfigPaths mod = resolver.resolve("sodium", "sodium-options.json", "ts", "fetch");
        assertEquals(Path.of("config", "sodium-options.json"), snapshotRelative(mod));
        assertEquals(gameDir.relativize(mod.active()), snapshotRelative(mod));
    }

    // push 的 backup 在全局仓库下（推送前备份全局旧值）
    @Test
    void testResolvePushBackupGoesToGlobalRepo() {
        ConfigPathResolver resolver = new ConfigPathResolver(defaultLayout());

        ConfigPaths paths = resolver.resolve("sodium", "a.cfg", "ts", "push");

        assertNotNull(paths);
        assertEquals(GLOBAL_BACKUP_DIR.resolve("ts").resolve("sodium").resolve("a.cfg"), paths.backup());
        // push 的 backupRoot 是全局 BACKUP_DIR
        assertEquals(GLOBAL_BACKUP_DIR, paths.backupRoot());
        assertEquals("sodium", paths.modId());
    }

    // ---------- 自定义 configDir ----------

    // 自定义 configDir（不是 gameDir/config）→ mod 的 active 跟着 configDir 走，global 与快照结构不受影响
    @Test
    void testResolveModUsesCustomConfigDir() {
        Path customConfigDir = gameDir.resolve("custom-config");
        ConfigPathResolver resolver = new ConfigPathResolver(new LocalLayout(gameDir, customConfigDir));

        ConfigPaths paths = resolver.resolve("sodium", "sodium-options.json", "ts", "fetch");

        assertNotNull(paths);
        assertEquals(customConfigDir.resolve("sodium-options.json"), paths.active());
        assertEquals(COMMON_DIR.resolve("sodium").resolve("sodium-options.json"), paths.global());
        assertEquals(fetchBackup("ts", "config", "sodium-options.json"), paths.backup());
        // 本地备份根固定在 gameDir 下，不跟 configDir 走
        assertEquals(gameDir.resolve("config").resolve("switch").resolve("backup"), paths.backupRoot());
    }

    // 自定义 configDir 下 minecraft 组仍落在 gameDir 根：options.txt 的位置不由 configDir 决定
    @Test
    void testResolveVanillaIgnoresCustomConfigDir() {
        Path customConfigDir = gameDir.resolve("custom-config");
        ConfigPathResolver resolver = new ConfigPathResolver(new LocalLayout(gameDir, customConfigDir));

        ConfigPaths paths = resolver.resolve("minecraft", "options.txt", "ts", "fetch");

        assertNotNull(paths);
        assertEquals(gameDir.resolve("options.txt"), paths.active());
        assertEquals(fetchBackup("ts", "options.txt"), paths.backup());
        // 快照内相对路径仍是不带前缀的 options.txt：本地恢复才能把它还原回 gameDir 根
        assertEquals(Path.of("options.txt"), snapshotRelative(paths));
    }
}
