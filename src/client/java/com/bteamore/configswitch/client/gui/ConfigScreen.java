package com.bteamore.configswitch.client.gui;

import com.bteamore.configswitch.Configswitch;
import com.bteamore.configswitch.client.ConfigDiscovery;
import com.bteamore.configswitch.discovery.ModGroup;
import com.bteamore.configswitch.manager.StateManager;
import com.bteamore.configswitch.repo.ConfigPathResolver;
import com.bteamore.configswitch.repo.ConfigPaths;
import com.bteamore.configswitch.util.Time;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

// 临时screen，后续会改
public class ConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 24;
    private static final int TITLE_Y = 40;

    private final Path gameDir = MinecraftClient.getInstance().runDirectory.toPath();

    private final StateManager stateManager = StateManager.getInstance();
    private final ConfigDiscovery discovery = new ConfigDiscovery(gameDir);
    private final ConfigPathResolver resolver = new ConfigPathResolver(gameDir);

    public ConfigScreen() {
        super(Text.literal("Config Screen"));
    }

    @Override
    protected void init() {
        int x = (this.width - BUTTON_WIDTH) / 2;
        int y = this.height / 2 - (BUTTON_HEIGHT + BUTTON_SPACING) / 2;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Fetch"), btn -> {
                    onPress("fetch");
                    MinecraftClient.getInstance().options.load();
                })
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        y += BUTTON_SPACING;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Push"), btn -> onPress("push"))
                .dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void onPress(String name) {
        List<ModGroup> groups = discovery.discover(name);
        groups.forEach(group -> Configswitch.LOGGER.debug("Found mod ID: {}", group.getModId()));

        String timeStamp = Time.timeString();

        List<ConfigPaths> configPaths = groups.stream()
                .flatMap(group -> group.getFiles().stream()
                        .map(path -> resolver.resolve(group.getModId(), path.getFileName().toString(), timeStamp, name)))
                .filter(Objects::nonNull)
                .toList();

        stateManager.transition(name, configPaths);
        Configswitch.LOGGER.info("{}按键被按下", name);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, TITLE_Y, 0xFFFFFF);
        // 暂时显示状态机的当前状态
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("当前状态：" + stateManager.getCurrentState().name()), this.width / 2, TITLE_Y + 20, 0xAAAAAA);
    }
}
