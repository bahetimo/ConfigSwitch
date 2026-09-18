package com.bteamore.configswitch.client.integration;

import com.bteamore.configswitch.client.gui.SettingsScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SettingsScreen(Text.translatable("configswitch.screen.settings"), parent);
    }
}
