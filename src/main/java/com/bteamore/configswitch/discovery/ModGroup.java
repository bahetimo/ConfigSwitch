package com.bteamore.configswitch.discovery;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置分组：一个 modid 对应一组配置文件
 * 保留 id：VANILLA_ID（原版 options.txt）、UNCATEGORIZED_ID（未分类）
 */
public class ModGroup {
    public static final String VANILLA_ID = "minecraft";
    public static final String UNCATEGORIZED_ID = "uncategorized";

    private final String modId;
    private final List<Path> files = new ArrayList<>();

    public ModGroup(String modId) {
        this.modId = modId;
    }

    public String getModId() {
        return modId;
    }

    public List<Path> getFiles() {
        return files;
    }

    public void addFile(Path file) {
        this.files.add(file);
    }
}
