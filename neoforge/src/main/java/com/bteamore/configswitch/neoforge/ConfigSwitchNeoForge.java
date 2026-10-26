package com.bteamore.configswitch.neoforge;

import com.bteamore.configswitch.CommonBootstrap;
import com.bteamore.configswitch.Constants;
import com.bteamore.configswitch.platform.NeoForgePlatform;
import com.bteamore.configswitch.platform.Platforms;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class ConfigSwitchNeoForge {

    public ConfigSwitchNeoForge(IEventBus eventBus) {
        Platforms.set(new NeoForgePlatform());
        CommonBootstrap.init();
    }
}
