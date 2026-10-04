package com.bteamore.configswitch.discovery;

import java.util.List;

public record SubdirReport(List<Entry> entries) {
    public record Entry(String relativePath, int fileCount, Verdict verdict, Reason reason, String detail) {}

    public enum Verdict {ADOPTED, SKIPPED, UNRECOGNIZED}

    public enum Reason {BLACKLIST, FLAT_FOUND, PARENT_FLAVOR, PARENT_SUBDIR, FLAVOR, SUBDIR, NONE}
}
