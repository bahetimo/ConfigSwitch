package com.bteamore.configswitch.core;

import java.nio.file.Path;
import java.util.function.Consumer;

public record ExportRequest(Path repoRoot, Path mappingsFile, Path output, String version, Consumer<PackReport> onResult) {
}
