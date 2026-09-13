package com.bteamore.configswitch.repo;

import java.nio.file.Path;

public record ConfigPaths(String fileName, Path active, Path global, Path backup, Path backupRoot, String modId) {
}
