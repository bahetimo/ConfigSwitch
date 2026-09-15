package com.bteamore.configswitch.core;

import com.bteamore.configswitch.discovery.BackupSnapshot;
import com.bteamore.configswitch.repo.ConfigPaths;

import java.nio.file.Path;
import java.util.List;

public interface IConfigFileService {
    SyncReport pushActiveToGlobal(List<ConfigPaths> paths);
    SyncReport fetchGlobalToActive(List<ConfigPaths> paths);  // global -> active
    SyncReport restore( BackupSnapshot snapshot, Path targetRoot, Path backupRoot);
}
