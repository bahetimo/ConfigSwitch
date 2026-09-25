package com.bteamore.configswitch;

import com.bteamore.configswitch.config.ModConfig;
import com.bteamore.configswitch.manager.PendingRewrites;
import com.bteamore.configswitch.repo.GlobalRepo;
import com.bteamore.configswitch.util.Log;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;


public class Configswitch implements ModInitializer {

    @Override
    public void onInitialize() {
        Log.init(FabricLoader.getInstance().getGameDir().resolve("logs"));
        ModConfig.load();
        GlobalRepo.init();
        Log.info("ConfigSwitch {} loaded (Minecraft {}, Fabric Loader {})", versionOf("configswitch"), versionOf("minecraft"), versionOf("fabricloader"));
        Log.debug("modIds: {}", FabricLoader.getInstance().getAllMods().stream().map(m -> m.getMetadata().getId()).toList());

        Runtime.getRuntime().addShutdownHook(new Thread(PendingRewrites::flush, "configswitch-shutdown"));
    }

    private static String versionOf(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
