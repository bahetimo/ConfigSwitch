package com.bteamore.configswitch.core;

import java.nio.file.Path;

public record PackReport(outcome outcome, int fileCount, Path location) {
    public enum outcome { SUCCESS, NOT_A_PACKAGE, IO_FAILED, CORRUPTED }
}
