package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.repo.ConfigPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigHandlerTest {
    @TempDir
    Path tempDir;

    private ConfigPaths newPaths(String fileName) {
        return new ConfigPaths(
                fileName,
                tempDir.resolve("active").resolve(fileName),
                tempDir.resolve("global").resolve(fileName),
                tempDir.resolve("backup").resolve(fileName)
        );
    }

    // global 已存在时：先备份旧值到 backup，再以 active 覆盖 global
    @Test
    void testPushOverwritesExistingGlobalAndBacksUp() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");
        Files.createDirectories(paths.global().getParent());
        Files.writeString(paths.global(), "old");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        assertEquals("new", Files.readString(paths.global()));
        assertTrue(Files.exists(paths.backup()));
        assertEquals("old", Files.readString(paths.backup()));
    }

    // global 不存在时：直接创建 global，不产生备份
    @Test
    void testPushCreatesGlobalWithoutBackup() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");

        new ConfigHandler().pushActiveToGlobal(List.of(paths));

        assertEquals("new", Files.readString(paths.global()));
        assertFalse(Files.exists(paths.backup()));
    }

    // 列表中的 null 元素跳过，不影响其余条目
    @Test
    void testPushSkipsNullEntry() throws IOException {
        ConfigPaths paths = newPaths("options.txt");
        Files.createDirectories(paths.active().getParent());
        Files.writeString(paths.active(), "new");

        // List.of 不允许 null 元素，用 Arrays.asList 构造含 null 的列表
        new ConfigHandler().pushActiveToGlobal(Arrays.asList(null, paths));

        assertEquals("new", Files.readString(paths.global()));
        assertFalse(Files.exists(paths.backup()));
    }
}
