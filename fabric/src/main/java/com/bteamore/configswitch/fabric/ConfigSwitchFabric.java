package com.bteamore.configswitch.fabric;

import com.bteamore.configswitch.CommonBootstrap;
import com.bteamore.configswitch.platform.FabricPlatform;
import com.bteamore.configswitch.platform.Platforms;
import net.fabricmc.api.ModInitializer;

public class ConfigSwitchFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Platforms.set(new FabricPlatform());
        CommonBootstrap.init();
    }
}
