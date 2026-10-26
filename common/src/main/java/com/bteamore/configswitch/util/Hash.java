package com.bteamore.configswitch.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Hash {
    public static int hashOf(Path active) {
        try {
            return Arrays.hashCode(Files.readAllBytes(active));
        } catch (IOException e) {
            Log.error("Failed to hash {}", active, e);
            return 0;
        }
    }
}
