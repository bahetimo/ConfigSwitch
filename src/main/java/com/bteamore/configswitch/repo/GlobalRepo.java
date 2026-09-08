package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.Configswitch;

import java.io.IOException;
import java.nio.file.Files;

public class GlobalRepo {
    public static void init() {
        try {
            if (Files.notExists(RepoPaths.COMMON_DIR)) {
                Files.createDirectories(RepoPaths.COMMON_DIR);
            }
        } catch (IOException e) {
            Configswitch.LOGGER.error("全局配置创建失败");
        }
    }
}
