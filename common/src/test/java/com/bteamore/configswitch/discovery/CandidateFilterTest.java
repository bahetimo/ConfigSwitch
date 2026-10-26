package com.bteamore.configswitch.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CandidateFilterTest {

    // ---------- 放行：普通 mod ----------

    // 普通 modid 都是候选（入参是 FabricLoader 的 ModMetadata.getId()）
    @Test
    void testNormalModIdsAreCandidates() {
        assertTrue(CandidateFilter.isCandidate("sodium"));
        assertTrue(CandidateFilter.isCandidate("yet_another_config_lib_v3"));
        assertTrue(CandidateFilter.isCandidate("xaeros_minimap"));
    }

    // ---------- 排除：fabric- 前缀 ----------

    // fabric- 开头的整族都排除（Fabric API 的各子模块就是这个形状）
    @Test
    void testFabricPrefixedModIdsAreExcluded() {
        assertFalse(CandidateFilter.isCandidate("fabric-api"));
        assertFalse(CandidateFilter.isCandidate("fabric-api-base"));
        assertFalse(CandidateFilter.isCandidate("fabric-lifecycle-events-v1"));
    }

    // 前缀看的是开头：fabric- 出现在中间照常放行
    // （写成 contains 的话这条会红）
    @Test
    void testFabricInMiddleIsAllowed() {
        assertTrue(CandidateFilter.isCandidate("my-fabric-mod"));
    }

    // ---------- 排除：原版与自身 ----------

    // 内置 modid 与自身 modid 排除
    // （fabricloader 不落在 fabric- 前缀里，是靠精确排除接住的）
    @Test
    void testBuiltInAndOwnModIdsAreExcluded() {
        assertFalse(CandidateFilter.isCandidate("java"));
        assertFalse(CandidateFilter.isCandidate("minecraft"));
        assertFalse(CandidateFilter.isCandidate("fabricloader"));
        assertFalse(CandidateFilter.isCandidate("configswitch"));
    }

    // 排除项是精确相等而不是前缀：名字以它们开头的普通 mod 照常放行
    // （写成 startsWith 的话这条会红）
    @Test
    void testBuiltInNamesMustMatchExactly() {
        assertTrue(CandidateFilter.isCandidate("configswitch-compat"));
        assertTrue(CandidateFilter.isCandidate("minecraft_extras"));
    }

    // 紧贴前缀同样不算命中：javafoo 从 "java" 开头、但也不是 "java" 本身 → 照常是候选
    // （equals 写成 startsWith 的话 javafoo / minecraftbetter 会当场被误杀）
    @Test
    void testReservedNameAsPrefixIsAllowed() {
        assertTrue(CandidateFilter.isCandidate("javafoo"));
        assertTrue(CandidateFilter.isCandidate("minecraftbetter"));
        assertTrue(CandidateFilter.isCandidate("fabricloader2"));
        assertTrue(CandidateFilter.isCandidate("configswitchcustom"));
    }
}
