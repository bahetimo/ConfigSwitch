package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GlobalConfigScannerTest {
    @TempDir
    Path tempDir;

    private final GlobalConfigScanner scanner = new GlobalConfigScanner();

    // common/options.txt → 原版组，组内含 options.txt
    @Test
    void testOptionsTxtCreatesVanillaGroup() throws IOException {
        Path commonDir = tempDir.resolve("common");
        Files.createDirectories(commonDir);
        Path optionsFile = commonDir.resolve("options.txt");
        Files.createFile(optionsFile);

        List<ModGroup> groups = scanner.scan(commonDir);

        assertEquals(1, groups.size());
        ModGroup vanilla = groups.get(0);
        assertEquals(ModGroup.VANILLA_ID, vanilla.getModId());
        assertEquals(List.of(optionsFile), vanilla.getFiles());
    }

    // common/<modid>/xxx.json → modid 组（modId 来自目录名）
    @Test
    void testModDirCreatesGroupWithDirNameAsModId() throws IOException {
        Path commonDir = tempDir.resolve("common");
        Path sodiumDir = commonDir.resolve("sodium");
        Files.createDirectories(sodiumDir);
        Path fileA = sodiumDir.resolve("sodium-options.json");
        Path fileB = sodiumDir.resolve("sodium-mixins.properties");
        Files.createFile(fileA);
        Files.createFile(fileB);

        List<ModGroup> groups = scanner.scan(commonDir);

        assertEquals(1, groups.size());
        ModGroup sodium = groups.get(0);
        assertEquals("sodium", sodium.getModId());
        assertEquals(2, sodium.getFiles().size());
        assertTrue(sodium.getFiles().containsAll(List.of(fileA, fileB)));
    }

    // common 目录不存在 → 空结果，不报错
    @Test
    void testMissingCommonDirReturnsEmpty() {
        List<ModGroup> groups = scanner.scan(tempDir.resolve("no-such-common"));

        assertTrue(groups.isEmpty());
    }

    // null 路径 → 空结果，不报错
    @Test
    void testNullCommonDirReturnsEmpty() {
        assertTrue(scanner.scan(null).isEmpty());
    }

    // 保留 ID 目录（minecraft/uncategorized）被跳过，不出现在结果里
    @Test
    void testReservedIdDirsSkipped() throws IOException {
        Path commonDir = tempDir.resolve("common");
        Files.createDirectories(commonDir.resolve("minecraft"));
        Files.createFile(commonDir.resolve("minecraft").resolve("foo.cfg"));
        Files.createDirectories(commonDir.resolve("uncategorized"));
        Files.createFile(commonDir.resolve("uncategorized").resolve("bar.cfg"));
        Path sodiumDir = commonDir.resolve("sodium");
        Files.createDirectories(sodiumDir);
        Files.createFile(sodiumDir.resolve("sodium-options.json"));

        List<ModGroup> groups = scanner.scan(commonDir);

        assertEquals(1, groups.size());
        assertEquals("sodium", groups.get(0).getModId());
    }

    // options.txt 与 mod 目录同时存在 → 原版组 + 各 mod 组
    @Test
    void testMixedLayout() throws IOException {
        Path commonDir = tempDir.resolve("common");
        Files.createDirectories(commonDir);
        Files.createFile(commonDir.resolve("options.txt"));
        Path sodiumDir = commonDir.resolve("sodium");
        Files.createDirectories(sodiumDir);
        Files.createFile(sodiumDir.resolve("sodium-options.json"));

        List<ModGroup> groups = scanner.scan(commonDir);

        assertEquals(2, groups.size());
        assertEquals(ModGroup.VANILLA_ID, groups.get(0).getModId());
        assertEquals(1, groups.get(0).getFiles().size());
        assertEquals("sodium", groups.get(1).getModId());
        assertEquals(1, groups.get(1).getFiles().size());
    }
}
