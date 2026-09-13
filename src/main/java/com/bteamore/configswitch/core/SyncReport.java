package com.bteamore.configswitch.core;

import java.util.List;
import java.util.Map;

public record SyncReport(Map<String, SyncOutcome> results) {
    public boolean hasFailed() {
        return results.containsValue(SyncOutcome.FAILED);
    }

    public int count(SyncOutcome outcome) {
        return (int) results.values().stream().filter(outcome::equals).count();
    }

    public List<String> failedModIds() {
        return results.entrySet().stream()
                .filter(entry -> entry.getValue() == SyncOutcome.FAILED)
                .map(Map.Entry::getKey)
                .toList();
    }
}
