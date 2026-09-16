package com.bteamore.configswitch;

import com.bteamore.configswitch.config.ModConfig;
import com.bteamore.configswitch.repo.GlobalRepo;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Configswitch implements ModInitializer {
    public static final String MOD_ID = "configswitch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        GlobalRepo.init();
        LOGGER.info("ConfigSwitch loaded!");
    }
}
