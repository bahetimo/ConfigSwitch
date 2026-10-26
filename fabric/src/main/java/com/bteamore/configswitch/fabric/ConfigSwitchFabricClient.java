package com.bteamore.configswitch.fabric;

import com.bteamore.configswitch.Constants;
import com.bteamore.configswitch.client.gui.ConfigScreen;
import com.bteamore.configswitch.client.gui.TitleScreenButton;
import com.bteamore.configswitch.client.gui.widget.ConfigIconButton;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ConfigSwitchFabricClient implements ClientModInitializer {
    private static KeyMapping openConfigScreen;

    @Override
    public void onInitializeClient() {
        // 默认不指定
        openConfigScreen = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                Constants.KEY_OPEN_SCREEN,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                Constants.KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigScreen.consumeClick()) {
                client.setScreen(new ConfigScreen(client.screen));
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ConfigIconButton button = TitleScreenButton.create(screen);
            if (button != null) {
                Screens.getButtons(screen).add(button);
            }
        });
    }
}
