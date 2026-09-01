package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigClassifierTest {
    @TempDir
    Path tempDir;

    //文件名匹配modid时归组
    @Test
    void testFileNameMatchesModId() {
        ConfigClassifier c = new ConfigClassifier();

        List<ModGroup> groups = c.classify(
                null,
                List.of(Path.of("config/appleskin.json5")),
                Set.of("appleskin", "sodium")
        );

        assertEquals(1, groups.size());
        assertEquals("appleskin", groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // options.txt 归原版组
    @Test
    void testOptionsFileAssignedToVanilla() throws IOException {
        Path optionsFile = tempDir.resolve("options.txt");
        Files.createFile(optionsFile);

        ConfigClassifier classifier = new ConfigClassifier();
        List<ModGroup> groups = classifier.classify(optionsFile, List.of(), Set.of());

        assertEquals(1, groups.size());
        assertEquals(ModGroup.VANILLA_ID, groups.get(0).getModId());
        assertEquals(List.of(optionsFile), groups.get(0).getFiles());
    }

    // 文件名匹配 modid(大小写不敏感)
    @Test
    void testFileNameMatchesModIdCaseInsensitive() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/MouseTweaks.cfg")),
                Set.of("mousetweaks", "sodium")
        );

        assertEquals(1, groups.size());
        assertEquals("mousetweaks", groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // 不匹配任何 modid 的文件进未分类组
    @Test
    void testUnmatchedFileGoesToUncategorized() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/xaeropatreon.txt")),
                Set.of("xaerominimap")
        );

        assertEquals(1, groups.size());
        assertEquals(ModGroup.UNCATEGORIZED_ID, groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // 文件名带 - 后缀时，剥离后缀匹配 modid
    @Test
    void testHyphenSuffixStrippedToMatchModId() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/sodium-extra.cfg")),
                Set.of("sodium", "appleskin")
        );

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
    }

    // 文件名带 _ 后缀时，剥离后缀匹配 modid
    @Test
    void testUnderscoreSuffixStrippedToMatchModId() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/sodium_extra.cfg")),
                Set.of("sodium")
        );

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
    }

    // 多级后缀依次剥离后匹配 modid
    @Test
    void testMultiLevelSuffixStripping() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/foo-bar-baz.cfg")),
                Set.of("foo")
        );

        assertEquals(1, groups.size());
        assertEquals("foo", groups.get(0).getModId());
    }

    // 长 modid 优先匹配：foo-bar.cfg 归 foo-bar 而非 foo
    @Test
    void testLongerModIdTakesPriority() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/foo-bar.cfg")),
                Set.of("foo", "foo-bar")
        );

        assertEquals(1, groups.size());
        assertEquals("foo-bar", groups.get(0).getModId());
    }

    // 长 modid 优先且支持后缀剥离：foo-bar-baz.cfg 归 foo-bar 而非 foo
    @Test
    void testLongerModIdTakesPriorityWithSuffixStripping() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/foo-bar-baz.cfg")),
                Set.of("foo", "foo-bar")
        );

        assertEquals(1, groups.size());
        assertEquals("foo-bar", groups.get(0).getModId());
    }

    // 后缀剥离同时大小写不敏感
    @Test
    void testSuffixStrippingCaseInsensitive() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/SODIUM-EXTRA.cfg")),
                Set.of("sodium")
        );

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
    }

    // modid 自身含 - 时精确匹配完整文件名
    @Test
    void testHyphenatedModIdMatchesExactly() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/foo-bar.cfg")),
                Set.of("foo-bar")
        );

        assertEquals(1, groups.size());
        assertEquals("foo-bar", groups.get(0).getModId());
    }

    // modid 自身含 - 时不匹配短文件名
    @Test
    void testHyphenatedModIdDoesNotMatchShorterFile() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/foo.cfg")),
                Set.of("foo-bar")
        );

        assertEquals(1, groups.size());
        assertEquals(ModGroup.UNCATEGORIZED_ID, groups.get(0).getModId());
    }

    // 混合 - 和 _ 分隔符时逐级剥离
    @Test
    void testMixedSeparatorsStripping() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/foo-bar_baz.cfg")),
                Set.of("foo-bar")
        );

        assertEquals(1, groups.size());
        assertEquals("foo-bar", groups.get(0).getModId());
    }

    // 无扩展名文件同样支持后缀剥离
    @Test
    void testFileWithoutExtensionStripped() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/sodium-extra")),
                Set.of("sodium")
        );

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
    }

    // 点号不作为剥离分隔符：foo.bar.cfg 不匹配 modid foo
    @Test
    void testDotIsNotStrippedAsSeparator() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(Path.of("config/foo.bar.cfg")),
                Set.of("foo")
        );

        assertEquals(1, groups.size());
        assertEquals(ModGroup.UNCATEGORIZED_ID, groups.get(0).getModId());
    }

    // 多个文件混合分组：带后缀、精确匹配与长 modid 优先同时生效
    @Test
    void testMixedFilesGroupedCorrectly() {
        ConfigClassifier classifier = new ConfigClassifier();

        List<ModGroup> groups = classifier.classify(
                null,
                List.of(
                        Path.of("config/sodium-extra.cfg"),
                        Path.of("config/appleskin.cfg"),
                        Path.of("config/foo-bar.cfg")
                ),
                Set.of("sodium", "appleskin", "foo", "foo-bar")
        );

        assertEquals(3, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
        assertEquals("appleskin", groups.get(1).getModId());
        assertEquals(1, groups.get(1).getFiles().size());
        assertEquals("foo-bar", groups.get(2).getModId());
        assertEquals(1, groups.get(2).getFiles().size());
    }
}
