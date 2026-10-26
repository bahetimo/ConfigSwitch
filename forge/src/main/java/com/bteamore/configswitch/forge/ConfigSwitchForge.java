package com.bteamore.configswitch.forge;

import com.bteamore.configswitch.CommonBootstrap;
import com.bteamore.configswitch.Constants;
import com.bteamore.configswitch.platform.ForgePlatform;
import com.bteamore.configswitch.platform.Platforms;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(Constants.MOD_ID)
public class ConfigSwitchForge {

    public ConfigSwitchForge(FMLJavaModLoadingContext context) {
        Platforms.set(new ForgePlatform());
        CommonBootstrap.init();

        // 只有客户端才解析并加载 ConfigSwitchForgeClient
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ConfigSwitchForgeClient.init(context);
        }
    }
}
