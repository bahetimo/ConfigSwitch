package com.bteamore.configswitch.client.gui.widget;

import com.bteamore.configswitch.client.gui.widget.entries.BackupEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.function.Consumer;

public class BackupListWidget extends ElementListWidget<BackupEntry> {
    public BackupListWidget(MinecraftClient minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
        // 条目从顶部开始排列，不随内容高度垂直居中
        this.centerListVertically = false;
    }

    public void addBackup(String timestamp, int fileCount, Consumer<String> onRestore) {
        this.addEntry(new BackupEntry(timestamp, fileCount, onRestore));
    }

    // 切换来源时清空重载
    public void clearBackups() {
        this.clearEntries();
    }

    @Override
    public int getRowWidth() {
        return this.width - (this.isScrollbarVisible() ? 38 : 32);
    }

    @Override
    protected int getScrollbarX() {
        return this.getRight() - 6;
    }
}
