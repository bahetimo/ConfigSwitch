package com.bteamore.configswitch.repo;

import com.bteamore.configswitch.util.Log;

import java.io.IOException;
import java.nio.file.Files;

public class GlobalRepo {
    public static void init() {
        try {
            if (Files.notExists(RepoPaths.commonDir())) {
                Files.createDirectories(RepoPaths.commonDir());
            }
        } catch (IOException e) {
            Log.error("Global config initialization failed", e);
        }
    }
}
