package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.Configswitch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GlobalRepo {
    public static void init() {
        Path commonDir = ConfigTargets.getTarget().globalFile();
        try {
            if (Files.notExists(commonDir)) {
                Files.createDirectories(commonDir);
            }
        } catch (IOException e) {
            Configswitch.LOGGER.error("全局配置创建失败");
        }
    }
}
