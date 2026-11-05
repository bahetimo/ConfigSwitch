package com.bteamore.configswitch.neoforge;

import com.bteamore.configswitch.CommonBootstrap;
import com.bteamore.configswitch.Constants;
import com.bteamore.configswitch.platform.NeoForgePlatform;
import com.bteamore.configswitch.platform.Platforms;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class ConfigSwitchNeoForge {

    public ConfigSwitchNeoForge() {
        Platforms.set(new NeoForgePlatform());
        CommonBootstrap.init();

        // 只有客户端才解析并加载 ConfigSwitchNeoForgeClient
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ConfigSwitchNeoForgeClient.init();
        }
    }
}
