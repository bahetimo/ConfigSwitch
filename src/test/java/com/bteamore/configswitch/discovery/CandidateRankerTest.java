package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CandidateRankerTest {

    // 排序结果只取 modId 顺序：断言读起来就是"谁在前"
    private static List<String> order(String stem, List<ModCandidate> candidates) {
        return CandidateRanker.rank(stem, candidates).stream().map(ModCandidate::modId).toList();
    }

    private static List<String> order(String stem, ModCandidate... candidates) {
        return order(stem, List.of(candidates));
    }

    // ---------- 真实案例 ----------

    // 真实：yacl.json5 → YACL 排第一（acronym 90）；对手 modId 更长，靠长度是赢不过它的
    @Test
    void testYaclAcronymRanksFirst() {
        ModCandidate yacl = new ModCandidate("yet_another_config_lib_v3", "Yet Another Config Lib");
        ModCandidate longer = new ModCandidate("yet_another_config_lib_v3_compat", "Compat Layer");

        assertEquals(List.of("yet_another_config_lib_v3", "yet_another_config_lib_v3_compat"),
                order("yacl", yacl, longer));
    }

    // 真实：etf_warnings → Entity Texture Features 排第一（acronym 90 压过 modid 前缀 80）
    @Test
    void testEtfWarningsAcronymRanksFirst() {
        ModCandidate etf = new ModCandidate("entity_texture_features", "Entity Texture Features");
        ModCandidate prefixHit = new ModCandidate("etf", "Simple Test Mod");

        assertEquals(List.of("entity_texture_features", "etf"), order("etf_warnings", etf, prefixHit));
    }

    // ---------- 冲突与稳定性 ----------

    // 必经的冲突：两条都前缀命中（80），长的 modId 在前
    @Test
    void testLongerModIdWinsTie() {
        ModCandidate sodium = new ModCandidate("sodium", "Sodium");
        ModCandidate sodiumExtra = new ModCandidate("sodium-extra", "Sodium Extra");

        assertEquals(List.of("sodium-extra", "sodium"), order("sodium-extra-options", sodium, sodiumExtra));
    }

    // 稳定性：同一输入跑两次顺序完全相同；换输入的排列也不该改变结果
    // （比较器不完备时 Java 的稳定排序会漏出输入顺序，这一条才是有牙的那条）
    @Test
    void testRankIsStableAndInputOrderIndependent() {
        String stem = "sodium-extra-options";
        List<ModCandidate> candidates = new ArrayList<>(List.of(
                new ModCandidate("sodium", "Sodium"),
                new ModCandidate("sodium-extra", "Sodium Extra"),
                new ModCandidate("entity_texture_features", "Entity Texture Features"),
                new ModCandidate("yet_another_config_lib_v3", "Yet Another Config Lib")));
        List<ModCandidate> snapshot = List.copyOf(candidates);

        List<String> first = order(stem, candidates);
        List<String> second = order(stem, candidates);
        List<String> reversedInput = order(stem, snapshot.reversed());

        assertEquals(first, second);
        assertEquals(first, reversedInput);
        assertEquals(snapshot, candidates);   // 排序不改动输入列表
    }

    // ---------- 不过滤 ----------

    // 一个都匹配不上时仍然全部返回，只是分都是 0（顺序退化成 长度降序 → 字母序）
    @Test
    void testNoMatchReturnsAllCandidatesWithZeroScore() {
        ModCandidate sodium = new ModCandidate("sodium", "Sodium");
        ModCandidate etf = new ModCandidate("entity_texture_features", "Entity Texture Features");
        ModCandidate yacl = new ModCandidate("yet_another_config_lib_v3", "Yet Another Config Lib");

        assertEquals(List.of("yet_another_config_lib_v3", "entity_texture_features", "sodium"),
                order("zzzz", sodium, etf, yacl));
    }

    // ---------- acronym 边界 ----------

    // 多词：每个词取首字母 → Config Switch 是 CS
    @Test
    void testAcronymFromMultipleWords() {
        ModCandidate cs = new ModCandidate("config_switch", "Config Switch");
        ModCandidate longer = new ModCandidate("config_switch_extra", "Whatever Name");

        assertEquals(List.of("config_switch", "config_switch_extra"), order("cs", cs, longer));
    }

    // 单字作废：Sodium 只有首字母 "S"，长度 < 2 直接作废，stem "s" 时拿不到 90
    // （zz_token_anchor 是校准用的：displayName 有词等于 stem，稳稳 70 分）
    @Test
    void testSingleWordAcronymIsDiscarded() {
        ModCandidate sodium = new ModCandidate("sodium", "Sodium");
        ModCandidate tokenHit = new ModCandidate("zz_token_anchor", "s");

        assertEquals(List.of("zz_token_anchor", "sodium"), order("s", sodium, tokenHit));
    }

    // 括号：[...]、(...) 连内容整段丢掉 —— "Entity Texture Features [ETF] (Fabric)" 的 acronym 仍是 ETF
    @Test
    void testBracketContentIsDropped() {
        ModCandidate etf = new ModCandidate("entity_texture_features", "Entity Texture Features [ETF] (Fabric)");
        ModCandidate longer = new ModCandidate("entity_texture_features_compat", "Whatever Name");

        assertEquals(List.of("entity_texture_features", "entity_texture_features_compat"),
                order("etf", etf, longer));
    }

    // 撇号：撇号也是切词符 —— Farmer's Delight 切出 Farmer / s / Delight，acronym 是 FSD（撇号后的 s 也计入）
    @Test
    void testApostropheSplitsWord() {
        ModCandidate fd = new ModCandidate("farmers_delight", "Farmer's Delight");
        ModCandidate longer = new ModCandidate("farmers_delight_compat", "Whatever Name");

        assertEquals(List.of("farmers_delight", "farmers_delight_compat"), order("fsd", fd, longer));
    }

    // 数字词：数字也是词 —— Config Switch 2 的 acronym 是 CS2
    @Test
    void testDigitWordJoinsAcronym() {
        ModCandidate cs2 = new ModCandidate("config_switch2", "Config Switch 2");
        ModCandidate longer = new ModCandidate("config_switch2_extra", "Whatever Name");

        assertEquals(List.of("config_switch2", "config_switch2_extra"), order("cs2", cs2, longer));
    }

    // 空：displayName 是空串时不生成 acronym，该候选只能靠别的路径拿分
    @Test
    void testBlankDisplayNameProducesNoAcronym() {
        ModCandidate blank = new ModCandidate("sodium_extra", "");
        ModCandidate tokenHit = new ModCandidate("sodium_tools", "Sodium Tools");

        assertEquals(List.of("sodium_tools", "sodium_extra"), order("sodium", blank, tokenHit));
    }

    // 空：displayName 为 null 时也不该抛 —— acronym 有 null 守卫，token 这条路径得一样容忍
    // 现状：score() 里的 tokens() 直接对 name 调 replaceAll → NPE，所以这条现在是红的
    @Test
    void testNullDisplayNameDoesNotThrow() {
        ModCandidate nullName = new ModCandidate("sodium_extra", null);
        ModCandidate tokenHit = new ModCandidate("sodium_tools", "Sodium Tools");

        List<String> ranked = assertDoesNotThrow(
                (ThrowingSupplier<List<String>>) () -> order("sodium", nullName, tokenHit));

        assertEquals(2, ranked.size());
        assertEquals(List.of("sodium_tools", "sodium_extra"), ranked);
    }
}
