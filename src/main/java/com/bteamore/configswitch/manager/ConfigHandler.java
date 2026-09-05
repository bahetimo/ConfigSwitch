package com.bteamore.configswitch.manager;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.core.IConfigFileService;
import com.bteamore.configswitch.repo.ConfigPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ConfigHandler implements IConfigFileService {
    @Override
    public void pushActiveToGlobal(List<ConfigPaths> paths) {
        for (ConfigPaths target : paths) {
            if (target == null) {
                Configswitch.LOGGER.error("Push failed - no config target registered");
                continue;
            }
            // 一致性规则:推送前先备份即将被覆盖的全局配置
            if (Files.exists(target.global())) {
                copy(target.global(), target.backup());
            }
            copy(target.active(), target.global());
            // 不加try-catch是因为copy和backup方法内部已经处理了异常，后面再处理
        }
    }

    @Override
    public void fetchGlobalToActive(List<ConfigPaths> paths) {
        for (ConfigPaths target : paths){
            if (target == null) {
                Configswitch.LOGGER.error("Fetch failed - no config target registered");
                continue;
            }
            if (!Files.exists(target.active())){
                continue;
            }
            copy(target.global(), target.active());
        }
    }

    private void copy(Path from, Path to) {
        try {
            if (to.getParent() != null) {
                Files.createDirectories(to.getParent());
            }
            if (Files.exists(to)) {
                Files.delete(to);
            }
            Files.copy(from, to);
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to copy {} -> {}: {}", from, to, e.getMessage());
        }
    }
}
