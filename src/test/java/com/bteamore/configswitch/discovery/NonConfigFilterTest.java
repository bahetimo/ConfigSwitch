package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonConfigFilterTest {

    // 辅助：把"期望跳过/放行"与用例意图写在一起，失败时信息自解释
    private static void assertSkipped(String relativePath) {
        assertTrue(NonConfigFilter.isSkipped(relativePath), "应跳过: " + relativePath);
    }

    private static void assertKept(String relativePath) {
        assertFalse(NonConfigFilter.isSkipped(relativePath), "应放行: " + relativePath);
    }

    // ---------- 段黑名单（完全相等） ----------

    // jei/world/local/x.json -> 跳过：路径里有 world 这一【段】
    @Test
    void testWorldSegmentSkipped() {
        assertSkipped("jei/world/local/x.json");
    }

    // 顶层目录名本身就是 world -> 同样按段命中
    @Test
    void testTopLevelWorldSegmentSkipped() {
        assertSkipped("world/x.json");
    }

    // 深层段命中：命中位置不在开头也算
    @Test
    void testBlacklistedSegmentAtAnyDepth() {
        assertSkipped("a/b/logs/c.json");
        assertSkipped("mod/tmp/x.json");
    }

    // 其余黑名单段各自可得：cache / saves / backup / backups / export / auth / temp
    @Test
    void testOtherBlacklistedSegments() {
        assertSkipped("mod/cache/x.json");
        assertSkipped("mod/saves/x.json");
        assertSkipped("mod/backup/x.json");
        assertSkipped("mod/backups/x.json");
        assertSkipped("mod/export/x.json");
        assertSkipped("mod/auth/x.json");
        assertSkipped("mod/temp/x.json");
    }

    // ---------- 段完全相等，而非包含 ----------

    // jei/world_settings.json -> 放行：段是 world_settings.json，与 world 不相等
    @Test
    void testWorldSettingsNotSkipped() {
        assertKept("jei/world_settings.json");
    }

    // 顶层文件名带黑名单词 -> 放行：段比较不做扩展名剥离
    @Test
    void testTopLevelFileNameWithBlacklistWordKept() {
        assertKept("world.json");
        assertKept("cache.json");
        assertKept("logs.txt");
        assertKept("saves.dat");
    }

    // 黑名单词加后缀/前缀 -> 放行：段必须完全相等
    @Test
    void testBlacklistWordWithAffixKept() {
        assertKept("mod/worlds/x.json");
        assertKept("mod/cachex/x.json");
        assertKept("mod/logs-old/x.json");
        assertKept("mod/myworld/x.json");
    }

    // ---------- 段内子串模式（_per_world / _per_server） ----------

    // litematica/area_selections_per_world/x -> 跳过：段含 _per_world
    @Test
    void testPerWorldSubstringSkipped() {
        assertSkipped("litematica/area_selections_per_world/x");
    }

    // _per_server 同属子串模式
    @Test
    void testPerServerSubstringSkipped() {
        assertSkipped("mod/whatever_per_server/x.json");
    }

    // 子串模式是 contains：前导下划线不可缺（per_world 不算），但尾随字符不豁免
    @Test
    void testPerWorldSubstringIsContains() {
        assertKept("mod/per_world/x.json");
        assertSkipped("mod/area_selections_per_worldx/x.json");
    }

    // ---------- 段黑名单 + hash 段同时出现 ----------

    // yes_steve_model/cache/client/<非hash>/x -> 跳过：把 hash 段换成普通段，只考察 cache 段
    @Test
    void testCacheSegmentSkippedWithoutHashInterference() {
        assertSkipped("yes_steve_model/cache/client/not_a_hash/x");
    }

    // yes_steve_model/cache/client/<hash>/x -> 跳过（真实形态，同时含 cache 段与 hash 段）
    @Test
    void testCacheAndHashSegmentsSkipped() {
        assertSkipped("yes_steve_model/cache/client/0123456789abcdef/x");
    }

    // ---------- hash 段（^[0-9a-f]{16}$） ----------

    // 16 位小写 hex 段 -> 跳过，即使没有任何黑名单段
    @Test
    void testHashSegmentSkipped() {
        assertSkipped("mod/0123456789abcdef/x.json");
    }

    // 长度边界：15 位 / 17 位都不算 hash 段
    @Test
    void testHashSegmentLengthBoundary() {
        assertKept("mod/0123456789abcde/x.json");
        assertKept("mod/0123456789abcdef0/x.json");
    }

    // 非 hex 字符（g-z、大写、连字符）不算 hash 段
    @Test
    void testNonHexSegmentKept() {
        assertKept("mod/0123456789abcdeg/x.json");
        assertKept("mod/0123456789abcdef-1/x.json");
    }

    // ---------- 不能误伤（真实放行样本） ----------

    // NoChatReports/NCR-Client.json -> 放行：15 字符且含大写/点号，不匹配 hash，也不在黑名单
    @Test
    void testNcrClientNotSkipped() {
        assertKept("NoChatReports/NCR-Client.json");
    }

    // 普通 mod 配置结构 -> 放行
    @Test
    void testOrdinaryConfigPathsKept() {
        assertKept("sodium.json");
        assertKept("jei/jei.json");
        assertKept("mod/client/settings.json5");
    }

    // ---------- 空与退化输入 ----------

    // 空路径 -> 放行（split 得到一个空段，不命中任何规则）
    @Test
    void testEmptyPathKept() {
        assertKept("");
    }

    // 单段黑名单词（无扩展名）本身也算段 -> 跳过
    @Test
    void testSingleBlacklistSegmentSkipped() {
        assertSkipped("world");
    }
}
