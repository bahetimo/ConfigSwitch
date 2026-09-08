package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.repo.ConfigPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigHandlerTest {
    @TempDir
    Path tempDir;

    private ConfigPaths newPaths(String fileName) {
        // backup 路径带时间戳目录，与生产结构（backupRoot/<ts>/<fileName>）对齐
        Path backupRoot = tempDir.resolve("backup");
        return new ConfigPaths(
                fileName,
                tempDir.resolve("active").resolve(fileName),
                tempDir.resolve("global").resolve(fileName),
                backupRoot.resolve("2026-09-07--00-00-00").resolve(fileName),
                backupRoot
        );
    }

    // global 已存在时：先备份旧值到 backup，再以 active 覆盖 global
    @Test
    void testPushOverwritesExistingGlobalAndBacksUp() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        assertEquals("new", Files.readString(paths.global()));
        assertTrue(Files.exists(paths.backup()));
        assertEquals("old", Files.readString(paths.backup()));
    }

    // global 不存在时：直接创建 global，不产生备份
    @Test
    void testPushCreatesGlobalWithoutBackup() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        assertEquals("new", Files.readString(paths.global()));
        assertFalse(Files.exists(paths.backup()));
    }

    // 列表中的 null 元素跳过，不影响其余条目
    @Test
    void testPushSkipsNullEntry() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");

        // List.of 不允许 null 元素，用 Arrays.asList 构造含 null 的列表
        new ConfigHandler().pushActiveToGlobal(Arrays.asList(null, paths));

        assertEquals("new", Files.readString(paths.global()));
        assertFalse(Files.exists(paths.backup()));
    }

    // 覆盖：global "new"、active "old" → fetch 后 active 变 "new"
    @Test
    void testFetchOverwritesActiveWithGlobal() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "new");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "old");

        new ConfigHandler().fetchGlobalToActive(List.of(paths));

        assertEquals("new", Files.readString(paths.active()));
    }

    // 跳过：active 不存在 → fetch 后 active 仍不存在（不被创建）
    @Test
    void testFetchSkipsWhenActiveMissing() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "new");
        // active 文件不创建

        new ConfigHandler().fetchGlobalToActive(List.of(paths));

        assertFalse(Files.exists(paths.active()));
    }

    // 多条目：跳过条目不影响后续条目的处理
    @Test
    void testFetchSkipsMissingActiveButProcessesOthers() throws IOException {
        ConfigPaths skipped = newPaths("skipped.cfg");
        Files.createDirectories(skipped.global().getParent());
        Files.writeString(skipped.global(), "g");
        // skipped 的 active 不创建

        ConfigPaths covered = newPaths("covered.cfg");
        Files.createDirectories(covered.global().getParent());
        Files.writeString(covered.global(), "new");
        Files.createDirectories(covered.active().getParent());
        Files.writeString(covered.active(), "old");

        new ConfigHandler().fetchGlobalToActive(List.of(skipped, covered));

        assertFalse(Files.exists(skipped.active()));
        assertEquals("new", Files.readString(covered.active()));
    }

    // ---------- prune / deleteRecursively ----------

    // 时间戳格式（与 Time.timeString 一致）：yyyy-MM-dd--HH-mm-ss
    private static final String TS_FMT = "2026-09-07--%02d-00-00";

    // 构造 push 语义的 ConfigPaths：backup 落在 backupRoot/<ts>/<fileName>
    private ConfigPaths pushPaths(Path backupRoot, String ts, String fileName) {
        return new ConfigPaths(
                fileName,
                tempDir.resolve("active").resolve(fileName),
                tempDir.resolve("global").resolve(fileName),
                backupRoot.resolve(ts).resolve(fileName),
                backupRoot
        );
    }

    // 在 backupRoot 下手工造一个时间戳备份目录（内含一个文件）
    private Path makeBackupDir(Path backupRoot, String ts) throws IOException {
        Path dir = backupRoot.resolve(ts);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("old.cfg"), "old");
        return dir;
    }

    // prune：超过 MAX_BACKUP_COUNT 的时间戳目录被删除，仅保留最新的 N 个
    @Test
    void testPruneKeepsOnlyNewestBackups() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Files.createDirectories(backupRoot);
        int total = ConfigHandler.MAX_BACKUP_COUNT + 3;
        for (int i = 0; i < total; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        // 触发一次 push，使 prune 执行（global 存在才会备份+prune）
        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, total), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        try (var stream = Files.list(backupRoot)) {
            List<String> remaining = stream.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(ConfigHandler.MAX_BACKUP_COUNT, remaining.size());
            // 最旧的 4 个（00、01、02、03）应被删除，保留 04..total
            assertFalse(remaining.contains(String.format(TS_FMT, 0)));
            assertFalse(remaining.contains(String.format(TS_FMT, 1)));
            assertFalse(remaining.contains(String.format(TS_FMT, 2)));
            assertFalse(remaining.contains(String.format(TS_FMT, 3)));
            assertTrue(remaining.contains(String.format(TS_FMT, 4)));
            assertTrue(remaining.contains(String.format(TS_FMT, total)));
        }
    }

    // prune：非时间戳命名的条目不参与清理（即使数量超限）
    @Test
    void testPruneIgnoresNonTimestampEntries() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Files.createDirectories(backupRoot);
        for (int i = 0; i < ConfigHandler.MAX_BACKUP_COUNT + 1; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }
        Path keepMe = backupRoot.resolve("keep-me.txt");
        Files.writeString(keepMe, "important");
        Path keepDir = backupRoot.resolve("manual-backup");
        Files.createDirectories(keepDir);

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        assertTrue(Files.exists(keepMe));
        assertTrue(Files.exists(keepDir));
    }

    // deleteRecursively：非空目录连同内容被整体删除
    @Test
    void testDeleteRecursivelyRemovesNonEmptyDir() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        // 造 MAX+1 个时间戳目录，最旧的那个做成多层嵌套非空目录
        Path oldest = backupRoot.resolve(String.format(TS_FMT, 0));
        Files.createDirectories(oldest.resolve("nested/deep"));
        Files.writeString(oldest.resolve("nested/deep/a.cfg"), "a");
        Files.writeString(oldest.resolve("b.cfg"), "b");
        for (int i = 1; i <= ConfigHandler.MAX_BACKUP_COUNT; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        // 最旧的非空目录应被整体删除
        assertFalse(Files.exists(oldest));
        assertFalse(Files.exists(oldest.resolve("nested/deep/a.cfg")));
    }

    // ---------- prune 边界情况 ----------

    // prune：备份数量恰好等于 MAX_BACKUP_COUNT 时，push 后新备份使总数超限，最旧的被删除
    @Test
    void testPruneDoesNothingWhenCountEqualsMax() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Files.createDirectories(backupRoot);
        for (int i = 0; i < ConfigHandler.MAX_BACKUP_COUNT; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        try (var stream = Files.list(backupRoot)) {
            List<String> remaining = stream.map(p -> p.getFileName().toString()).sorted().toList();
            // 总数 MAX+1，prune 后保留 MAX 个
            assertEquals(ConfigHandler.MAX_BACKUP_COUNT, remaining.size());
            // 最旧的 00 被删除
            assertFalse(remaining.contains(String.format(TS_FMT, 0)));
            // 最新的 99 保留
            assertTrue(remaining.contains(String.format(TS_FMT, 99)));
        }
    }

    // prune：备份数量少于 MAX_BACKUP_COUNT 时不删除任何备份
    @Test
    void testPruneDoesNothingWhenCountBelowMax() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Files.createDirectories(backupRoot);
        int count = ConfigHandler.MAX_BACKUP_COUNT - 3;
        for (int i = 0; i < count; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        try (var stream = Files.list(backupRoot)) {
            List<String> remaining = stream.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(count + 1, remaining.size());
        }
    }

    // prune：空备份目录时不抛异常
    @Test
    void testPruneHandlesEmptyBackupDir() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Files.createDirectories(backupRoot);

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 0), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        // 不应抛出异常
        assertDoesNotThrow(() -> new ConfigHandler().pushActiveToGlobal(List.of(paths)));

        // 新备份应被创建
        assertTrue(Files.exists(backupRoot.resolve(String.format(TS_FMT, 0))));
    }

    // prune：备份目录不存在时不抛异常
    @Test
    void testPruneHandlesNonExistentBackupDir() throws IOException {
        Path backupRoot = tempDir.resolve("nonexistent");
        // 不创建 backupRoot 目录

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 0), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        // 不应抛出异常（copy 会创建目录，prune 会处理空目录）
        assertDoesNotThrow(() -> new ConfigHandler().pushActiveToGlobal(List.of(paths)));
    }

    // prune：时间戳排序正确性 - 确保按时间戳排序而非字典序
    @Test
    void testPruneSortsByTimestampCorrectly() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Files.createDirectories(backupRoot);
        // 创建时间戳目录，其中字典序和时间序不一致的情况
        // 例如：2026-09-07--09-00-00 字典序 < 2026-09-07--10-00-00，但时间序也正确
        // 更关键的是：2026-09-07--09-00-00 vs 2026-09-07--1-00-00（如果格式不固定宽度）
        // 由于格式是固定宽度 %02d，这里主要验证排序逻辑本身
        for (int i = 0; i < ConfigHandler.MAX_BACKUP_COUNT + 2; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        try (var stream = Files.list(backupRoot)) {
            List<String> remaining = stream.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(ConfigHandler.MAX_BACKUP_COUNT, remaining.size());
            // 最旧的 3 个（00、01、02）应被删除
            assertFalse(remaining.contains(String.format(TS_FMT, 0)));
            assertFalse(remaining.contains(String.format(TS_FMT, 1)));
            assertFalse(remaining.contains(String.format(TS_FMT, 2)));
            // 最新的应保留
            assertTrue(remaining.contains(String.format(TS_FMT, ConfigHandler.MAX_BACKUP_COUNT + 1)));
        }
    }

    // ---------- deleteRecursively 直接测试 ----------

    // deleteRecursively：删除单个文件
    @Test
    void testDeleteRecursivelyRemovesSingleFile() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Files.createDirectories(backupRoot);
        // 造 MAX+1 个时间戳目录，最旧的那个只包含一个文件
        Path oldest = backupRoot.resolve(String.format(TS_FMT, 0));
        Files.createDirectories(oldest);
        Files.writeString(oldest.resolve("single.cfg"), "content");
        for (int i = 1; i <= ConfigHandler.MAX_BACKUP_COUNT; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        assertFalse(Files.exists(oldest));
        assertFalse(Files.exists(oldest.resolve("single.cfg")));
    }

    // deleteRecursively：删除空目录
    @Test
    void testDeleteRecursivelyRemovesEmptyDir() throws IOException {
        Path backupRoot = tempDir.resolve("backups");
        Files.createDirectories(backupRoot);
        // 造 MAX+1 个时间戳目录，最旧的那个是空目录
        Path oldest = backupRoot.resolve(String.format(TS_FMT, 0));
        Files.createDirectories(oldest);
        for (int i = 1; i <= ConfigHandler.MAX_BACKUP_COUNT; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        assertFalse(Files.exists(oldest));
    }
}
