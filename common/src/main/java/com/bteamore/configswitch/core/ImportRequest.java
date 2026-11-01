package com.bteamore.configswitch.core;

import java.nio.file.Path;
import java.util.function.Consumer;

public record ImportRequest(Path repoRoot, Path mappingsFile, Path input, Path backupRoot, Consumer<PackReport> onResult) {
}
