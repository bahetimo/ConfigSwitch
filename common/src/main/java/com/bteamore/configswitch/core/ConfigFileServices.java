package com.bteamore.configswitch.core;

public final class ConfigFileServices {
    private static IConfigFileService service;

    private ConfigFileServices() {
    }

    public static void register(IConfigFileService service) {
        if (ConfigFileServices.service == null) {
            ConfigFileServices.service = service;
        }
    }

    public static IConfigFileService get() {
        return service;
    }
}
