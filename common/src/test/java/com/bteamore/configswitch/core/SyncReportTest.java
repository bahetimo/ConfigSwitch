package com.bteamore.configswitch.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SyncReportTest {

    // 一条可用的来源警告
    private static final SourceMismatch SODIUM =
            new SourceMismatch("sodium", "1.20.1", "fabric", "1.21.1", "fabric");

    // ---------- sourceMismatches 的收纳 ----------

    // 老构造器只给 results：没有来源可比，来源警告是空表而不是 null
    @Test
    void testOldConstructorHasNoMismatches() {
        SyncReport report = new SyncReport(Map.of("sodium", SyncOutcome.SUCCESS));

        // 读方不该因为"没传"而判空
        assertNotNull(report.sourceMismatches());
        assertEquals(List.of(), report.sourceMismatches());
    }

    // 新构造器显式传 null 也收纳成空表
    @Test
    void testNullMismatchListBecomesEmpty() {
        SyncReport report = new SyncReport(Map.of("sodium", SyncOutcome.SUCCESS), null);

        assertNotNull(report.sourceMismatches());
        assertEquals(List.of(), report.sourceMismatches());
    }

    // 构造时复制一份：传出后再改原 list（增、清）不影响已建好的报告
    @Test
    void testMismatchesAreDefensivelyCopied() {
        List<SourceMismatch> source = new ArrayList<>();
        source.add(SODIUM);

        SyncReport report = new SyncReport(Map.of("sodium", SyncOutcome.SUCCESS), source);
        source.add(new SourceMismatch("jei", "1.20.1", "fabric", "1.21.1", "fabric"));
        source.clear();

        assertEquals(List.of(SODIUM), report.sourceMismatches());
    }

    // 交出的是不可变列表：报告是同步结果的快照，读方不能就地增删
    @Test
    void testMismatchListIsImmutable() {
        SyncReport report = new SyncReport(Map.of("sodium", SyncOutcome.SUCCESS), List.of(SODIUM));

        assertThrows(UnsupportedOperationException.class, () -> report.sourceMismatches().add(SODIUM));
    }

    // 顺序沿用传入顺序：排序是产出方（ConfigHandler）的事，报告本身不重排
    @Test
    void testMismatchOrderIsPreserved() {
        SourceMismatch jei = new SourceMismatch("jei", "1.20.1", "fabric", "1.21.1", "fabric");

        SyncReport report = new SyncReport(Map.of(), List.of(jei, SODIUM));

        assertEquals(List.of(jei, SODIUM), report.sourceMismatches());
    }
}
