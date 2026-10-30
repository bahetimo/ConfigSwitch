package com.bteamore.configswitch.core;

import com.bteamore.configswitch.discovery.BackupSnapshot;
import com.bteamore.configswitch.repo.ConfigPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public interface IConfigFileService {
    SyncReport pushActiveToGlobal(List<ConfigPaths> paths);
    SyncReport fetchGlobalToActive(List<ConfigPaths> paths);  // global -> active
    SyncReport restore( BackupSnapshot snapshot, Function<Path, Path> targetResolver, Path backupRoot);
    PackReport exportRepo(Path root, Path output, String version);
    PackReport importRepo(Path root, Path input, Path backupRoot);
}
