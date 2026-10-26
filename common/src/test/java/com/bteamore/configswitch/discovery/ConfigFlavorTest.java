package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigFlavorTest {

    // ---------- 单段目录名：缩写无效 ----------

    // 单段目录名只能造出 1 个字母的缩写，按无效处理，不该让任何文件都命中
    @Test
    void testSingleSegmentDirNameDoesNotMatchEverything() {
        assertFalse(ConfigFlavor.isConfigLike("123.json", "minihud"));
        // 文件名里确实含那个字母 'm'，仍不该命中
        assertFalse(ConfigFlavor.isConfigLike("some-mod.json", "minihud"));
    }

    // ---------- 驼峰目录名：缩写必须真的能命中 ----------

    // NCR 要从 NoChatReports 里提取出来
    @Test
    void testAcronymMustActuallyHitCamelCase() {
        // 这个文件还含 FLAVORS 词 client，命中也可能来自词表
        assertTrue(ConfigFlavor.isConfigLike("NCR-Client.json", "NoChatReports"));
        // 该文件既不含 "nochatreports"、也没有任何 FLAVOR 词 → 命中只能来自缩写
        assertTrue(ConfigFlavor.isConfigLike("NCR-Encryption.json", "NoChatReports"));
    }

    // 目录名带括号后缀时，括号里的内容不该混进缩写："(Fabric)" 的 F 不算
    @Test
    void testAcronymStripsBracketSuffix() {
        assertTrue(ConfigFlavor.isConfigLike("NCR-tweaks.json", "NoChatReports (Fabric)"));
    }

    // 数字词跟着缩写走：Config Switch 2 → CS2
    @Test
    void testDigitWordJoinsAcronym() {
        assertTrue(ConfigFlavor.isConfigLike("CS2-notes.json", "Config Switch 2"));
    }

    // 反向：目录名的缩写不该命中无关文件
    @Test
    void testAcronymDoesNotMatchUnrelatedFile() {
        assertFalse(ConfigFlavor.isConfigLike("sodium.json", "No Chat Reports"));
    }

    // 缩写生效的另一种形态：用空格或连字符分词的多词目录名
    @Test
    void testMultiWordDirNameAcronym() {
        assertTrue(ConfigFlavor.isConfigLike("NCR-export.json", "No Chat Reports"));
        assertTrue(ConfigFlavor.isConfigLike("ETF-warnings.json", "Entity Texture Features"));
    }

    // ---------- 正向 ----------

    // 目录名出现在文件名里即命中，两侧统一小写；反向则不该命中
    @Test
    void testDirNameAppearsInFileName() {
        assertTrue(ConfigFlavor.isConfigLike("minihud.json", "minihud"));
        assertTrue(ConfigFlavor.isConfigLike("MiniHUD.json", "minihud"));

        assertFalse(ConfigFlavor.isConfigLike("sodium.json", "minihud"));
    }

    // FLAVORS 词单独出现即命中，目录名完全不出现也算
    @Test
    void testFlavorWordAloneIsEnough() {
        assertTrue(ConfigFlavor.isConfigLike("common.json", "xaerominimap"));
        assertTrue(ConfigFlavor.isConfigLike("options.txt", "xaerominimap"));
    }

    // FLAVORS 用 contains 匹配：词出现在别的 mod 的文件里同样命中，
    // 词内子串也算（defaults 命中 default）——有意放宽的匹配，不是缺陷
    @Test
    void testFlavorWordMisjudgementIsPinned() {
        assertTrue(ConfigFlavor.isConfigLike("villager-trading-config.json", "inventoryprofilesnext"));
        // 词内子串同样命中：defaults 命中 default
        assertTrue(ConfigFlavor.isConfigLike("defaults-preview.json", "inventoryprofilesnext"));
    }

    // 词表里每个词都必须单独可命中
    @Test
    void testEveryFlavorWordMatches() {
        for (String word : List.of("common", "client", "server", "config", "settings", "blacklist",
                "whitelist", "default", "options", "main", "core", "global", "general")) {
            assertTrue(ConfigFlavor.isConfigLike(word + ".json", "xaerominimap"), word);
        }
    }

    // ---------- 边界 ----------

    // 任一侧为 null → false，不抛
    @Test
    void testNullInputsReturnFalse() {
        assertFalse(ConfigFlavor.isConfigLike(null, "sodium"));
        assertFalse(ConfigFlavor.isConfigLike("sodium.json", null));
        assertFalse(ConfigFlavor.isConfigLike(null, null));
    }

    // 空串一律 false：空串没有可比内容，两侧都在入口处直接返回
    @Test
    void testEmptyStringsReturnFalse() {
        assertFalse(ConfigFlavor.isConfigLike("123.json", ""));
        assertFalse(ConfigFlavor.isConfigLike("hello.txt", ""));

        assertFalse(ConfigFlavor.isConfigLike("", "minihud"));
        assertFalse(ConfigFlavor.isConfigLike("", ""));
    }
}
