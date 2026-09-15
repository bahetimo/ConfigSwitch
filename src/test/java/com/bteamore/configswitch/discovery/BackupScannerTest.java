package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BackupScannerTest {
    @TempDir
    Path tempDir;

    private final BackupScanner scanner = new BackupScanner();

    // 定宽时间戳（与 Time.timeString 一致），字典序即时间序
    private static final String TS_OLDEST = "2026-08-31--23-59-59";
    private static final String TS_MIDDLE = "2026-09-07--09-00-00";
    private static final String TS_NEWEST = "2026-09-07--10-00-00";

    // 在备份根下造一个快照目录并写入文件；relativeFiles 是相对快照根的路径，不传则造空快照
    private Path makeSnapshot(Path backupRoot, String timeStamp, String... relativeFiles) throws IOException {
        Path snapshotRoot = backupRoot.resolve(timeStamp);
        if (relativeFiles.length == 0) {
            Files.createDirectories(snapshotRoot);
            return snapshotRoot;
        }
        for (String relativeFile : relativeFiles) {
            Path file = snapshotRoot.resolve(relativeFile);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "content");
        }
        return snapshotRoot;
    }

    private List<String> timeStampsOf(List<BackupSnapshot> snapshots) {
        return snapshots.stream().map(BackupSnapshot::timeStamp).toList();
    }

    // ---------- 问题 1：备份根不可用时返回空列表，不抛异常 ----------

    // 备份根不存在 → 空列表，不抛异常
    @Test
    void testMissingBackupRootReturnsEmpty() {
        Path missing = tempDir.resolve("no-such-backup");

        List<BackupSnapshot> snapshots = assertDoesNotThrow(() -> scanner.scan(missing));

        assertTrue(snapshots.isEmpty());
    }

    // null 备份根 → 空列表，不抛异常
    @Test
    void testNullBackupRootReturnsEmpty() {
        List<BackupSnapshot> snapshots = assertDoesNotThrow(() -> scanner.scan(null));

        assertTrue(snapshots.isEmpty());
    }

    // 空备份根（存在但没有快照）→ 空列表
    @Test
    void testEmptyBackupRootReturnsEmpty() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        Files.createDirectories(backupRoot);

        assertTrue(scanner.scan(backupRoot).isEmpty());
    }

    // 备份根是普通文件 → Files.list 抛 IOException 被吞掉，仍返回空列表
    @Test
    void testBackupRootIsFileReturnsEmpty() throws IOException {
        Path notADir = tempDir.resolve("backup");
        Files.writeString(notADir, "not a directory");

        List<BackupSnapshot> snapshots = assertDoesNotThrow(() -> scanner.scan(notADir));

        assertTrue(snapshots.isEmpty());
    }

    // ---------- 问题 3：快照按时间戳倒序 ----------

    // 多个快照 → 最新的在最前
    @Test
    void testSnapshotsSortedNewestFirst() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        // 故意打乱创建顺序，确保结果只由时间戳决定
        makeSnapshot(backupRoot, TS_MIDDLE, "a.cfg");
        makeSnapshot(backupRoot, TS_OLDEST, "a.cfg");
        makeSnapshot(backupRoot, TS_NEWEST, "a.cfg");

        List<BackupSnapshot> snapshots = scanner.scan(backupRoot);

        assertEquals(List.of(TS_NEWEST, TS_MIDDLE, TS_OLDEST), timeStampsOf(snapshots));
    }

    // ---------- 快照筛选规则 ----------

    // 非时间戳命名的目录不是快照（手工备份、临时目录等）
    @Test
    void testNonTimestampDirIgnored() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        makeSnapshot(backupRoot, TS_NEWEST, "a.cfg");
        Path manual = backupRoot.resolve("manual-backup");
        Files.createDirectories(manual);
        Files.writeString(manual.resolve("a.cfg"), "x");
        Files.createDirectories(backupRoot.resolve("tmp"));

        List<BackupSnapshot> snapshots = scanner.scan(backupRoot);

        assertEquals(List.of(TS_NEWEST), timeStampsOf(snapshots));
    }

    // 时间戳命名的普通文件不是快照（只认目录）
    @Test
    void testTimestampNamedFileIgnored() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        Files.createDirectories(backupRoot);
        Files.writeString(backupRoot.resolve(TS_NEWEST), "not a snapshot dir");

        assertTrue(scanner.scan(backupRoot).isEmpty());
    }

    // ---------- 核心逻辑：relativePaths ----------

    // 嵌套结构 <ts>/<modId>/<file> → 相对路径保留 modId 层，且不含时间戳层
    @Test
    void testNestedSnapshotKeepsModIdInRelativePaths() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        makeSnapshot(backupRoot, TS_NEWEST,
                "sodium/sodium-options.json",
                "sodium/sodium-mixins.properties");

        BackupSnapshot snapshot = scanner.scan(backupRoot).get(0);

        assertEquals(TS_NEWEST, snapshot.timeStamp());
        assertEquals(2, snapshot.relativePaths().size());
        assertTrue(snapshot.relativePaths().containsAll(List.of(
                Path.of("sodium", "sodium-options.json"),
                Path.of("sodium", "sodium-mixins.properties"))));
        // 相对快照根：不带时间戳前缀，也不是绝对路径
        assertTrue(snapshot.relativePaths().stream()
                .noneMatch(path -> path.isAbsolute() || path.startsWith(TS_NEWEST)));
    }

    // 原版配置 <ts>/options.txt（比 mod 少一层）→ relativePaths = [options.txt]
    @Test
    void testVanillaSnapshotRelativePaths() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        makeSnapshot(backupRoot, TS_NEWEST, "options.txt");

        BackupSnapshot snapshot = scanner.scan(backupRoot).get(0);

        assertEquals(List.of(Path.of("options.txt")), snapshot.relativePaths());
    }

    // 更深层的嵌套保留完整层级
    @Test
    void testDeeplyNestedFileKeepsFullRelativePath() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        makeSnapshot(backupRoot, TS_NEWEST, "mod-a/sub/deep.cfg");

        BackupSnapshot snapshot = scanner.scan(backupRoot).get(0);

        assertEquals(List.of(Path.of("mod-a", "sub", "deep.cfg")), snapshot.relativePaths());
    }

    // 各快照只收自己目录下的文件，不串台
    @Test
    void testSnapshotsDoNotLeakEachOthersFiles() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        makeSnapshot(backupRoot, TS_NEWEST, "sodium/new.json");
        makeSnapshot(backupRoot, TS_OLDEST, "sodium/old.json");

        List<BackupSnapshot> snapshots = scanner.scan(backupRoot);

        assertEquals(List.of(Path.of("sodium", "new.json")), snapshots.get(0).relativePaths());
        assertEquals(List.of(Path.of("sodium", "old.json")), snapshots.get(1).relativePaths());
    }

    // 空快照目录仍会返回（不因没有文件被丢弃），relativePaths 为空
    @Test
    void testEmptySnapshotReturnedWithEmptyPaths() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        makeSnapshot(backupRoot, TS_OLDEST);
        makeSnapshot(backupRoot, TS_NEWEST, "a.cfg");

        List<BackupSnapshot> snapshots = scanner.scan(backupRoot);

        assertEquals(List.of(TS_NEWEST, TS_OLDEST), timeStampsOf(snapshots));
        assertTrue(snapshots.get(1).relativePaths().isEmpty());
    }

    // 快照内只有目录、没有文件 → relativePaths 为空（只收 regular file）
    @Test
    void testDirsWithoutFilesProduceEmptyPaths() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        Files.createDirectories(backupRoot.resolve(TS_NEWEST).resolve("sodium/nested"));

        List<BackupSnapshot> snapshots = scanner.scan(backupRoot);

        assertEquals(List.of(TS_NEWEST), timeStampsOf(snapshots));
        assertTrue(snapshots.get(0).relativePaths().isEmpty());
    }
}
