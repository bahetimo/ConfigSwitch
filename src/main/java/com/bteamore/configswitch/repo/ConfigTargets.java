package com.bteamore.configswitch.repo;

public final class ConfigTargets {
    public static IConfigTarget target;

    public static void register(IConfigTarget target) {
        if (ConfigTargets.target == null) {
            ConfigTargets.target = target;
        }
    }

    public static IConfigTarget get() {
        return target;
    }
}
