package com.bteamore.configswitch.forge;

import com.bteamore.configswitch.Constants;
import com.bteamore.configswitch.client.gui.ConfigScreen;
import com.bteamore.configswitch.client.gui.TitleScreenButton;
import com.bteamore.configswitch.client.gui.widget.ConfigIconButton;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

public final class ConfigSwitchForgeClient {
    private static KeyMapping openConfigScreen;

    private ConfigSwitchForgeClient() {
    }

    public static void init(FMLJavaModLoadingContext context) {
        // 加载期注册：RegisterKeyMappingsEvent 是 IModBusEvent，只在客户端 fire
        context.getModEventBus().addListener(ConfigSwitchForgeClient::onRegisterKeyMappings);

        // 运行期事件：tick 与 screen 都在 Forge 总线
        MinecraftForge.EVENT_BUS.addListener(ConfigSwitchForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ConfigSwitchForgeClient::onScreenInit);

        context.registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((screen) -> new ConfigScreen(screen))
        );
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

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
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
