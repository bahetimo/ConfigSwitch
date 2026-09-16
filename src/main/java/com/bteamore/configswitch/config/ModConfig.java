package com.bteamore.configswitch.config;

public final class ModConfig {
    private static final SettingsStore STORE = new SettingsStore(ModSettings.DEFAULT_ROOT.resolve("configswitch-config.json"));
    private static ModSettings settings = ModSettings.DEFAULT;


    public static ModSettings settings() {
        return settings;
    }

    public static void load() {
        ModSettings loaded = STORE.load();
        if (!ModSettings.isSyntacticallyValid(loaded)){
            ModSettings fixed = ModSettings.DEFAULT;
            STORE.save(fixed);
            loaded = fixed;
        }
        settings = loaded;
    }

    public static SettingsStore store() {
        return STORE;
    }

    private ModConfig() {
    }

}
