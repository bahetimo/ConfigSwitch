package com.bteamore.configswitch.discovery;


import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class CandidateRanker {
    // 分数降序 -> modId.length() 降序 -> modId 字母序
    public static List<ModCandidate> rank(String stem, List<ModCandidate> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingInt((ModCandidate c) -> score(stem, c)).reversed()
                        .thenComparing(Comparator.comparingInt((ModCandidate c) -> c.modId().length()).reversed())
                        .thenComparing(ModCandidate::modId))
                .toList();
    }

    // 计算候选 mod 的分数
    // 1. modid 匹配 -> 100
    // 2. acronym 命中 -> 90
    // 3. modid 前缀命中 -> 80
    // 4. displayName 存在词命中 -> 70
    // 其他 -> 0
    private static int score(String stem, ModCandidate candidate) {
        if (candidate.displayName() == null || candidate.displayName().isEmpty()){
            return 0;
        }

        int score = 0;
        if (candidate.modId().equalsIgnoreCase(stem)) {
            return 100;
        }
        if (StemMatches.matches(acronym(candidate.displayName()), stem)) {
            return 90;
        }
        if (StemMatches.matches(candidate.modId(), stem)) {
            return 80;
        }
        for (String token : tokens(candidate.displayName())) {
            if (token.equalsIgnoreCase(stem)) {
                return 70;
            }
        }

        return 0;
    }

    private static String[] tokens(String name) {
        if (name == null || name.isEmpty()) {
            return new String[0];
        }
        return Arrays.stream(name.replaceAll("[(\\[{].*?[)\\]}]", "")
                .replaceAll("\\s+", " ")
                .trim()
                .split("[^a-zA-Z0-9]"))
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    // config_switch,ConfigSwitch,Config Switch,config switch,CONFIG_SWITCH,Config-Switch,config-switch -> CS
    // 1. 去掉所有 (...)、[...]、{...} 及其内容（(Fabric) (1.21.1) [XX]）
    // 2. 按【非字母数字】切词（空格 - _ ' . 等）
    // 3. 丢掉空词
    // 4. 每个词取首字符，转大写
    // 5. 结果长度 < 2，返回 ""（作废，不参与打分）
    private static String acronym(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }

        String[] split = tokens(name);

        String acron = Arrays.stream(split)
                .map(s -> s.substring(0, 1).toUpperCase())
                .collect(Collectors.joining());

        return acron.length() < 2 ? "" : acron;
    }
}
