package com.bteamore.configswitch.platform;

import java.util.Objects;

public final class Platforms {
    private static volatile Platform instance;

    private Platforms() {}

    public static void set(Platform platform) {
        Objects.requireNonNull(platform, "platform");
        Platform current = instance;
        if (current != null) {
            throw new IllegalStateException("Platform already set to "
                + current.getClass().getName()
                + ", this attempt came from " + platform.getClass().getName()
                + ": check for duplicate initialization in loader entrypoints.");
        }
        instance = platform;
    }

    public static Platform get() {
        Platform current = instance;
        if (current == null) {
            throw new IllegalStateException("Platform not initialized: every loader entrypoint must call "
                + "Platforms.set(new XxxPlatform()) before touching common business code.");
        }
        return current;
    }

    public static boolean isSet() {
        return instance != null;
    }
}
