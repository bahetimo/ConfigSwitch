package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileNamesTest {

    // 去掉最后一段扩展名，中间的点号保留
    @Test
    void testStemDropsLastExtension() {
        assertEquals("sodium-options", FileNames.stemOf("sodium-options.json"));
        assertEquals("ferritecore.mixin", FileNames.stemOf("ferritecore.mixin.properties"));
    }

    // 无扩展名时原样返回
    @Test
    void testStemWithoutExtensionIsUnchanged() {
        assertEquals("options", FileNames.stemOf("options"));
        assertEquals("sodium-extra", FileNames.stemOf("sodium-extra"));
    }

    // 点号在首位或末尾的边界
    @Test
    void testStemAtDotBoundaries() {
        assertEquals("", FileNames.stemOf(".gitignore"));
        assertEquals("sodium", FileNames.stemOf("sodium."));
    }

    // 大小写原样保留，是否忽略由调用方的比较逻辑决定
    @Test
    void testStemKeepsOriginalCase() {
        assertEquals("Sodium-Options", FileNames.stemOf("Sodium-Options.JSON"));
    }

    // ---------- 尾斜杠：目录名角色 ----------

    // 同名输入的三种形态：目录名（尾斜杠）与无扩展名文件都原样返回，带扩展名的剥掉扩展名
    @Test
    void testTrailingSlashMeansDirectoryName() {
        assertEquals("jei", FileNames.stemOf("jei/"));
        assertEquals("jei", FileNames.stemOf("jei"));
        assertEquals("a", FileNames.stemOf("a.b"));
    }

    // 目录名里的点号不是扩展名：config.json/ 就是 config.json，大小写也原样
    @Test
    void testDirectoryNameKeepsDotsAndCase() {
        assertEquals("config.json", FileNames.stemOf("config.json/"));
        assertEquals("sodium-extra.v2", FileNames.stemOf("sodium-extra.v2/"));
        assertEquals("NoChatReports", FileNames.stemOf("NoChatReports/"));
    }

    // 空串与纯斜杠：都不该抛，落回空串
    @Test
    void testEmptyAndSlashOnlyFallBackToEmpty() {
        assertEquals("", FileNames.stemOf(""));
        assertEquals("", FileNames.stemOf("/"));
    }
}
