package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class MappingTableTest {

    // 表只吃 Reader，解析不落盘，所以这里不用 @TempDir
    private MappingTable load(String content) throws IOException {
        return MappingTable.load(new StringReader(content));
    }

    // ---------- 条目解析 ----------

    // 不带 = 的条目是自映射（残留字典）：键和值都是同一个 modid
    @Test
    void testSelfMappingEntryResolvesToItself() throws IOException {
        MappingTable table = load("appleskin\ndurabilityviewer\n");

        assertEquals(2, table.size());
        assertEquals(Optional.of("appleskin"), table.lookup("appleskin"));
        assertEquals(Optional.of("durabilityviewer"), table.lookup("durabilityviewer"));
    }

    // 带 = 的条目是别名：左值是文件名 stem，右值是真实 modid
    @Test
    void testAliasEntryResolvesToRealModId() throws IOException {
        MappingTable table = load("yacl = yet_another_config_lib_v3\n");

        assertEquals(1, table.size());
        assertEquals(Optional.of("yet_another_config_lib_v3"), table.lookup("yacl"));
    }

    // 别名条目同样吃后缀剥离：yacl.json5、yacl_client.json 都指向真实 modid
    @Test
    void testAliasEntryMatchesWithSuffixStripped() throws IOException {
        MappingTable table = load("yacl = yet_another_config_lib_v3\n");

        assertEquals(Optional.of("yet_another_config_lib_v3"), table.lookup("yacl.json5"));
        assertEquals(Optional.of("yet_another_config_lib_v3"), table.lookup("yacl_client.json"));
    }

    // 空行与整行注释（含缩进后的 #）都不产生条目
    @Test
    void testBlankAndCommentLinesIgnored() throws IOException {
        MappingTable table = load("\n   \n# 注释\n   # 缩进注释\n");

        assertEquals(0, table.size());
        assertTrue(table.lookup("注释").isEmpty());
    }

    // 行内注释按第一个 # 截断：自映射条目的值不会带上注释尾巴
    @Test
    void testInlineCommentStripped() throws IOException {
        MappingTable table = load("foo             # [I] foo.json\nyacl = yet_another_config_lib_v3   # [V] yacl.json5\n");

        assertEquals(2, table.size());
        assertEquals(Optional.of("foo"), table.lookup("foo"));
        assertEquals(Optional.of("yet_another_config_lib_v3"), table.lookup("yacl.json5"));
    }

    // 键值两侧空白去掉、统一小写；比较不区分大小写
    @Test
    void testEntryTrimmedAndLowercased() throws IOException {
        MappingTable table = load("  Foo-Bar  =  Some_Mod  \n");

        assertEquals(1, table.size());
        assertEquals(Optional.of("some_mod"), table.lookup("FOO-BAR"));
        assertEquals(Optional.of("some_mod"), table.lookup("Foo-Bar.cfg"));
    }

    // 键或值为空的条目直接丢弃，不污染查表
    @Test
    void testMalformedEntriesSkipped() throws IOException {
        MappingTable table = load("= value\nkey =\n=\n   =   \n");

        assertEquals(0, table.size());
        assertTrue(table.lookup("key").isEmpty());
        assertTrue(table.lookup("value").isEmpty());
    }

    // 同一个键出现多次时后写的生效（用户记忆覆盖内置种子时的直觉行为）
    @Test
    void testDuplicateKeyLastEntryWins() throws IOException {
        MappingTable table = load("foo = first\nfoo = second\n");

        assertEquals(1, table.size());
        assertEquals(Optional.of("second"), table.lookup("foo"));
    }

    // size 只统计有效条目：空行、注释、坏条目都不计入
    @Test
    void testSizeCountsOnlyValidEntries() throws IOException {
        MappingTable table = load("a\nb = c\n# d\n\ne\n   # f\n");

        assertEquals(3, table.size());
    }

    // ---------- 查表匹配 ----------

    // 分隔符集合是 - _ .：任意尾段都可剥掉，剥剩下的前缀段命中即算匹配
    @Test
    void testStemSeparatorsAreHyphenUnderscoreAndDot() throws IOException {
        MappingTable table = load("foo\n");

        assertEquals(Optional.of("foo"), table.lookup("foo-bar"));
        assertEquals(Optional.of("foo"), table.lookup("foo_bar"));
        assertEquals(Optional.of("foo"), table.lookup("foo.bar"));
        assertEquals(Optional.of("foo"), table.lookup("foo-bar_baz.cfg"));
    }

    // 多个条目同时命中时取最长键：foo-bar-baz 归 foo-bar 而不是 foo
    @Test
    void testLongestEntryWins() throws IOException {
        MappingTable table = load("foo = short\nfoo-bar = long\n");

        assertEquals(Optional.of("long"), table.lookup("foo-bar-baz"));
        assertEquals(Optional.of("short"), table.lookup("foo-baz"));
    }

    // 双扩展名：剥掉 .properties / .json 后剩下的段继续剥，最终命中前缀条目
    @Test
    void testDoubleExtensionMatchesPrefixEntry() throws IOException {
        MappingTable table = load("ferritecore\nworldeditcui\n");

        assertEquals(Optional.of("ferritecore"), table.lookup("ferritecore.mixin.properties"));
        assertEquals(Optional.of("worldeditcui"), table.lookup("worldeditcui.config.json"));
    }

    // 负例：只有剥剩下的前缀段参与比较，中间段与尾段（mod、mixin）都不命中
    @Test
    void testInnerSegmentDoesNotMatch() throws IOException {
        MappingTable table = load("mod\nmixin\n");

        assertTrue(table.lookup("nota.mod.json").isEmpty());
        assertTrue(table.lookup("ferritecore.mixin.properties").isEmpty());
    }

    // 未命中的 stem 返回空 Optional，不抛异常也不回退成自身
    @Test
    void testUnknownStemReturnsEmpty() throws IOException {
        MappingTable table = load("appleskin\n");

        assertTrue(table.lookup("sodium").isEmpty());
        assertTrue(table.lookup("").isEmpty());
    }

    // 键比 stem 长时不可能命中：foo 不会归到 foo-bar 下
    @Test
    void testShorterStemDoesNotMatchLongerEntry() throws IOException {
        MappingTable table = load("foo-bar\n");

        assertTrue(table.lookup("foo").isEmpty());
    }

    // ---------- 现成 Map 构造 / entries() / empty() ----------

    // 用现成 Map 直接构造：不再经过文本解析（MappingsStore 注入种子后就是这条交接路径）
    @Test
    void testConstructFromMap() {
        MappingTable table = new MappingTable(Map.of("yacl", "yet_another_config_lib_v3"));

        assertEquals(1, table.size());
        assertEquals(Optional.of("yet_another_config_lib_v3"), table.lookup("yacl"));
    }

    // entries() 交出的内容能原样喂回构造函数，条目一个不少
    @Test
    void testEntriesRoundTrip() {
        MappingTable table = new MappingTable(Map.of("foo", "bar", "baz", "baz"));

        MappingTable copy = new MappingTable(table.entries());

        assertEquals(table.size(), copy.size());
        assertEquals(Optional.of("bar"), copy.lookup("foo"));
        assertEquals(Optional.of("baz"), copy.lookup("baz"));
    }

    // empty() 是公开的空表：查不到东西，也不抛
    @Test
    void testEmptyTableHasNoEntries() {
        MappingTable table = MappingTable.empty();

        assertEquals(0, table.size());
        assertTrue(table.lookup("yacl").isEmpty());
    }

    // ---------- 内置种子 ----------

    // 种子条目数是刻意的：改 seed.txt 就得同步改这里，避免条目被误删/误加而无声溜过
    @Test
    void testBundledSeedParses() {
        assertEquals(71, MappingTable.loadBundled().size());
    }

    // ---------- 读失败 ----------

    // Reader 中途读失败：lines() 包的 UncheckedIOException 被 load 拆回 IOException 抛出，
    // 调用方只需要接 IOException，不该看到包装类型
    @Test
    void testReaderFailureSurfacesAsIOException() {
        Reader failing = new Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public void close() {
            }
        };

        IOException thrown = assertThrows(IOException.class, () -> MappingTable.load(failing));

        // 拆包只脱壳、不换异常：原始 IOException 要原样冒出来
        assertEquals("boom", thrown.getMessage());
    }
}
