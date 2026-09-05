package com.bteamore.configswitch.core;

import com.bteamore.configswitch.repo.ConfigPaths;

import java.util.List;

public interface IConfigFileService {
    void pushActiveToGlobal(List<ConfigPaths> paths);
    void fetchGlobalToActive(List<ConfigPaths> paths);  // global -> active
}
