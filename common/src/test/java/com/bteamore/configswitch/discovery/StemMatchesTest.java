package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StemMatchesTest {

    // ---------- 空值与空串一律不匹配 ----------

    // modId 为空串：空串不能与剥到空的 stem 判为相等
    @Test
    void testEmptyModIdNeverMatches() {
        assertFalse(StemMatches.matches("", "a.b"));
        assertFalse(StemMatches.matches("", "sodium"));
        assertFalse(StemMatches.matches("", "_y.z"));
        assertFalse(StemMatches.matches("", ""));
    }

    // stem 为空串
    @Test
    void testEmptyStemNeverMatches() {
        assertFalse(StemMatches.matches("sodium", ""));
        assertFalse(StemMatches.matches("x", ""));
    }

    // null 入参返回 false 而非抛异常
    @Test
    void testNullArgumentsNeverMatch() {
        assertFalse(StemMatches.matches(null, "a.b"));
        assertFalse(StemMatches.matches("a", null));
        assertFalse(StemMatches.matches(null, null));
    }

    // 剥到空串即停止，空串不再参与比较
    @Test
    void testStrippingDownToEmptyStopsWithoutMatching() {
        assertFalse(StemMatches.matches("x", "_y.z"));
        assertFalse(StemMatches.matches("x", "."));
    }

    // ---------- 命中 ----------

    // stem 整体与 modId 相等（末段扩展名已由调用方剥掉）
    @Test
    void testStemEqualsModId() {
        assertTrue(StemMatches.matches("a", "a.b"));
        assertTrue(StemMatches.matches("sodium", "sodium"));
    }

    // modId 与 stem 两侧都大小写不敏感
    @Test
    void testCaseInsensitive() {
        assertTrue(StemMatches.matches("Sodium", "sodium-extra"));
        assertTrue(StemMatches.matches("sodium", "SODIUM-EXTRA"));
        assertTrue(StemMatches.matches("foo-bar", "FOO-BAR"));
    }

    // - _ . 三种分隔符同等对待
    @Test
    void testAllSeparatorsStripEqually() {
        assertTrue(StemMatches.matches("sodium", "sodium-extra"));
        assertTrue(StemMatches.matches("sodium", "sodium_extra"));
        assertTrue(StemMatches.matches("sodium", "sodium.extra"));
    }

    // 逐级剥离：剥到哪一级相等都算命中
    @Test
    void testStripsSegmentBySegment() {
        assertTrue(StemMatches.matches("foo-bar", "foo-bar-baz"));
        assertTrue(StemMatches.matches("foo", "foo-bar-baz"));
        assertTrue(StemMatches.matches("ferritecore", "ferritecore.mixin.properties"));
    }

    // ---------- 不命中 ----------

    // 只有剥出来的前缀参与比较：中间段与末段都不算
    @Test
    void testInnerAndTrailingSegmentsNeverMatch() {
        assertFalse(StemMatches.matches("bar", "foo-bar-baz"));
        assertFalse(StemMatches.matches("baz", "foo-bar-baz"));
        assertFalse(StemMatches.matches("mixin", "ferritecore.mixin"));
    }

    // 没有分隔符时只比整体，不做前缀匹配
    @Test
    void testWithoutSeparatorOnlyWholeStemCounts() {
        assertFalse(StemMatches.matches("sodium", "sodiumextra"));
        assertFalse(StemMatches.matches("sodiumextra", "sodium"));
    }

    // modId 比 stem 长时不匹配：modId 不能跨段拼出来
    @Test
    void testLongerModIdNeverMatchesShorterStem() {
        assertFalse(StemMatches.matches("foo-bar", "foo"));
        assertFalse(StemMatches.matches("sodium", "sod"));
    }
}
