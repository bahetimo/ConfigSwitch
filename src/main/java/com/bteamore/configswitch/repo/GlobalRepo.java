package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.Configswitch;

import java.io.IOException;
import java.nio.file.Files;

public class GlobalRepo {
    public static void init() {
        try {
            if (Files.notExists(RepoPaths.commonDir())) {
                Files.createDirectories(RepoPaths.commonDir());
            }
        } catch (IOException e) {
            Configswitch.LOGGER.error("全局配置创建失败");
        }
    }
}
