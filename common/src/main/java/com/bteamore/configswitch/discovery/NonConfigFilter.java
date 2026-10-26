package com.bteamore.configswitch.discovery;

import java.util.Set;
import java.util.regex.Pattern;

public final class NonConfigFilter {
    private NonConfigFilter() {
    }
    private static final Set<String> BLACKLIST = Set.of("world", "cache", "saves", "logs", "backup", "backups", "export", "auth", "temp", "tmp");
    private static final Set<String> CONTAIN_BLACKLIST = Set.of("_per_world", "_per_server");
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{16}$");

    // 相对 config/ 的路径
    public static boolean isSkipped(String relativePath) {
        String[] parts = relativePath.split("/");
        for (String part : parts) {
            part = part.toLowerCase();
            if (BLACKLIST.contains(part)) {
                return true;
            }
            if (CONTAIN_BLACKLIST.stream().anyMatch(part::contains)) {
                return true;
            }
            if (HASH_PATTERN.matcher(part).matches()) {
                return true;
            }
        }
        return false;
    }
}
