package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.Configswitch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class GlobalRepo {
    private static final Path root = Path.of(System.getProperty("user.home"),"AppData", "Roaming", ".minecraft", "configswitch");
    private static final Path globalDir = Path.of(root.toString(), "global");
    private static final Path commonDir = Path.of(globalDir.toString(), "common");
    public static void init() {
        try {
            if (Files.notExists(commonDir)) {
                Files.createDirectories(commonDir);
            }
        } catch (IOException e) {
            Configswitch.LOGGER.error("全局配置创建失败");
        }
    }
}
