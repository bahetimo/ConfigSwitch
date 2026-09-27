package com.bteamore.configswitch.discovery;

import java.util.Set;

public final class CandidateFilter {
    private static final String FABRIC_PREFIX = "fabric-";
    private static final Set<String> EXCLUDED_IDS = Set.of("java", "minecraft", "fabricloader", "configswitch");


    private CandidateFilter() {
    }

    public static boolean isCandidate(String modId) {
        return modId.startsWith(FABRIC_PREFIX) && !EXCLUDED_IDS.contains(modId);
    }
}
