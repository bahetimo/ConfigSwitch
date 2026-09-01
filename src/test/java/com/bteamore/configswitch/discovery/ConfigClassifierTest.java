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
}
