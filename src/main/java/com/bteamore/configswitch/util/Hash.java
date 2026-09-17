package com.bteamore.configswitch.util;

import com.bteamore.configswitch.Configswitch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Hash {
    public static int hashOf(Path active) {
        try {
            return Arrays.hashCode(Files.readAllBytes(active));
        } catch (IOException e) {
            Configswitch.LOGGER.error("Failed to hash {}: {}", active, e.getMessage());
            return 0;
        }
    }
}
