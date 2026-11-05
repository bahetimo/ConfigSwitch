package com.bteamore.configswitch.neoforge;

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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

public final class ConfigSwitchNeoForgeClient {
    private static KeyMapping openConfigScreen;

    private ConfigSwitchNeoForgeClient() {
    }

    public static void init() {
        // 加载期注册：RegisterKeyMappingsEvent 是 IModBusEvent，只在客户端 fire
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        eventBus.addListener(ConfigSwitchNeoForgeClient::onRegisterKeyMappings);

        // 运行期事件：tick 与 screen 都在 Forge 总线
        MinecraftForge.EVENT_BUS.addListener(ConfigSwitchNeoForgeClient::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ConfigSwitchNeoForgeClient::onScreenInit);

        ModLoadingContext.get().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, screen) -> new ConfigScreen(screen))
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

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
