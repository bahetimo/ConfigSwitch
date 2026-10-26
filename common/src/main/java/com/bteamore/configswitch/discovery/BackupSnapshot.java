package com.bteamore.configswitch.discovery;

import java.nio.file.Path;
import java.util.List;

public record BackupSnapshot(String timeStamp, List<Path> relativePaths) {
}
