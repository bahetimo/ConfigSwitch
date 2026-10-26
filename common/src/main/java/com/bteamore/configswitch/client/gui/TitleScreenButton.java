package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.client.gui.widget.ConfigIconButton;
import com.bteamore.configswitch.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public final class TitleScreenButton {
    private static final String LANGUAGE_BUTTON_KEY = "options.language";
    private static final String SINGLEPLAYER_BUTTON_KEY = "menu.singleplayer";
    private static final String MULTIPLAYER_BUTTON_KEY = "menu.multiplayer";

    private TitleScreenButton() {
    }

    public static ConfigIconButton create(Screen screen) {
        if (!(screen instanceof TitleScreen)) {
            return null;
        }

        AbstractWidget language = findButton(screen, LANGUAGE_BUTTON_KEY);
        int x;
        int y;
        if (language == null) {
            // 找不到语言按钮：退到左下角，位置可预期且不会压到按钮列
            Log.warn("Language button not found on the title screen, falling back to the bottom left corner");
            x = ConfigIconButton.MARGIN;
            y = ConfigIconButton.bottomLeftY(screen.height);
        } else {
            x = language.getX();
            y = ConfigIconButton.aboveY(language.getY(), rowStep(screen));
        }

        return new ConfigIconButton(x, y, ConfigIconButton.SIZE,
                () -> Minecraft.getInstance().setScreen(new ConfigScreen(screen)));
    }

    private static int rowStep(Screen screen) {
        AbstractWidget singleplayer = findButton(screen, SINGLEPLAYER_BUTTON_KEY);
        AbstractWidget multiplayer = findButton(screen, MULTIPLAYER_BUTTON_KEY);
        if (singleplayer == null || multiplayer == null) {
            Log.warn("Singleplayer/multiplayer rows not found on the title screen, using default row step {}",
                    ConfigIconButton.DEFAULT_ROW_STEP);
            return ConfigIconButton.DEFAULT_ROW_STEP;
        }

        int step = ConfigIconButton.rowStep(singleplayer.getY(), multiplayer.getY());
        Log.debug("Title screen row step: {}", step);
        return step;
    }

    private static AbstractWidget findButton(Screen screen, String translationKey) {
        String label = Component.translatable(translationKey).getString();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget
                    && label.equals(widget.getMessage().getString())) {
                return widget;
            }
        }
        return null;
    }
}
