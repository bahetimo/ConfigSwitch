package com.bteamore.configswitch.client.gui.widget;

import com.bteamore.configswitch.client.gui.widget.entries.BackupEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.function.Consumer;

public class BackupListWidget extends ScrollableListWidget<BackupEntry> {
    public BackupListWidget(MinecraftClient minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
    }

    public void addBackup(String timestamp, int fileCount, Consumer<String> onRestore) {
        this.addEntry(new BackupEntry(timestamp, fileCount, onRestore));
    }

    // 切换来源时清空重载
    public void clearBackups() {
        this.clearEntries();
    }
}
