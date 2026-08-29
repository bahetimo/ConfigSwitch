package com.bteamore.configswitch.core;

public interface IConfigFileService {
    void pushActiveToGlobal();   // 先备份 global,再 active -> global
    void fetchGlobalToActive();  // global -> active
}
