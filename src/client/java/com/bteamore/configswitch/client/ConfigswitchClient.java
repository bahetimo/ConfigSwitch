package com.bteamore.configswitch.client;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.client.gui.ConfigScreen;
import com.bteamore.configswitch.client.repo.OptionsTarget;
import com.bteamore.configswitch.core.ConfigFileServices;
import com.bteamore.configswitch.discovery.ModGroup;
import com.bteamore.configswitch.manager.ConfigHandler;
import com.bteamore.configswitch.repo.ConfigTargets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ConfigswitchClient implements ClientModInitializer {
    private static KeyBinding openConfigScreen;

    @Override
    public void onInitializeClient() {
        ConfigTargets.register(new OptionsTarget());
        ConfigFileServices.register(new ConfigHandler());

        openConfigScreen = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.configswitch.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.configswitch"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigScreen.wasPressed()) {
                client.setScreen(new ConfigScreen());
            }
        });

        // for (ModGroup group : new ConfigDiscovery().discover()) {
        //     Configswitch.LOGGER.info("[discovery] {} -> {}",group.getModId(), group.getFiles());
        // }
    }
}
