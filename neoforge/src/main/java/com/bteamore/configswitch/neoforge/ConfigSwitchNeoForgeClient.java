package com.bteamore.configswitch.neoforge;

import com.bteamore.configswitch.Constants;
import com.bteamore.configswitch.client.gui.ConfigScreen;
import com.bteamore.configswitch.client.gui.TitleScreenButton;
import com.bteamore.configswitch.client.gui.widget.ConfigIconButton;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public class ConfigSwitchNeoForgeClient {
    private static KeyMapping openConfigScreen;

    public ConfigSwitchNeoForgeClient(IEventBus modEventBus, ModContainer container) {
        // 加载期注册：RegisterKeyMappingsEvent 是 IModBusEvent，只在客户端 fire
        modEventBus.addListener(ConfigSwitchNeoForgeClient::onRegisterKeyMappings);

        // 运行期事件：tick 与 screen 都在游戏总线
        NeoForge.EVENT_BUS.addListener(ConfigSwitchNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(ConfigSwitchNeoForgeClient::onScreenInit);

        container.registerExtensionPoint(IConfigScreenFactory.class,
            (modContainer, screen) -> new ConfigScreen(screen));
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 默认不指定
        openConfigScreen = new KeyMapping(
                Constants.KEY_OPEN_SCREEN,
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                Constants.KEY_CATEGORY);
        event.register(openConfigScreen);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (openConfigScreen.consumeClick()) {
            minecraft.setScreen(new ConfigScreen(minecraft.screen));
        }
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        ConfigIconButton button = TitleScreenButton.create(event.getScreen());
        if (button != null) {
            event.addListener(button);
        }
    }
}
