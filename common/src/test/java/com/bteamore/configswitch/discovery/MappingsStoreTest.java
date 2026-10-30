package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.List;
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

    // 要断言整段文件内容时顺序有意义：append 按 Map 的迭代顺序写行
    private Map<String, String> ordered(String firstKey, String firstValue, String secondKey, String secondValue) {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(firstKey, firstValue);
        mappings.put(secondKey, secondValue);
        return mappings;
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

    // ---------- ④ 旧格式迁移 ----------

    // 旧键（带扩展名）→ 键归一化为 stem：文件被原地重写，值不变
    @Test
    void testLegacyKeysAreMigratedToStems() throws IOException {
        writeFile("yacl.json5=yet_another_config_lib_v3\netf_warnings.json=entity_texture_features\n");
        MappingsStore store = store();

        store.injectSeed(Map.of("yacl", "yet_another_config_lib_v3"));

        assertEquals("etf_warnings=entity_texture_features\nyacl=yet_another_config_lib_v3\n",
                Files.readString(mappingPath()));
        // 读文件本身：换一个全新实例也要读得到（迁移结果是落盘的，不是内存里的）
        MappingTable reloaded = store().load();
        assertEquals(Optional.of("yet_another_config_lib_v3"), reloaded.lookup("yacl"));
        assertEquals(Optional.of("entity_texture_features"), reloaded.lookup("etf_warnings"));
    }

    // 迁移只发生一次：已是 stem 的键不再被当成旧格式，内容与 mtime 都不该变
    @Test
    void testMigrationHappensOnlyOnce() throws IOException {
        writeFile("yacl.json5=yet_another_config_lib_v3\n");
        MappingsStore store = store();
        Map<String, String> seed = Map.of("yacl", "yet_another_config_lib_v3");

        store.injectSeed(seed);
        String migrated = Files.readString(mappingPath());
        FileTime afterMigration = Files.getLastModifiedTime(mappingPath());

        store.injectSeed(seed);

        assertEquals(migrated, Files.readString(mappingPath()));
        assertEquals(afterMigration, Files.getLastModifiedTime(mappingPath()));
    }

    // 同一个 stem 出现两种写法 → 以已归一化的键为准，旧键被清掉而不是留成第二行
    @Test
    void testStemKeyWinsOverLegacyKey() throws IOException {
        writeFile("sodium.json=from_legacy\nsodium=from_stem\n");
        MappingsStore store = store();

        store.injectSeed(Map.of("sodium", "from_stem"));

        assertEquals("sodium=from_stem\n", Files.readString(mappingPath()));
        assertEquals(1L, countKeyLines("sodium"));
        assertEquals(Optional.of("from_stem"), store.load().lookup("sodium"));
    }

    // 原子替换的副产物：落点目录里只该有 mappings.txt，临时文件不能留下
    @Test
    void testMigrationLeavesNoTempFileBehind() throws IOException {
        writeFile("yacl.json5=yet_another_config_lib_v3\n");

        store().injectSeed(Map.of("yacl", "yet_another_config_lib_v3"));

        try (var entries = Files.list(mappingPath().getParent())) {
            assertEquals(List.of("mappings.txt"), entries.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }

    // ---------- ⑤ append 的换行边界 ----------

    // 0 字节文件：没有可接的尾字节，第一条前面不该多出换行
    @Test
    void testAppendToEmptyFileHasNoLeadingBlankLine() throws IOException {
        writeFile("");
        MappingsStore store = store();

        assertTrue(store.append(Map.of("foo", "bar")));

        assertEquals("foo=bar\n", Files.readString(mappingPath()));
    }

    // 连文件带目录都不存在、一次写多条：目录与文件由 append 自己建，第一条前面同样不该多出换行
    @Test
    void testAppendManyToMissingFileHasNoLeadingBlankLine() throws IOException {
        MappingsStore store = store();

        assertTrue(store.append(ordered("baz", "qux", "quux", "corge")));

        assertTrue(Files.exists(mappingPath()));
        assertEquals("baz=qux\nquux=corge\n", Files.readString(mappingPath()));
    }

    // 末尾已有换行：新条目直接接上，不补也不多出空行
    @Test
    void testAppendAfterTrailingNewlineAddsNoBlankLine() throws IOException {
        writeFile("foo=bar\n");
        MappingsStore store = store();

        assertTrue(store.append(Map.of("baz", "qux")));

        assertEquals("foo=bar\nbaz=qux\n", Files.readString(mappingPath()));
    }

    // 末尾已有换行、一次写多条：无需补换行，条目之间也不该出现空行
    @Test
    void testAppendManyAfterTrailingNewlineAddsNoBlankLine() throws IOException {
        writeFile("foo=bar\n");
        MappingsStore store = store();

        assertTrue(store.append(ordered("baz", "qux", "quux", "corge")));

        assertEquals("foo=bar\nbaz=qux\nquux=corge\n", Files.readString(mappingPath()));
    }

    // 末尾缺换行、只写一条：正好补一个换行把新旧两行隔开
    @Test
    void testAppendSingleAfterMissingTrailingNewlineAddsOne() throws IOException {
        writeFile("foo=bar");
        MappingsStore store = store();

        assertTrue(store.append(Map.of("baz", "qux")));

        assertEquals("foo=bar\nbaz=qux\n", Files.readString(mappingPath()));
    }

    // 末尾缺换行、一次写多条：补的换行只落在第一条之前，条目之间不该出现空行
    @Test
    void testAppendManyAfterMissingTrailingNewlineAddsSingleSeparator() throws IOException {
        writeFile("foo=bar");
        MappingsStore store = store();

        assertTrue(store.append(ordered("baz", "qux", "quux", "corge")));

        assertEquals("foo=bar\nbaz=qux\nquux=corge\n", Files.readString(mappingPath()));
    }

    // 连续两次 append：第二次接在第一次写出的换行后面，中间不空行
    @Test
    void testConsecutiveAppendsAddNoBlankLineBetween() throws IOException {
        MappingsStore store = store();

        assertTrue(store.append(Map.of("foo", "bar")));
        assertTrue(store.append(Map.of("baz", "qux")));

        assertEquals("foo=bar\nbaz=qux\n", Files.readString(mappingPath()));
    }

    // injectSeed 走的也是 append：末尾缺换行时补种多条，落盘该是一行一条、没有空行
    @Test
    void testInjectSeedIntoFileWithoutTrailingNewlineLeavesNoBlankLine() throws IOException {
        writeFile("foo=bar");
        MappingsStore store = store();

        store.injectSeed(Map.of("baz", "qux", "quux", "corge"));

        List<String> lines = Files.readAllLines(mappingPath());
        assertTrue(lines.stream().noneMatch(String::isEmpty), lines.toString());

        MappingTable table = store.load();
        assertEquals(Optional.of("qux"), table.lookup("baz"));
        assertEquals(Optional.of("corge"), table.lookup("quux"));
    }

    // injectSeed 落盘的就是磁盘上的表：换个新实例 load() 读到的内容与它返回的一致
    @Test
    void testLoadAfterInjectSeedReturnsSameTable() {
        MappingsStore store = store();

        MappingTable injected = store.injectSeed(MappingTable.loadBundled().entries());
        MappingTable reloaded = store().load();

        assertFalse(injected.entries().isEmpty());
        assertEquals(injected.entries(), reloaded.entries());
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
