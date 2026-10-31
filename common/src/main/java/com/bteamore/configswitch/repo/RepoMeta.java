package com.bteamore.configswitch.repo;

import java.util.LinkedHashMap;
import java.util.Map;

public record RepoMeta(int format, Map<String, ModSource> mods) {
    public static final int FORMAT = 1;

    public RepoMeta {
        // 非正值说明调用方没声明版本，落回当前格式，避免写出自己都不认的文件
        if (format <= 0) format = FORMAT;
        mods = (mods == null) ? new LinkedHashMap<>() : new LinkedHashMap<>(mods);
    }

    public static RepoMeta empty() {
        return new RepoMeta(FORMAT, Map.of());
    }
}
