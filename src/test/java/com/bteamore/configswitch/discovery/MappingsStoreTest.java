package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MappingsStoreTest {
    @TempDir
    Path tempDir;

    // 真实落点是 %APPDATA%\.minecraft\configswitch\mappings.txt，测试一律落在 @TempDir
    private Path mappingPath() {
        return tempDir.resolve("configswitch/mappings.txt");
    }

    private MappingsStore store() {
        return new MappingsStore(mappingPath());
    }

    private void writeFile(String content) throws IOException {
        Files.createDirectories(mappingPath().getParent());
        Files.writeString(mappingPath(), content);
    }

    // 文件里某个 key 占了几行（容忍 "key = value" 这种带空格的写法）
    private long countKeyLines(String key) throws IOException {
        return Files.readAllLines(mappingPath()).stream()
                .map(line -> line.replace(" ", ""))
                .filter(line -> line.startsWith(key + "="))
                .count();
    }

    // ---------- ① 首次注入 ----------

    // 首次注入 → 文件里出现种子（读文件本身，不看返回值）
    @Test
    void testFirstInjectWritesSeedIntoFile() throws IOException {
        MappingsStore store = store();

        MappingTable table = store.injectSeed(MappingTable.loadBundled().entries());

        assertTrue(Files.exists(mappingPath()));
        assertTrue(Files.readString(mappingPath()).contains("yacl=yet_another_config_lib_v3"));
        assertEquals(MappingTable.loadBundled().size(), table.size());
    }

    // ---------- ② 重复注入 ----------

    // 注入两次同一个种子 → yacl 只该出现一次，条目数不长胖
    @Test
    void testRepeatedInjectDoesNotDuplicate() throws IOException {
        MappingsStore store = store();
        Map<String, String> seed = MappingTable.loadBundled().entries();

        store.injectSeed(seed);
        MappingTable table = store.injectSeed(seed);

        assertEquals(1L, countKeyLines("yacl"));
        assertEquals(MappingTable.loadBundled().size(), table.size());
    }

    // 文件里已有条目时：用户的值优先，只补缺失的键
    @Test
    void testExistingEntryWinsOverSeed() throws IOException {
        writeFile("yacl=custom_value\n");
        MappingsStore store = store();

        MappingTable table = store.injectSeed(Map.of(
                "yacl", "yet_another_config_lib_v3",
                "etf", "entity_texture_features"));

        assertEquals(Optional.of("custom_value"), table.lookup("yacl"));            // 已有条目不覆盖
        assertEquals(Optional.of("entity_texture_features"), table.lookup("etf"));  // 缺失的补上
        assertEquals(1L, countKeyLines("yacl"));
    }

    // ---------- ③ 没有缺失时不写盘 ----------

    // 第二次注入没有缺失 → 不写盘：内容与 mtime 都不该变
    @Test
    void testSecondInjectLeavesFileUntouched() throws IOException {
        MappingsStore store = store();
        Map<String, String> seed = MappingTable.loadBundled().entries();
        store.injectSeed(seed);
        String contentAfterFirstInject = Files.readString(mappingPath());
        FileTime afterFirstInject = Files.getLastModifiedTime(mappingPath());

        store.injectSeed(seed);

        // 内容比对才是硬断言：mtime 精度依文件系统而定（可能只有 1 秒），同秒内真写了也看不出来
        assertEquals(contentAfterFirstInject, Files.readString(mappingPath()));
        assertEquals(afterFirstInject, Files.getLastModifiedTime(mappingPath()));
    }

    // 文件里条目已齐（绕开"首次建文件"这个前置）→ 同样不落盘
    @Test
    void testCompleteFileIsNotRewritten() throws IOException {
        writeFile("yacl=yet_another_config_lib_v3\n");
        String contentBefore = Files.readString(mappingPath());
        FileTime before = Files.getLastModifiedTime(mappingPath());

        new MappingsStore(mappingPath()).injectSeed(Map.of("yacl", "yet_another_config_lib_v3"));

        assertEquals(contentBefore, Files.readString(mappingPath()));
        assertEquals(before, Files.getLastModifiedTime(mappingPath()));
    }

    // 空种子、null 种子 → 直接回读当前表，连文件都不建
    @Test
    void testEmptySeedIsNoOp() {
        MappingsStore store = store();

        assertEquals(0, store.injectSeed(null).size());
        assertEquals(0, store.injectSeed(Map.of()).size());
        assertFalse(Files.exists(mappingPath()));
    }

    // ---------- 读写边界 ----------

    // 文件不存在 → 空表，且不创建文件
    @Test
    void testLoadMissingFileReturnsEmpty() {
        MappingsStore store = store();

        assertEquals(0, store.load().size());
        assertFalse(Files.exists(mappingPath()));
    }

    // append 写出的行必须能被 load 读回；文件不存在时也由 append 自己建
    @Test
    void testAppendIsReadBack() throws IOException {
        MappingsStore store = store();

        assertTrue(store.append(Map.of("foo", "bar")));

        assertTrue(Files.exists(mappingPath()));   // 建文件也是 append 的活
        assertEquals(Optional.of("bar"), store.load().lookup("foo"));
    }

    // 落点被目录占住（读必然失败）→ 空表，不抛
    @Test
    void testUnreadableMappingPathReturnsEmpty() throws IOException {
        Files.createDirectories(mappingPath());
        MappingsStore store = store();

        assertEquals(0, assertDoesNotThrow((ThrowingSupplier<MappingTable>) store::load).size());
        assertEquals(0, assertDoesNotThrow(
                (ThrowingSupplier<MappingTable>) () -> store.injectSeed(Map.of("yacl", "x"))).size());
    }
}
