package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.ConfigState;
import com.bteamore.configswitch.repo.ConfigTargets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigHandler {
    // 文件操作的具体逻辑还没细化，这里只写个大概的思路代码
    private ConfigHandler() {
    }

    public static void store() {
        try {
            Files.copy(ConfigTargets.getTarget().activeFile(), ConfigTargets.getTarget().storeFile());
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to store config file: {}", e.getMessage());
        }
    }

    public static void restore(Path from, Path to) {
        try {
            if (Files.exists(to)) {
                Files.delete(to);
            }
            Files.copy(from, to);
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to restore config file: {}", e.getMessage());
        }
    }

    public static void backup(ConfigState source) {
        try {
            switch (source) {
                case LINKED:
                    Files.copy(ConfigTargets.getTarget().globalFile(), ConfigTargets.getTarget().backupFile());
                    break;
                case LOCAL:
                    Files.copy(ConfigTargets.getTarget().activeFile(), ConfigTargets.getTarget().backupFile());
                    break;
                case STORE:
                    Files.copy(ConfigTargets.getTarget().storeFile(), ConfigTargets.getTarget().backupFile());
                    break;
            }
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to backup config file: {}", e.getMessage());
        }
    }

    public static void overwrite(ConfigState source) {
        try {
            Path from;
            Path to;

            switch (source) {
                case PUSH:
                    from = ConfigTargets.getTarget().activeFile();
                    to = ConfigTargets.getTarget().globalFile();
                    break;
                case FETCH:
                    from = ConfigTargets.getTarget().globalFile();
                    to = ConfigTargets.getTarget().activeFile();
                    break;
                case STORE:
                    from = ConfigTargets.getTarget().storeFile();
                    to = ConfigTargets.getTarget().globalFile();
                    break;
                default:
                    return;
            }
            ;
            if (Files.exists(to)) {
                Files.delete(to);
            }
            Files.copy(from, to);
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to overwrite config file: {}", e.getMessage());
        }
    }
}
