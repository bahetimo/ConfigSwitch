package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigClassifierTest {
    @TempDir
    Path tempDir;

    // 空映射表：只测 L2 文件名启发式的用例走它
    private static final MappingTable NO_MAPPING = mappingOf("");

    private static MappingTable mappingOf(String content) {
        try {
            return MappingTable.load(new StringReader(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e); // StringReader 不会失败，纯语法兜底
        }
    }

    // 只关心 L2 的用例：喂已安装集合即可（无映射表、无仓库目录、无 options.txt）
    private static List<ModGroup> classify(Set<String> installedModIds, Path... configFiles) {
        return new ConfigClassifier(installedModIds, NO_MAPPING, Set.of()).classify(null, List.of(configFiles));
    }

    //文件名匹配modid时归组
    @Test
    void testFileNameMatchesModId() {
        List<ModGroup> groups = classify(Set.of("appleskin", "sodium"), Path.of("config/appleskin.json5"));

        assertEquals(1, groups.size());
        assertEquals("appleskin", groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // options.txt 归原版组
    @Test
    void testOptionsFileAssignedToVanilla() throws IOException {
        Path optionsFile = tempDir.resolve("options.txt");
        Files.createFile(optionsFile);

        ConfigClassifier classifier = new ConfigClassifier(Set.of(), NO_MAPPING, Set.of());
        List<ModGroup> groups = classifier.classify(optionsFile, List.of());

        assertEquals(1, groups.size());
        assertEquals(ModGroup.VANILLA_ID, groups.get(0).getModId());
        assertEquals(List.of(optionsFile), groups.get(0).getFiles());
    }

    // 文件名匹配 modid(大小写不敏感)
    @Test
    void testFileNameMatchesModIdCaseInsensitive() {
        List<ModGroup> groups = classify(Set.of("mousetweaks", "sodium"), Path.of("config/MouseTweaks.cfg"));

        assertEquals(1, groups.size());
        assertEquals("mousetweaks", groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // 不匹配任何 modid 的文件进未分类组
    @Test
    void testUnmatchedFileGoesToUncategorized() {
        List<ModGroup> groups = classify(Set.of("xaerominimap"), Path.of("config/xaeropatreon.txt"));

        assertEquals(1, groups.size());
        assertEquals(ModGroup.UNCATEGORIZED_ID, groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // 文件名带 - 后缀时，剥离后缀匹配 modid
    @Test
    void testHyphenSuffixStrippedToMatchModId() {
        List<ModGroup> groups = classify(Set.of("sodium", "appleskin"), Path.of("config/sodium-extra.cfg"));

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // 文件名带 _ 后缀时，剥离后缀匹配 modid
    @Test
    void testUnderscoreSuffixStrippedToMatchModId() {
        List<ModGroup> groups = classify(Set.of("sodium"), Path.of("config/sodium_extra.cfg"));

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
    }

    // 多级后缀依次剥离后匹配 modid
    @Test
    void testMultiLevelSuffixStripping() {
        List<ModGroup> groups = classify(Set.of("foo"), Path.of("config/foo-bar-baz.cfg"));

        assertEquals(1, groups.size());
        assertEquals("foo", groups.get(0).getModId());
    }

    // 长 modid 优先匹配：foo-bar.cfg 归 foo-bar 而非 foo
    @Test
    void testLongerModIdTakesPriority() {
        List<ModGroup> groups = classify(Set.of("foo", "foo-bar"), Path.of("config/foo-bar.cfg"));

        assertEquals(1, groups.size());
        assertEquals("foo-bar", groups.get(0).getModId());
    }

    // 长 modid 优先且支持后缀剥离：foo-bar-baz.cfg 归 foo-bar 而非 foo
    @Test
    void testLongerModIdTakesPriorityWithSuffixStripping() {
        List<ModGroup> groups = classify(Set.of("foo", "foo-bar"), Path.of("config/foo-bar-baz.cfg"));

        assertEquals(1, groups.size());
        assertEquals("foo-bar", groups.get(0).getModId());
    }

    // 后缀剥离同时大小写不敏感
    @Test
    void testSuffixStrippingCaseInsensitive() {
        List<ModGroup> groups = classify(Set.of("sodium"), Path.of("config/SODIUM-EXTRA.cfg"));

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
    }

    // modid 自身含 - 时精确匹配完整文件名
    @Test
    void testHyphenatedModIdMatchesExactly() {
        List<ModGroup> groups = classify(Set.of("foo-bar"), Path.of("config/foo-bar.cfg"));

        assertEquals(1, groups.size());
        assertEquals("foo-bar", groups.get(0).getModId());
    }

    // modid 自身含 - 时不匹配短文件名
    @Test
    void testHyphenatedModIdDoesNotMatchShorterFile() {
        List<ModGroup> groups = classify(Set.of("foo-bar"), Path.of("config/foo.cfg"));

        assertEquals(1, groups.size());
        assertEquals(ModGroup.UNCATEGORIZED_ID, groups.get(0).getModId());
    }

    // 混合 - 和 _ 分隔符时逐级剥离
    @Test
    void testMixedSeparatorsStripping() {
        List<ModGroup> groups = classify(Set.of("foo-bar"), Path.of("config/foo-bar_baz.cfg"));

        assertEquals(1, groups.size());
        assertEquals("foo-bar", groups.get(0).getModId());
    }

    // 无扩展名文件同样支持后缀剥离
    @Test
    void testFileWithoutExtensionStripped() {
        List<ModGroup> groups = classify(Set.of("sodium"), Path.of("config/sodium-extra"));

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
    }

    // 点号同为剥离分隔符：foo.bar.cfg 剥掉中间段 bar 后匹配 modid foo
    @Test
    void testDotSuffixStrippedToMatchModId() {
        List<ModGroup> groups = classify(Set.of("foo"), Path.of("config/foo.bar.cfg"));

        assertEquals(1, groups.size());
        assertEquals("foo", groups.get(0).getModId());
    }

    // 双扩展名：剥掉 .properties 后剩下 ferritecore.mixin，点号段继续剥离后命中
    @Test
    void testDoubleExtensionStrippedToMatchModId() {
        List<ModGroup> groups = classify(Set.of("ferritecore"), Path.of("config/ferritecore.mixin.properties"));

        assertEquals(1, groups.size());
        assertEquals("ferritecore", groups.get(0).getModId());
    }

    // 双扩展名带配置段：worldeditcui.config.json 命中 worldeditcui
    @Test
    void testDoubleExtensionWithConfigInfixMatchesModId() {
        List<ModGroup> groups = classify(Set.of("worldeditcui"), Path.of("config/worldeditcui.config.json"));

        assertEquals(1, groups.size());
        assertEquals("worldeditcui", groups.get(0).getModId());
    }

    // 负例：只有剩下的前缀段参与匹配，中间段 mod 与尾段 mixin 都不命中
    @Test
    void testInnerSegmentDoesNotMatchModId() {
        List<ModGroup> groups = classify(Set.of("mod", "mixin"),
                Path.of("config/nota.mod.json"),
                Path.of("config/ferritecore.mixin.properties"));

        assertEquals(1, groups.size());
        assertEquals(ModGroup.UNCATEGORIZED_ID, groups.get(0).getModId());
        assertEquals(2, groups.get(0).getFiles().size());
    }

    // 多个文件混合分组：带后缀、精确匹配与长 modid 优先同时生效
    @Test
    void testMixedFilesGroupedCorrectly() {
        List<ModGroup> groups = classify(Set.of("sodium", "appleskin", "foo", "foo-bar"),
                Path.of("config/sodium-extra.cfg"),
                Path.of("config/appleskin.cfg"),
                Path.of("config/foo-bar.cfg"));

        assertEquals(3, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
        assertEquals("appleskin", groups.get(1).getModId());
        assertEquals(1, groups.get(1).getFiles().size());
        assertEquals("foo-bar", groups.get(2).getModId());
        assertEquals(1, groups.get(2).getFiles().size());
    }

    // ---------- ① 映射表（断言优先于启发式） ----------

    // 别名命中：yacl.json5 当场归到真实 modid
    @Test
    void testMappingHitGroupsByTargetModId() {
        ConfigClassifier classifier = new ConfigClassifier(
                Set.of("yet_another_config_lib_v3"), mappingOf("yacl = yet_another_config_lib_v3"), Set.of());

        List<ModGroup> groups = classifier.classify(null, List.of(Path.of("config/yacl.json5")));

        assertEquals(1, groups.size());
        assertEquals("yet_another_config_lib_v3", groups.get(0).getModId());
    }

    // 映射表先于 L2：命中映射后不再看文件名启发式（哪怕 foo-bar 已安装）
    @Test
    void testMappingTakesPrecedenceOverFileNameHeuristic() {
        ConfigClassifier classifier = new ConfigClassifier(
                Set.of("foo-bar", "real"), mappingOf("foo-bar = real"), Set.of());

        List<ModGroup> groups = classifier.classify(null, List.of(Path.of("config/foo-bar.cfg")));

        assertEquals(1, groups.size());
        assertEquals("real", groups.get(0).getModId());
    }

    // 目标为 ignore → 文件整个丢弃：连未分类都不进
    @Test
    void testMappingIgnoreDropsFile() {
        ConfigClassifier classifier = new ConfigClassifier(Set.of(), mappingOf("foo = ignore"), Set.of());

        List<ModGroup> groups = classifier.classify(null, List.of(Path.of("config/foo.json")));

        assertTrue(groups.isEmpty());
    }

    // ---------- ③ 未安装的组 → 残留组（不渲染） ----------

    // 映射命中但目标没装 → 进残留组，而不是留在可见组里
    @Test
    void testMappingTargetNotInstalledGoesToResidue() {
        ConfigClassifier classifier = new ConfigClassifier(
                Set.of(), mappingOf("yacl = yet_another_config_lib_v3"), Set.of());

        List<ModGroup> groups = classifier.classify(null, List.of(Path.of("config/yacl.json5")));

        assertEquals(1, groups.size());
        assertEquals(ModGroup.RESIDUE_ID, groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // 真实场景：本包只装了 sodium，种子里有 sodium-extra（从残留文件剥出来的真 modid），
    // 文件 sodium-extra-options.json 属一个没装的 mod → 必须隐藏。
    // 判断装没装只能用 contains：StemMatches.matches("sodium", "sodium-extra") 恰好为 true，
    // 用错就把残留留在 sodium 组里了；多数用例下两种写法结果相同，只有这条能分辨。
    @Test
    void testInstalledCheckUsesExactMatchNotStemMatch() {
        ConfigClassifier classifier = new ConfigClassifier(
                Set.of("sodium"),                          // 只装了 sodium
                mappingOf("sodium-extra = sodium-extra"),  // 表里是带连字符的真 modid
                Set.of());

        List<ModGroup> groups = classifier.classify(null,
                List.of(Path.of("config/sodium-extra-options.json")));

        assertEquals(1, groups.size());
        assertEquals(ModGroup.RESIDUE_ID, groups.get(0).getModId());   // 应是残留，不是归到 sodium
    }

    // 仓库目录名（B）命中但 mod 没装 → 同样算残留
    @Test
    void testRepoModMatchGoesToResidue() {
        ConfigClassifier classifier = new ConfigClassifier(Set.of("xaerominimap"), NO_MAPPING, Set.of("xaeropatreon"));

        List<ModGroup> groups = classifier.classify(null, List.of(Path.of("config/xaeropatreon.txt")));

        assertEquals(1, groups.size());
        assertEquals(ModGroup.RESIDUE_ID, groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // 多个残留合并成一个组：收件箱不按 modid 一行一行地淹没列表
    @Test
    void testResiduesMergedIntoSingleGroup() {
        ConfigClassifier classifier = new ConfigClassifier(Set.of(), NO_MAPPING, Set.of("foo", "bar"));

        List<ModGroup> groups = classifier.classify(null, List.of(
                Path.of("config/foo.json"),
                Path.of("config/bar.json")
        ));

        assertEquals(1, groups.size());
        assertEquals(ModGroup.RESIDUE_ID, groups.get(0).getModId());
        assertEquals(2, groups.get(0).getFiles().size());
    }

    // 残留组固定置底，可见组顺序不变
    @Test
    void testResidueGroupAppendedLast() {
        ConfigClassifier classifier = new ConfigClassifier(Set.of("sodium"), NO_MAPPING, Set.of("xaeropatreon"));

        List<ModGroup> groups = classifier.classify(null, List.of(
                Path.of("config/xaeropatreon.txt"),
                Path.of("config/sodium.json")
        ));

        assertEquals(2, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
        assertEquals(ModGroup.RESIDUE_ID, groups.get(1).getModId());
    }
}
