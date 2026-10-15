package com.bteamore.configswitch.client;

import com.bteamore.configswitch.client.gui.ConfigScreen;
import com.bteamore.configswitch.client.gui.widget.ConfigIconButton;
import com.bteamore.configswitch.config.ModConfig;
import com.bteamore.configswitch.core.ConfigFileServices;
import com.bteamore.configswitch.manager.ConfigHandler;
import com.bteamore.configswitch.util.Log;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ConfigswitchClient implements ClientModInitializer {
    private static final String LANGUAGE_BUTTON_KEY = "options.language";
    private static final String SINGLEPLAYER_BUTTON_KEY = "menu.singleplayer";
    private static final String MULTIPLAYER_BUTTON_KEY = "menu.multiplayer";

    private static KeyBinding openConfigScreen;

    @Override
    public void onInitializeClient() {
        ConfigFileServices.register(new ConfigHandler(ModConfig.settings().maxBackupCount()));

        openConfigScreen = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.configswitch.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                "category.configswitch"
        ));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> addTitleScreenButton(client, screen));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigScreen.wasPressed()) {
                client.setScreen(new ConfigScreen(client.currentScreen));
            }
        });
    }

    // 语言按钮正上方放一个图标按钮；找不到语言按钮就默认主菜单左下角
    private static void addTitleScreenButton(MinecraftClient client, Screen screen) {
        if (!(screen instanceof TitleScreen)) return;

        ClickableWidget language = findButton(screen, LANGUAGE_BUTTON_KEY);
        int x;
        int y;
        if (language == null) {
            // 找不到语言按钮：退到左下角，位置可预期且不会压到按钮列
            Log.warn("Language button not found on the title screen, falling back to the bottom left corner");
            x = ConfigIconButton.MARGIN;
            y = ConfigIconButton.bottomLeftY(screen.height);
        } else {
            x = language.getX();
            y = ConfigIconButton.aboveY(language.getY(), titleScreenRowStep(screen));
        }

        Screens.getButtons(screen).add(new ConfigIconButton(x, y, ConfigIconButton.SIZE,
                () -> client.setScreen(new ConfigScreen(screen))));
    }

    // 行距照主菜单自己的单人游戏/多人游戏两行量，量不到再落回原版默认值
    private static int titleScreenRowStep(Screen screen) {
        ClickableWidget singleplayer = findButton(screen, SINGLEPLAYER_BUTTON_KEY);
        ClickableWidget multiplayer = findButton(screen, MULTIPLAYER_BUTTON_KEY);
        if (singleplayer == null || multiplayer == null) {
            Log.warn("Singleplayer/multiplayer rows not found on the title screen, using default row step {}", ConfigIconButton.DEFAULT_ROW_STEP);
            return ConfigIconButton.DEFAULT_ROW_STEP;
        }

        int step = ConfigIconButton.rowStep(singleplayer.getY(), multiplayer.getY());
        Log.debug("Title screen row step: {}", step);
        return step;
    }

    // 不用下标：其他模组也会往主菜单插按钮
    private static ClickableWidget findButton(Screen screen, String translationKey) {
        String label = Text.translatable(translationKey).getString();
        for (var widget : Screens.getButtons(screen)) {
            if (label.equals(widget.getMessage().getString())) {
                return widget;
            }
        }
        return null;
    }
}
