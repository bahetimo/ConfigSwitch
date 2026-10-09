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
}
