package com.bteamore.configswitch.core;

import com.bteamore.configswitch.repo.ConfigPaths;

import java.util.List;

public interface IConfigFileService {
    void pushActiveToGlobal();   // 先备份 global,再 active -> global
    void pushActiveToGlobal(List<ConfigPaths> paths);
    void fetchGlobalToActive();  // global -> active
}
