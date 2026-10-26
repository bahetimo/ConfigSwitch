package com.bteamore.configswitch.config;

import com.bteamore.configswitch.repo.RepoPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SettingsStoreTest {
    @TempDir
    Path tempDir;

    // 配置文件落在临时目录里，避免碰到真实的 .minecraft 下的配置
    private Path configPath;

    private static final String CUSTOM_REPO_ROOT = "D:/custom-repo";
    private static final String DEFAULT_REPO_ROOT = RepoPaths.root().toString();
    private static final int DEFAULT_MAX_BACKUP_COUNT = 10;

    // 手写一份"用户改过"的配置，用来喂给 store
    private static final String CUSTOM_JSON = "{\"repoRoot\":\"D:/custom-repo\",\"maxBackupCount\":5}";

    @BeforeEach
    void useTempConfigPath() {
        configPath = tempDir.resolve("configswitch-config.json");
    }

    // store 不持有设置状态，用例现造一个指向临时目录的实例（生产实例由 ModConfig 持有）
    private SettingsStore newStore() {
        return new SettingsStore(configPath);
    }

    private static ModSettings defaults() {
        return new ModSettings(DEFAULT_REPO_ROOT, DEFAULT_MAX_BACKUP_COUNT);
    }

    private static ModSettings custom() {
        return new ModSettings(CUSTOM_REPO_ROOT, 5);
    }

    // load()/save() 都不该抛异常，统一包一层，顺便规避 assertDoesNotThrow 的重载歧义
    private static ModSettings load(SettingsStore store) {
        return assertDoesNotThrow((ThrowingSupplier<ModSettings>) store::load);
    }

    private static boolean save(SettingsStore store, ModSettings settings) {
        return assertDoesNotThrow((ThrowingSupplier<Boolean>) () -> store.save(settings));
    }

    // ---------- round-trip ----------

    // save(custom) → load() 拿回同样的字段
    @Test
    void testSaveThenLoadKeepsFields() {
        assertTrue(save(newStore(), custom()));

        ModSettings loaded = load(newStore());

        assertEquals(CUSTOM_REPO_ROOT, loaded.repoRoot());
        assertEquals(5, loaded.maxBackupCount());
    }

    // 手写成文件的自定义设置也能读回来（不经过 save）
    @Test
    void testLoadReadsExistingFile() throws IOException {
        Files.writeString(configPath, CUSTOM_JSON);

        assertEquals(custom(), load(newStore()));
    }

    // ---------- 无状态：每次调用都以当前文件/入参为准 ----------

    // 同一个实例上先写 custom 再整体覆盖成 defaults → 再读必须是 defaults
    @Test
    void testStoreKeepsNoSettingsBetweenCalls() {
        SettingsStore store = newStore();
        assertTrue(save(store, custom()));
        assertEquals(custom(), load(store));

        assertTrue(save(store, defaults()));

        assertEquals(defaults(), load(store));
    }

    // 文件被删掉后 load() 不保留上次读到的值，直接落回默认值
    @Test
    void testLoadDoesNotRememberPreviousValue() throws IOException {
        SettingsStore store = newStore();
        assertTrue(save(store, custom()));
        assertEquals(custom(), load(store));

        Files.delete(configPath);

        assertEquals(defaults(), load(store));
    }

    // 同一份设置在两个实例上各写一次，文件内容逐字节一致
    @Test
    void testSaveIsStableAcrossInstances() throws IOException {
        assertTrue(save(newStore(), custom()));
        String firstJson = Files.readString(configPath);

        assertTrue(save(newStore(), custom()));

        assertEquals(firstJson, Files.readString(configPath));
    }

    // save 会建出缺失的父目录
    @Test
    void testSaveCreatesParentDirs() {
        Path nested = tempDir.resolve("nested").resolve("deep").resolve("configswitch-config.json");

        assertTrue(save(new SettingsStore(nested), custom()));

        assertEquals(custom(), load(new SettingsStore(nested)));
    }

    // 二次 save 覆盖旧内容：文件里不残留上一次的字段值
    @Test
    void testSaveOverwritesPreviousContent() throws IOException {
        SettingsStore store = newStore();
        assertTrue(save(store, custom()));
        assertTrue(save(store, defaults()));

        String json = Files.readString(configPath);
        assertFalse(json.contains(CUSTOM_REPO_ROOT), json);
        assertEquals(defaults(), load(store));
    }

    // 写盘 JSON 里有两个字段；默认仓库根的反斜杠在 JSON 中被转义
    @Test
    void testSaveWritesBothFieldsEscapingWindowsPath() throws IOException {
        assertTrue(save(newStore(), defaults()));

        String json = Files.readString(configPath);
        assertTrue(json.contains("\"repoRoot\""), json);
        assertTrue(json.contains("\"maxBackupCount\""), json);
        assertTrue(json.contains(DEFAULT_REPO_ROOT.replace("\\", "\\\\")), json);
    }

    // ---------- 容错：文件不存在 / 空 / 损坏 / 同名目录 ----------

    // 文件不存在 → 落回默认值（首次启动的常态）
    @Test
    void testMissingFileUsesDefaults() {
        assertEquals(defaults(), load(new SettingsStore(tempDir.resolve("no-such-config.json"))));
    }

    // 空文件 → 落回默认值
    @Test
    void testEmptyFileUsesDefaults() throws IOException {
        Files.createFile(configPath);

        assertEquals(defaults(), load(newStore()));
    }

    // 损坏的 JSON / JSON null / 结构不对 → 落回默认值，不抛异常
    @Test
    void testCorruptJsonUsesDefaultsWithoutThrowing() throws IOException {
        for (String corrupt : List.of("{ not json", "[1,2,3]", "null", "\"text\"")) {
            Files.writeString(configPath, corrupt);

            assertEquals(defaults(), load(newStore()), "应落回默认值: " + corrupt);
        }
    }

    // 落点是个同名目录 → 落回默认值，不抛异常
    @Test
    void testDirectoryInsteadOfFileUsesDefaults() throws IOException {
        Files.createDirectory(configPath);

        assertEquals(defaults(), load(newStore()));
    }

    // 文件里的空仓库根 / 非法备份数被归一化（record 规范化构造器）
    @Test
    void testOutOfRangeValuesFromFileAreNormalized() throws IOException {
        Files.writeString(configPath, "{\"repoRoot\":\"  \",\"maxBackupCount\":-5}");

        assertEquals(defaults(), load(newStore()));
    }

    // 落点父级被普通文件占位 → save 返回 false，不抛异常
    @Test
    void testSaveFailureReturnsFalseWithoutThrowing() throws IOException {
        Path blocked = tempDir.resolve("blocked");
        Files.writeString(blocked, "not a directory");

        assertFalse(save(new SettingsStore(blocked.resolve("configswitch-config.json")), custom()));
    }
}
