package com.bteamore.configswitch.repo;

import java.nio.file.Path;

public record ConfigPaths(String relativePath, Path active, Path global, Path backup, Path backupRoot, String modId) {
}
