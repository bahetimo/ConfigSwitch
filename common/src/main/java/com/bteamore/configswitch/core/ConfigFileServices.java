package com.bteamore.configswitch.core;

import java.util.Objects;

public final class ConfigFileServices {
    private static IConfigFileService service;

    private ConfigFileServices() {
    }

    public static void register(IConfigFileService service) {
        Objects.requireNonNull(service, "service");
        if (ConfigFileServices.service != null) {
            throw new IllegalStateException("Service already registered");
        }
        ConfigFileServices.service = service;
    }

    public static IConfigFileService get() {
        IConfigFileService current = service;
        if (current == null) {
            throw new IllegalStateException("ConfigFileServices not registered"
            + ", call register() first");
        }
        return current;
    }
}
