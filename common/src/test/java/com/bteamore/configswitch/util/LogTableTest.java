package com.bteamore.configswitch.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class LogTableTest {

    private static List<String> lines(Log.Table table) {
        return table.toString().lines().toList();
    }

    // 同一组的行共用列宽：每列按组内最宽的单元格补齐
    @Test
    void testColumnsAlignedInSameGroup() {
        Log.Table table = Log.table("header")
                .row("  ", "A", "aa", "1")
                .row("  ", "BB", "b", "22");

        assertEquals(List.of(
                "header",
                "  A   aa  1",
                "  BB  b   22"), lines(table));
    }

    // 缩进或列数变化 → 另起一组，列宽重新计算
    @Test
    void testNewGroupRecomputesWidths() {
        Log.Table table = Log.table("header")
                .row("    ", "section:")
                .row("        ", "short", "1 files")
                .row("        ", "a-much-longer-name", "22 files");

        assertEquals(List.of(
                "header",
                "    section:",
                "        short               1 files",
                "        a-much-longer-name  22 files"), lines(table));
    }

    // 最后一列不补空格（行尾不留空白）
    @Test
    void testLastColumnNotPadded() {
        Log.Table table = Log.table("h")
                .row("", "x", "a")
                .row("", "y", "longer");

        assertEquals(List.of("h", "x  a", "y  longer"), lines(table));
        assertFalse(lines(table).stream().anyMatch(line -> line.endsWith(" ")));
    }

    // 单列行（标题）按原样输出，重复取内容结果一致
    @Test
    void testSingleCellRowAndRepeatedBuild() {
        Log.Table table = Log.table("summary").row("    ", "TITLE:");

        assertEquals(List.of("summary", "    TITLE:"), lines(table));
        assertEquals(lines(table), lines(table));
    }
}
