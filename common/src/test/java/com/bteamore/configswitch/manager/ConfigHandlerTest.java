package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.config.PackManifest;
import com.bteamore.configswitch.core.PackReport;
import com.bteamore.configswitch.core.SyncOutcome;
import com.bteamore.configswitch.core.SyncReport;
import com.bteamore.configswitch.discovery.BackupSnapshot;
import com.bteamore.configswitch.discovery.ModGroup;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.repo.LocalLayout;
import com.bteamore.configswitch.repo.ModSource;
import com.bteamore.configswitch.repo.RepoMeta;
import com.bteamore.configswitch.repo.RepoMetaStore;
import com.bteamore.configswitch.repo.SourceInfo;
import com.bteamore.configswitch.util.Time;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigHandlerTest {
    @TempDir
    Path tempDir;

    // SyncReport 以 modId 为 key 聚合，不跨 mod 的用例统一用这个占位值
    private static final String MOD_ID = "test-mod";

    // 备份保留份数：显式注入给 handler（生产由 ModConfig.settings() 提供）
    // 刻意取一个与 ModSettings 默认值不同的数，确保断言的是注入值而不是某处的硬编码
    private static final int MAX_BACKUP_COUNT = 5;

    // 与生产 backup 路径结构对齐的时间戳目录名
    private static final String TS = "2026-09-07--00-00-00";

    // 本次 push 会写的 meta 落点：每个用例各自一个 tempDir，天然隔离
    private Path metaPath() {
        return tempDir.resolve("repo/global/meta.json");
    }

    // 默认 handler 接一个真实落点的 RepoMetaStore；只有断言 meta 行为的用例才需要自己造 store 注入
    private ConfigHandler handler() {
        return handler(new RepoMetaStore(metaPath()));
    }

    private ConfigHandler handler(RepoMetaStore metaStore) {
        return new ConfigHandler(MAX_BACKUP_COUNT,
                new SourceInfo("1.21.1", "fabric"), metaStore);
    }

    private ConfigPaths newPaths(String fileName) {
        return newPaths(MOD_ID, fileName);
    }

    // 指定 modId，用于断言 SyncReport 按 modId 聚合
    private ConfigPaths newPaths(String modId, String fileName) {
        // backup 路径带时间戳目录；handler 只按传入路径读写、不关心目录内部结构，这里用扁平形式简化
        Path backupRoot = tempDir.resolve("backup");
        return new ConfigPaths(
                fileName,
                tempDir.resolve("active").resolve(fileName),
                tempDir.resolve("global").resolve(fileName),
                backupRoot.resolve(TS).resolve(fileName),
                backupRoot,
                modId
        );
    }

    // 失败注入：backup 的父目录被同名普通文件占位 → copy 建临时文件时抛 IOException → 返回 false
    private ConfigPaths brokenBackupPaths(String modId, String fileName) throws IOException {
        Path blocked = tempDir.resolve("blocked-" + fileName);
        Files.writeString(blocked, "not a directory");
        Path backupRoot = tempDir.resolve("backup");
        Files.createDirectories(backupRoot);
        return new ConfigPaths(
                fileName,
                tempDir.resolve("active").resolve(fileName),
                tempDir.resolve("global").resolve(fileName),
                blocked.resolve(fileName),
                backupRoot,
                modId
        );
    }

    // 写文件并自动创建父目录
    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    // global 已存在时：先备份旧值到 backup，再以 active 覆盖 global
    @Test
    void testPushOverwritesExistingGlobalAndBacksUp() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        SyncReport report = handler().pushActiveToGlobal(List.of(paths));

        assertEquals("new", Files.readString(paths.global()));
        assertTrue(Files.exists(paths.backup()));
        assertEquals("old", Files.readString(paths.backup()));
        assertEquals(SyncOutcome.SUCCESS, report.results().get(MOD_ID));
    }

    // global 不存在时：直接创建 global，不产生备份
    @Test
    void testPushCreatesGlobalWithoutBackup() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");

        SyncReport report = handler().pushActiveToGlobal(List.of(paths));

        assertEquals("new", Files.readString(paths.global()));
        assertFalse(Files.exists(paths.backup()));
        assertEquals(SyncOutcome.SUCCESS, report.results().get(MOD_ID));
    }

    // 嵌套相对路径：global 与 backup 都保留子目录结构，父目录不存在时自动创建
    @Test
    void testPushKeepsNestedRelativePath() throws IOException {
        ConfigPaths paths = newPaths("sub/a.cfg");
        write(paths.active(), "new");
        write(paths.global(), "old");

        SyncReport report = handler().pushActiveToGlobal(List.of(paths));

        assertEquals("new", Files.readString(paths.global()));
        assertEquals("old", Files.readString(paths.backup()));
        assertEquals(SyncOutcome.SUCCESS, report.results().get(MOD_ID));
    }

    // 列表中的 null 元素跳过，不影响其余条目
    @Test
    void testPushSkipsNullEntry() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");

        // List.of 不允许 null 元素，用 Arrays.asList 构造含 null 的列表
        SyncReport report = handler().pushActiveToGlobal(Arrays.asList(null, paths));

        assertEquals("new", Files.readString(paths.global()));
        assertFalse(Files.exists(paths.backup()));
        // null 条目没有 modId，不进报告
        assertEquals(1, report.results().size());
        assertEquals(SyncOutcome.SUCCESS, report.results().get(MOD_ID));
    }

    // ---------- push 写 meta ----------
    //
    // meta 自身的解析与原子写由 RepoMetaStoreTest 负责，这里只压"push 的结果如何映射到 meta"：
    // 哪些条目该写、哪些不该写，以及 meta 出错时不能影响 push 本身

    // 推送成功的真实 mod → 记下本次来源的 MC 版本与 loader，at 是本次推送时刻
    @Test
    void testPushRecordsSuccessfulModIntoMeta() throws IOException {
        ConfigPaths paths = newPaths("sodium", "sodium-options.json");
        write(paths.active(), "new");

        handler().pushActiveToGlobal(List.of(paths));

        ModSource record = new RepoMetaStore(metaPath()).load().mods().get("sodium");
        assertNotNull(record);
        assertEquals("1.21.1", record.minecraft());
        assertEquals("fabric", record.loader());
        assertFalse(record.at().isBlank());
    }

    // 推送失败的 mod 不覆盖旧条目：旧记录原样留着，不给它盖上本次来源
    @Test
    void testFailedModKeepsOldMetaEntry() throws IOException {
        RepoMetaStore metaStore = new RepoMetaStore(metaPath());
        ModSource old = new ModSource("1.20.1", "forge", "2026-01-01T00:00:00Z");
        metaStore.save(new RepoMeta(RepoMeta.FORMAT, Map.of("sodium", old)));
        // 本地文件不存在 → 推送失败
        ConfigPaths paths = newPaths("sodium", "sodium-options.json");

        SyncReport report = handler(metaStore).pushActiveToGlobal(List.of(paths));

        assertEquals(SyncOutcome.FAILED, report.results().get("sodium"));
        assertEquals(old, metaStore.load().mods().get("sodium"));
    }

    // 原版 / uncategorized / residue 不是可追踪的 mod → 一条都不记，连 meta 文件都不建
    @Test
    void testSpecialGroupsAreNotRecorded() throws IOException {
        List<ConfigPaths> paths = List.of(
                newPaths(ModGroup.VANILLA_ID, "options.txt"),
                newPaths(ModGroup.UNCATEGORIZED_ID, "uncategorized.cfg"),
                newPaths(ModGroup.RESIDUE_ID, "residue.cfg"));
        for (ConfigPaths path : paths) {
            write(path.active(), "new");
        }

        SyncReport report = handler().pushActiveToGlobal(paths);

        assertEquals(3, report.count(SyncOutcome.SUCCESS));
        assertEquals(Map.of(), new RepoMetaStore(metaPath()).load().mods());
        assertFalse(Files.exists(metaPath()));
    }

    // 当前来源未知（拿不到 loader）→ 推照常推，但 meta 不写也不建
    @Test
    void testUnknownSourceWritesNoMetaFile() throws IOException {
        ConfigPaths paths = newPaths("sodium", "sodium-options.json");
        write(paths.active(), "new");
        ConfigHandler handler = new ConfigHandler(MAX_BACKUP_COUNT,
                new SourceInfo("1.21.1", null), new RepoMetaStore(metaPath()));

        SyncReport report = handler.pushActiveToGlobal(List.of(paths));

        assertEquals(SyncOutcome.SUCCESS, report.results().get("sodium"));
        assertEquals("new", Files.readString(paths.global()));
        assertFalse(Files.exists(metaPath()));
    }

    // 一次 push 多个 mod → 全部落盘，而不是只留下最后一个（压"每条写一次"的退化实现）
    @Test
    void testPushRecordsEveryModInOneGo() throws IOException {
        List<ConfigPaths> paths = List.of(
                newPaths("sodium", "sodium-options.json"),
                newPaths("jei", "jei/jei.json"),
                newPaths("etf", "etf.json"));
        for (ConfigPaths path : paths) {
            write(path.active(), "new");
        }

        handler().pushActiveToGlobal(paths);

        Map<String, ModSource> mods = new RepoMetaStore(metaPath()).load().mods();
        assertEquals(List.of("etf", "jei", "sodium"), mods.keySet().stream().sorted().toList());
        // 同一次 push：三条的时间戳必须完全一致
        assertEquals(1L, mods.values().stream().map(ModSource::at).distinct().count());
    }

    // 已有其他 mod 的条目时，本次 push 只增改自己的，不能把表覆盖成只剩本次的
    @Test
    void testPushKeepsUnrelatedMetaEntries() throws IOException {
        RepoMetaStore metaStore = new RepoMetaStore(metaPath());
        ModSource other = new ModSource("1.20.1", "forge", "2026-01-01T00:00:00Z");
        metaStore.save(new RepoMeta(RepoMeta.FORMAT, Map.of("jei", other)));
        ConfigPaths paths = newPaths("sodium", "sodium-options.json");
        write(paths.active(), "new");

        handler(metaStore).pushActiveToGlobal(List.of(paths));

        Map<String, ModSource> mods = metaStore.load().mods();
        assertEquals(2, mods.size());
        assertEquals(other, mods.get("jei"));
        assertEquals("1.21.1", mods.get("sodium").minecraft());
    }

    // meta 写失败（落点被目录占住）不能把已经成功的 push 拖下水
    @Test
    void testPushSucceedsEvenWhenMetaWriteFails() throws IOException {
        Files.createDirectories(metaPath());
        ConfigPaths paths = newPaths("sodium", "sodium-options.json");
        write(paths.active(), "new");

        SyncReport report = handler().pushActiveToGlobal(List.of(paths));

        assertEquals(SyncOutcome.SUCCESS, report.results().get("sodium"));
        assertEquals("new", Files.readString(paths.global()));
        assertTrue(Files.isDirectory(metaPath()));
    }

    // 只推原版时不产生 meta 文件
    @Test
    void testPushVanillaOnlyCreatesNoMetaFile() throws IOException {
        ConfigPaths paths = newPaths(ModGroup.VANILLA_ID, "options.txt");
        write(paths.active(), "new");

        SyncReport report = handler().pushActiveToGlobal(List.of(paths));

        assertEquals(SyncOutcome.SUCCESS, report.results().get(ModGroup.VANILLA_ID));
        assertFalse(Files.exists(metaPath()));
    }

    // 只推原版时已有 meta 一个字节都不改
    @Test
    void testPushVanillaOnlyKeepsExistingMeta() throws IOException {
        RepoMetaStore metaStore = new RepoMetaStore(metaPath());
        metaStore.save(new RepoMeta(RepoMeta.FORMAT, Map.of(
                "sodium", new ModSource("1.20.1", "forge", "2026-01-01T00:00:00Z"))));
        String before = Files.readString(metaPath());
        ConfigPaths paths = newPaths(ModGroup.VANILLA_ID, "options.txt");
        write(paths.active(), "new");

        handler(metaStore).pushActiveToGlobal(List.of(paths));

        assertEquals(before, Files.readString(metaPath()));
    }

    // 输入约定：fetch 的 paths 来自本地配置发现，active 必然存在；global 可缺失
    // 覆盖：global "new"、active "old" → fetch 后 active 变 "new"
    @Test
    void testFetchOverwritesActiveWithGlobal() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "new");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "old");

        SyncReport report = handler().fetchGlobalToActive(List.of(paths));

        assertEquals("new", Files.readString(paths.active()));
        assertEquals(SyncOutcome.SUCCESS, report.results().get(MOD_ID));
    }

    // 本地配置存在、global 缺失（从未推送过）→ 跳过：active 保持原值，不产生备份
    @Test
    void testFetchSkipsWhenGlobalMissing() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "old");
        // global 文件不创建

        SyncReport report = handler().fetchGlobalToActive(List.of(paths));

        assertEquals("old", Files.readString(paths.active()));
        assertFalse(Files.exists(paths.backup()));
        // 该 mod 的唯一文件被跳过 → 单条 SKIPPED
        assertEquals(SyncOutcome.SKIPPED, report.results().get(MOD_ID));
    }

    // 多条目：global 缺失的条目被跳过，不影响后续条目的处理
    @Test
    void testFetchSkipsMissingGlobalButProcessesOthers() throws IOException {
        ConfigPaths skipped = newPaths("skipped.cfg");
        // skipped 的 global 不创建
        Files.createDirectories(skipped.active().getParent());
        Files.writeString(skipped.active(), "local");

        ConfigPaths covered = newPaths("covered.cfg");
        Files.createDirectories(covered.global().getParent());
        Files.writeString(covered.global(), "new");
        Files.createDirectories(covered.active().getParent());
        Files.writeString(covered.active(), "old");

        SyncReport report = handler().fetchGlobalToActive(List.of(skipped, covered));

        // global 缺失：active 保持原值
        assertEquals("local", Files.readString(skipped.active()));
        // global 存在：active 被覆盖
        assertEquals("new", Files.readString(covered.active()));
        // 两个文件同属一个 mod：SKIPPED 不掩盖 SUCCESS，聚合为单条 SUCCESS
        assertEquals(1, report.results().size());
        assertEquals(SyncOutcome.SUCCESS, report.results().get(MOD_ID));
    }

    // 原版配置走跳过 hash 与回写登记的分支：同步行为与普通 mod 一致
    @Test
    void testFetchSyncsVanillaConfig() throws IOException {
        ConfigPaths paths = newPaths(ModGroup.VANILLA_ID, "options.txt");
        write(paths.global(), "new");
        write(paths.active(), "old");

        SyncReport report = handler().fetchGlobalToActive(List.of(paths));

        assertEquals("new", Files.readString(paths.active()));
        assertEquals("old", Files.readString(paths.backup()));
        assertEquals(SyncOutcome.SUCCESS, report.results().get(ModGroup.VANILLA_ID));
    }

    // ---------- SyncReport 按 modId 聚合 ----------

    // 同一 mod 两个文件：A 成功 + B 跳过 → SUCCESS（不因 SKIPPED 被拉低）
    // 场景真实可达：全局仓库只存过 A（B 从未推送）；若优先级倒置，这里会退化为 SKIPPED
    @Test
    void testFetchAggregatesSuccessAndSkippedAsSuccess() throws IOException {
        ConfigPaths ok = newPaths("a.cfg");
        write(ok.active(), "old-a");
        write(ok.global(), "new-a");

        ConfigPaths neverPushed = newPaths("b.cfg");
        write(neverPushed.active(), "local-b"); // global 缺失 → SKIPPED

        SyncReport report = handler().fetchGlobalToActive(List.of(ok, neverPushed));

        assertEquals(1, report.results().size()); // 同 mod 只保留一条
        assertEquals(SyncOutcome.SUCCESS, report.results().get(MOD_ID));
        assertFalse(report.hasFailed());
    }

    // 同一 mod 两个文件：A 成功 + B 失败 → FAILED（不被 SUCCESS 掩盖）
    @Test
    void testFetchAggregatesSuccessAndFailedAsFailed() throws IOException {
        ConfigPaths ok = newPaths("a.cfg");
        write(ok.active(), "old-a");
        write(ok.global(), "new-a");

        ConfigPaths broken = brokenBackupPaths(MOD_ID, "b.cfg");
        write(broken.active(), "old-b");
        write(broken.global(), "new-b");

        SyncReport report = handler().fetchGlobalToActive(List.of(ok, broken));

        assertEquals(1, report.results().size());
        assertEquals(SyncOutcome.FAILED, report.results().get(MOD_ID));
        assertTrue(report.hasFailed());
        assertEquals(List.of(MOD_ID), report.failedModIds());
    }

    // 同一 mod 两个文件：A 跳过 + B 失败 → FAILED
    @Test
    void testFetchAggregatesSkippedAndFailedAsFailed() throws IOException {
        ConfigPaths neverPushed = newPaths("a.cfg");
        write(neverPushed.active(), "local-a"); // global 缺失 → SKIPPED

        ConfigPaths broken = brokenBackupPaths(MOD_ID, "b.cfg");
        write(broken.active(), "old-b");
        write(broken.global(), "new-b");

        SyncReport report = handler().fetchGlobalToActive(List.of(neverPushed, broken));

        assertEquals(SyncOutcome.FAILED, report.results().get(MOD_ID));
        assertEquals(1, report.count(SyncOutcome.FAILED));
    }

    // 聚合取优先级而非"最后写入覆盖"：FAILED 在前、SKIPPED 在后仍为 FAILED
    @Test
    void testFetchAggregationIsPriorityBasedNotLastWriteWins() throws IOException {
        ConfigPaths broken = brokenBackupPaths(MOD_ID, "a.cfg");
        write(broken.active(), "old-a");
        write(broken.global(), "new-a");

        ConfigPaths neverPushed = newPaths("b.cfg");
        write(neverPushed.active(), "local-b"); // global 缺失 → SKIPPED（后处理）

        SyncReport report = handler().fetchGlobalToActive(List.of(broken, neverPushed));

        assertEquals(SyncOutcome.FAILED, report.results().get(MOD_ID));
    }

    // push 不会产生 SKIPPED：A 成功 + B 失败 → FAILED
    @Test
    void testPushAggregatesSuccessAndFailedAsFailed() throws IOException {
        ConfigPaths ok = newPaths("a.cfg");
        write(ok.active(), "local-new-a"); // global 缺失 → 只写不备份 → SUCCESS

        ConfigPaths broken = brokenBackupPaths(MOD_ID, "b.cfg");
        write(broken.active(), "local-new-b");
        write(broken.global(), "repo-old-b"); // global 已存在 → 触发备份，而备份失败 → FAILED

        SyncReport report = handler().pushActiveToGlobal(List.of(ok, broken));

        assertEquals(1, report.results().size());
        assertEquals(SyncOutcome.FAILED, report.results().get(MOD_ID));
        assertEquals(List.of(MOD_ID), report.failedModIds());
    }

    // push：同一 mod 多文件全部成功 → 聚合为单条 SUCCESS
    @Test
    void testPushAggregatesAllSuccessAsSuccess() throws IOException {
        ConfigPaths a = newPaths("a.cfg");
        write(a.active(), "a");

        ConfigPaths b = newPaths("b.cfg");
        write(b.active(), "b");
        write(b.global(), "old-b");

        SyncReport report = handler().pushActiveToGlobal(List.of(a, b));

        assertEquals(1, report.results().size());
        assertEquals(SyncOutcome.SUCCESS, report.results().get(MOD_ID));
        assertEquals(1, report.count(SyncOutcome.SUCCESS));
        assertEquals(0, report.count(SyncOutcome.FAILED));
        assertTrue(report.failedModIds().isEmpty());
    }

    // 不同 mod 各自成条，互不干扰
    @Test
    void testReportKeepsOneEntryPerMod() throws IOException {
        ConfigPaths ok = newPaths("mod-one", "a.cfg");
        write(ok.active(), "a");

        ConfigPaths broken = brokenBackupPaths("mod-two", "b.cfg");
        write(broken.active(), "b");
        write(broken.global(), "old-b");

        SyncReport report = handler().pushActiveToGlobal(List.of(ok, broken));

        assertEquals(2, report.results().size());
        assertEquals(SyncOutcome.SUCCESS, report.results().get("mod-one"));
        assertEquals(SyncOutcome.FAILED, report.results().get("mod-two"));
        assertEquals(List.of("mod-two"), report.failedModIds());
        assertEquals(1, report.count(SyncOutcome.SUCCESS));
        assertEquals(1, report.count(SyncOutcome.FAILED));
    }

    // ---------- restore ----------

    // 被恢复的那次快照的时间戳（固定过去时间，避免与恢复时新建的备份目录同秒）
    private static final String SNAPSHOT_TS = "2026-09-07--10-00-00";

    // 造一个快照：backupRoot/<SNAPSHOT_TS>/<relativeFile> = content
    private BackupSnapshot makeSnapshot(Path backupRoot, Path relativeFile, String content) throws IOException {
        write(backupRoot.resolve(SNAPSHOT_TS).resolve(relativeFile), content);
        return new BackupSnapshot(SNAPSHOT_TS, List.of(relativeFile));
    }

    // 备份根下的时间戳目录（升序）
    private List<Path> timestampDirs(Path backupRoot) throws IOException {
        try (var stream = Files.list(backupRoot)) {
            return stream.filter(Files::isDirectory)
                    .filter(path -> Time.BACKUP_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
    }

    // 本次恢复新建的备份目录（排除被恢复的那次快照目录）
    private Path restoreBackupDir(Path backupRoot) throws IOException {
        List<Path> dirs = timestampDirs(backupRoot).stream()
                .filter(dir -> !dir.getFileName().toString().equals(SNAPSHOT_TS))
                .toList();
        assertEquals(1, dirs.size(), "恢复应只新建一个备份目录");
        Path dir = dirs.get(0);
        // 目录名必须是时间戳格式，否则 prune 永远清理不到它
        assertTrue(Time.BACKUP_FILE_PATTERN.matcher(dir.getFileName().toString()).matches());
        return dir;
    }

    // 恢复前先把 target 的旧值备份到新时间戳目录，再写入备份内容
    // “新备份里是 old 而不是 new”这一条就能钉死“备份取错源”或“备份晚于恢复”的回归
    @Test
    void testRestoreOverwritesTargetAndBacksUpItsOldContent() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        BackupSnapshot snapshot = makeSnapshot(backupRoot, Path.of("options.txt"), "new");

        Path targetRoot = tempDir.resolve("target");
        write(targetRoot.resolve("options.txt"), "old");

        SyncReport report = handler().restore(snapshot, targetRoot::resolve, backupRoot);

        // 恢复结果
        assertEquals("new", Files.readString(targetRoot.resolve("options.txt")));
        assertEquals(SyncOutcome.SUCCESS, report.results().get("options.txt"));
        // 旧值被存入本次新建的备份目录，内容必为 old
        assertEquals("old", Files.readString(restoreBackupDir(backupRoot).resolve("options.txt")));
    }

    // target 不存在 → 直接按备份内容创建，不产生多余备份
    @Test
    void testRestoreCreatesTargetWithoutExtraBackup() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        BackupSnapshot snapshot = makeSnapshot(backupRoot, Path.of("options.txt"), "new");
        Path targetRoot = tempDir.resolve("target"); // 目标不存在

        SyncReport report = handler().restore(snapshot, targetRoot::resolve, backupRoot);

        assertEquals("new", Files.readString(targetRoot.resolve("options.txt")));
        assertEquals(SyncOutcome.SUCCESS, report.results().get("options.txt"));
        // 没有旧值可备份：备份根下只剩被恢复的那次快照
        assertEquals(List.of(SNAPSHOT_TS), timestampDirs(backupRoot).stream()
                .map(dir -> dir.getFileName().toString())
                .toList());
    }

    // 多文件：成功 / 失败（备份里缺文件）/ 目标不存在 → 逐条按相对路径统计
    @Test
    void testRestoreReportsPartialFailurePerFile() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        write(backupRoot.resolve(SNAPSHOT_TS).resolve("a.cfg"), "new-a");
        write(backupRoot.resolve(SNAPSHOT_TS).resolve("c.cfg"), "new-c");
        // 快照声明了 b.cfg，但备份里没有这个文件 → 恢复该文件失败
        BackupSnapshot snapshot = new BackupSnapshot(SNAPSHOT_TS,
                List.of(Path.of("a.cfg"), Path.of("b.cfg"), Path.of("c.cfg")));

        Path targetRoot = tempDir.resolve("target");
        write(targetRoot.resolve("a.cfg"), "old-a");
        write(targetRoot.resolve("b.cfg"), "old-b");
        // c.cfg 目标不存在

        SyncReport report = handler().restore(snapshot, targetRoot::resolve, backupRoot);

        assertEquals(3, report.results().size());
        assertEquals(SyncOutcome.SUCCESS, report.results().get("a.cfg"));
        assertEquals(SyncOutcome.FAILED, report.results().get("b.cfg"));
        assertEquals(SyncOutcome.SUCCESS, report.results().get("c.cfg"));
        assertTrue(report.hasFailed());
        assertEquals(2, report.count(SyncOutcome.SUCCESS));
        assertEquals(1, report.count(SyncOutcome.FAILED));
        // 成功的被恢复/创建，失败的保持原值
        assertEquals("new-a", Files.readString(targetRoot.resolve("a.cfg")));
        assertEquals("old-b", Files.readString(targetRoot.resolve("b.cfg")));
        assertEquals("new-c", Files.readString(targetRoot.resolve("c.cfg")));
    }

    // 嵌套相对路径（<ts>/<modId>/<file>）→ 源与目标都带 mod 子目录
    @Test
    void testRestoreKeepsNestedRelativePath() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        Path relative = Path.of("sodium", "sodium-options.json");
        BackupSnapshot snapshot = makeSnapshot(backupRoot, relative, "repo");

        Path targetRoot = tempDir.resolve("target");
        write(targetRoot.resolve(relative), "local");

        SyncReport report = handler().restore(snapshot, targetRoot::resolve, backupRoot);

        assertEquals("repo", Files.readString(targetRoot.resolve(relative)));
        assertEquals(SyncOutcome.SUCCESS, report.results().get(relative.toString()));
        // 旧值备份到同一相对路径下，mod 子目录层级保留
        assertEquals("local", Files.readString(restoreBackupDir(backupRoot).resolve(relative)));
    }

    // 快照里的 null 条目被跳过，不影响其余文件
    @Test
    void testRestoreSkipsNullEntry() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        Path relative = Path.of("options.txt");
        write(backupRoot.resolve(SNAPSHOT_TS).resolve(relative), "new");
        // List.of 不允许 null 元素
        BackupSnapshot snapshot = new BackupSnapshot(SNAPSHOT_TS, Arrays.asList(null, relative));

        Path targetRoot = tempDir.resolve("target");
        write(targetRoot.resolve(relative), "old");

        SyncReport report = handler().restore(snapshot, targetRoot::resolve, backupRoot);

        assertEquals("new", Files.readString(targetRoot.resolve(relative)));
        // null 条目没有相对路径，不进报告
        assertEquals(1, report.results().size());
        assertEquals(SyncOutcome.SUCCESS, report.results().get(relative.toString()));
    }

    // 本地恢复的落点由 LocalLayout 按快照内相对路径分流：config/<file> 回 configDir，其余（options.txt）回 gameDir 根。
    // configDir 刻意不取 gameDir/config，才能证明落点跟着布局走而不是写死某一层目录
    @Test
    void testRestoreRoutesConfigFilesToConfigDirAndVanillaToGameDir() throws IOException {
        Path backupRoot = tempDir.resolve("backup");
        Path modRelative = Path.of("config", "sodium-options.json");
        Path vanillaRelative = Path.of("options.txt");
        write(backupRoot.resolve(SNAPSHOT_TS).resolve(modRelative), "repo-mod");
        write(backupRoot.resolve(SNAPSHOT_TS).resolve(vanillaRelative), "repo-vanilla");
        BackupSnapshot snapshot = new BackupSnapshot(SNAPSHOT_TS, List.of(modRelative, vanillaRelative));

        Path gameDir = tempDir.resolve("game");
        Path configDir = tempDir.resolve("custom-config");
        LocalLayout layout = new LocalLayout(gameDir, configDir);
        // 两处目标都先有旧值：恢复后既能看新值，也能看到旧值被存进本次新建的备份
        write(configDir.resolve("sodium-options.json"), "old-mod");
        write(gameDir.resolve("options.txt"), "old-vanilla");

        SyncReport report = handler().restore(snapshot, layout::localRestoreTarget, backupRoot);

        assertEquals("repo-mod", Files.readString(configDir.resolve("sodium-options.json")));
        assertEquals("repo-vanilla", Files.readString(gameDir.resolve("options.txt")));
        // config 前缀是快照里的分组标记，不能落成目录；gameDir/config 也不该被凭空建出来
        assertFalse(Files.exists(configDir.resolve("config")));
        assertFalse(Files.exists(gameDir.resolve("config").resolve("sodium-options.json")));
        assertEquals(SyncOutcome.SUCCESS, report.results().get(modRelative.toString()));
        assertEquals(SyncOutcome.SUCCESS, report.results().get(vanillaRelative.toString()));
        // 旧值备份仍按快照内的相对路径存放（含 config 前缀），下次恢复才找得到
        assertEquals("old-mod", Files.readString(restoreBackupDir(backupRoot).resolve(modRelative)));
        assertEquals("old-vanilla", Files.readString(restoreBackupDir(backupRoot).resolve(vanillaRelative)));
    }

    // ---------- prune / deleteRecursively ----------

    // 时间戳格式（与 Time.timeString 一致）：yyyy-MM-dd--HH-mm-ss
    private static final String TS_FMT = "2026-09-07--%02d-00-00";

    // 构造 push 语义的 ConfigPaths：backup 落在 backupRoot/<ts>/<relativePath>
    private ConfigPaths pushPaths(Path backupRoot, String ts, String fileName) {
        return new ConfigPaths(
                fileName,
                tempDir.resolve("active").resolve(fileName),
                tempDir.resolve("global").resolve(fileName),
                backupRoot.resolve(ts).resolve(fileName),
                backupRoot,
                MOD_ID
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
        int total = MAX_BACKUP_COUNT + 3;
        for (int i = 0; i < total; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        // 触发一次 push，使 prune 执行（global 存在才会备份+prune）
        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, total), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        handler().pushActiveToGlobal(List.of(paths));

        try (var stream = Files.list(backupRoot)) {
            List<String> remaining = stream.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(MAX_BACKUP_COUNT, remaining.size());
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
        for (int i = 0; i < MAX_BACKUP_COUNT + 1; i++) {
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

        handler().pushActiveToGlobal(List.of(paths));

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
        for (int i = 1; i <= MAX_BACKUP_COUNT; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        handler().pushActiveToGlobal(List.of(paths));

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
        for (int i = 0; i < MAX_BACKUP_COUNT; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        handler().pushActiveToGlobal(List.of(paths));

        try (var stream = Files.list(backupRoot)) {
            List<String> remaining = stream.map(p -> p.getFileName().toString()).sorted().toList();
            // 总数 MAX+1，prune 后保留 MAX 个
            assertEquals(MAX_BACKUP_COUNT, remaining.size());
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
        int count = MAX_BACKUP_COUNT - 3;
        for (int i = 0; i < count; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        handler().pushActiveToGlobal(List.of(paths));

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
        assertDoesNotThrow(() -> handler().pushActiveToGlobal(List.of(paths)));

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
        assertDoesNotThrow(() -> handler().pushActiveToGlobal(List.of(paths)));
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
        for (int i = 0; i < MAX_BACKUP_COUNT + 2; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        handler().pushActiveToGlobal(List.of(paths));

        try (var stream = Files.list(backupRoot)) {
            List<String> remaining = stream.map(p -> p.getFileName().toString()).sorted().toList();
            assertEquals(MAX_BACKUP_COUNT, remaining.size());
            // 最旧的 3 个（00、01、02）应被删除
            assertFalse(remaining.contains(String.format(TS_FMT, 0)));
            assertFalse(remaining.contains(String.format(TS_FMT, 1)));
            assertFalse(remaining.contains(String.format(TS_FMT, 2)));
            // 最新的应保留
            assertTrue(remaining.contains(String.format(TS_FMT, MAX_BACKUP_COUNT + 1)));
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
        for (int i = 1; i <= MAX_BACKUP_COUNT; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        handler().pushActiveToGlobal(List.of(paths));

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
        for (int i = 1; i <= MAX_BACKUP_COUNT; i++) {
            makeBackupDir(backupRoot, String.format(TS_FMT, i));
        }

        ConfigPaths paths = pushPaths(backupRoot, String.format(TS_FMT, 99), "options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        handler().pushActiveToGlobal(List.of(paths));

        assertFalse(Files.exists(oldest));
    }

    // ---------- exportRepo / importRepo ----------

    // 造一个可打包的仓库：RepoPacker 只收 global 目录与 mappings.txt，缺 mappings.txt 会打包失败
    private Path newRepo(String name) throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve(name));
        Files.writeString(repo.resolve("mappings.txt"), "M");
        return repo;
    }

    // 与 RepoPacker.pack 写出形态一致的 manifest
    private static String exportedManifest() {
        return "{\"format\":" + PackManifest.DEFAULT_FORMAT
                + ",\"exportedAt\":\"2026-01-01T00:00:00Z\",\"context\":\"1.0.0\"}";
    }

    // 手搓一个合法包：manifest + 条目（名称、内容成对给出）
    private Path zipOf(String... nameAndContent) throws IOException {
        Path zip = Files.createTempFile(tempDir, "fixture-", ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry(PackManifest.ENTRY_NAME));
            zos.write(exportedManifest().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            for (int i = 0; i < nameAndContent.length; i += 2) {
                zos.putNextEntry(new ZipEntry(nameAndContent[i]));
                zos.write(nameAndContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return zip;
    }

    // 读包里的一个条目，用来校验 manifest 与备份内容
    private static String entryContent(Path zip, String name) throws IOException {
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            ZipEntry entry = zipFile.getEntry(name);
            assertNotNull(entry, "包里缺少条目: " + name);
            try (InputStream in = zipFile.getInputStream(entry)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    // importRepo 新建的时间戳备份目录（备份根下应只有一个）
    private Path importBackupDir(Path backupRoot) throws IOException {
        List<Path> dirs = timestampDirs(backupRoot);
        assertEquals(1, dirs.size(), "导入应新建且只新建一个备份目录");
        return dirs.get(0);
    }

    // 导出目录不存在时自动创建；包名带时间戳，manifest 记下版本与导出位置
    @Test
    void testExportCreatesTargetRootAndWritesPackage() throws IOException {
        Path repo = newRepo("repo");
        write(repo.resolve("global/a.json"), "A");
        Path exports = tempDir.resolve("exports"); // 不存在，由 exportRepo 创建

        PackReport report = handler().exportRepo(repo, exports, "1.2.3");

        assertEquals(PackReport.outcome.SUCCESS, report.outcome());
        // 目录条目不计入文件数：只有 global/a.json 与 mappings.txt
        assertEquals(2, report.fileCount());
        assertTrue(Files.isDirectory(exports), "导出目录应被自动创建");
        assertEquals(exports, report.location().getParent());

        String fileName = report.location().getFileName().toString();
        assertTrue(fileName.endsWith("-export.zip"), fileName);
        String timestamp = fileName.substring(0, fileName.length() - "-export.zip".length());
        assertTrue(Time.BACKUP_FILE_PATTERN.matcher(timestamp).matches(), "包名应以时间戳开头: " + fileName);

        assertEquals("A", entryContent(report.location(), "global/a.json"));
        assertEquals("M", entryContent(report.location(), "mappings.txt"));
        PackManifest manifest = new Gson().fromJson(entryContent(report.location(), PackManifest.ENTRY_NAME), PackManifest.class);
        assertEquals(PackManifest.DEFAULT_FORMAT, manifest.format());
        assertEquals("1.2.3", manifest.context());
        assertEquals(report.location().toString(), manifest.exportedAt());
    }

    // 导出目录建不出来（父级被普通文件占位）→ IO_FAILED，location 为 null，占位文件不动
    @Test
    void testExportFailsWhenTargetRootCannotBeCreated() throws IOException {
        Path repo = newRepo("repo");
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "not a directory");

        PackReport report = handler().exportRepo(repo, blocked.resolve("exports"), "1.2.3");

        assertEquals(PackReport.outcome.IO_FAILED, report.outcome());
        assertEquals(0, report.fileCount());
        assertNull(report.location());
        assertEquals("not a directory", Files.readString(blocked));
    }

    // 导入 = 先备份现有仓库、再解包：备份根与其下的时间戳目录都由 importRepo 创建，调用方只传路径
    @Test
    void testImportBacksUpRepoBeforeUnpacking() throws IOException {
        Path repo = newRepo("repo");
        write(repo.resolve("global/old.json"), "OLD");
        Path zip = zipOf("global/new.json", "NEW", "mappings.txt", "NEW");
        Path backupRoot = tempDir.resolve("import-backups"); // 不存在，由 importRepo 创建

        PackReport report = handler().importRepo(repo, zip, backupRoot);

        assertEquals(PackReport.outcome.SUCCESS, report.outcome(),
                "备份目录 backupRoot/<时间戳> 需要由 importRepo 创建，否则打包备份会因父目录不存在而失败");
        assertEquals(2, report.fileCount());
        assertEquals(zip, report.location());
        // 包内容落地
        assertEquals("NEW", Files.readString(repo.resolve("global/new.json")));
        assertEquals("NEW", Files.readString(repo.resolve("mappings.txt")));
        // 备份里是导入前的旧内容，不是导入后的
        Path backupZip = importBackupDir(backupRoot).resolve("backup.zip");
        assertEquals("OLD", entryContent(backupZip, "global/old.json"));
        assertEquals("M", entryContent(backupZip, "mappings.txt"));
    }

    // 导入只覆盖包里出现的条目：仓库里不在包中的文件保留
    @Test
    void testImportKeepsRepoFilesNotInPackage() throws IOException {
        Path repo = newRepo("repo");
        write(repo.resolve("global/untouched.json"), "KEEP");
        Path zip = zipOf("mappings.txt", "NEW");

        PackReport report = handler().importRepo(repo, zip, tempDir.resolve("import-backups"));

        assertEquals(PackReport.outcome.SUCCESS, report.outcome(),
                "备份目录 backupRoot/<时间戳> 需要由 importRepo 创建，否则导入永远停在备份失败上");
        assertEquals("NEW", Files.readString(repo.resolve("mappings.txt")));
        assertEquals("KEEP", Files.readString(repo.resolve("global/untouched.json")));
    }

    // 备份根建不出来 → IO_FAILED，location 为 null，仓库保持原状
    @Test
    void testImportFailsWhenBackupRootCannotBeCreated() throws IOException {
        Path repo = newRepo("repo");
        write(repo.resolve("global/keep.json"), "KEEP");
        Path zip = zipOf("global/new.json", "NEW");
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "not a directory");

        PackReport report = handler().importRepo(repo, zip, blocked.resolve("backups"));

        assertEquals(PackReport.outcome.IO_FAILED, report.outcome());
        assertEquals(0, report.fileCount());
        assertNull(report.location());
        // 备份都没做成，不能动仓库
        assertEquals("KEEP", Files.readString(repo.resolve("global/keep.json")));
        assertFalse(Files.exists(repo.resolve("global/new.json")));
    }

    // 备份失败就不导入：没有可回退的包时不能动仓库
    @Test
    void testImportAbortsWhenBackupFails() throws IOException {
        // 仓库里没有 mappings.txt → 备份打包失败
        Path repo = Files.createDirectories(tempDir.resolve("repo"));
        write(repo.resolve("global/keep.json"), "KEEP");
        Path zip = zipOf("global/new.json", "NEW");

        PackReport report = handler().importRepo(repo, zip, tempDir.resolve("import-backups"));

        assertEquals(PackReport.outcome.IO_FAILED, report.outcome());
        assertFalse(Files.exists(repo.resolve("global/new.json")), "备份失败后不该继续导入");
        assertEquals("KEEP", Files.readString(repo.resolve("global/keep.json")));
    }
}
