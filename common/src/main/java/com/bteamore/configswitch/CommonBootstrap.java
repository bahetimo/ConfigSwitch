package com.bteamore.configswitch;

import com.bteamore.configswitch.config.ModConfig;
import com.bteamore.configswitch.core.ConfigFileServices;
import com.bteamore.configswitch.discovery.MappingConfig;
import com.bteamore.configswitch.manager.ConfigHandler;
import com.bteamore.configswitch.manager.PendingRewrites;
import com.bteamore.configswitch.platform.Platform;
import com.bteamore.configswitch.platform.Platforms;
import com.bteamore.configswitch.repo.GlobalRepo;
import com.bteamore.configswitch.util.Log;

public final class CommonBootstrap {
    public static void init() {
        Platform platform = Platforms.get();

        Log.init(platform.gameDir().resolve("logs"));
        ModConfig.load();
        MappingConfig.injectSeed();
        GlobalRepo.init();
        ConfigFileServices.register(new ConfigHandler(ModConfig.settings().maxBackupCount()));

        Log.info("ConfigSwitch {} loaded (Minecraft {}, dev={})",
            platform.selfVersion(),
            platform.versionOf("minecraft").orElse("unknown"),
            platform.isDevelopmentEnv());
        Log.debug("modIds: {}", platform.modIds().stream().sorted().toList());

        Runtime.getRuntime().addShutdownHook(
            new Thread(PendingRewrites::flush, "configswitch-shutdown"));
    }
}
