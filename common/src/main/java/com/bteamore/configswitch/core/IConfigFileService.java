package com.bteamore.configswitch.core;

import com.bteamore.configswitch.discovery.BackupSnapshot;
import com.bteamore.configswitch.repo.ConfigPaths;

import java.nio.file.Path;
import java.util.List;

public interface IConfigFileService {
    SyncReport pushActiveToGlobal(List<ConfigPaths> paths);
    SyncReport fetchGlobalToActive(List<ConfigPaths> paths);  // global -> active
    SyncReport restore(BackupSnapshot snapshot, RestoreTarget restoreTarget, Path backupRoot);
    PackReport exportRepo(Path root, Path output, String version);
    PackReport importRepo(Path root, Path input, Path backupRoot);
}
