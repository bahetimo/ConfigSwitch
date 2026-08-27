package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.Configswitch;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 24;
    private static final int TITLE_Y = 40;


    public ConfigScreen() {
        super(Text.literal("Config Screen"));
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = this.height / 2 - BUTTON_SPACING;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Switch"), btn -> onPress("switch"))
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        y += BUTTON_SPACING;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Overlay"), btn -> onPress("overlay"))
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        y += BUTTON_SPACING;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Sync"), btn -> onPress("sync"))
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void onPress(String name) {
        Configswitch.LOGGER.info("{}按键被按下", name);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
    }
}
