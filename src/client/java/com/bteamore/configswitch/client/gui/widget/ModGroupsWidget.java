package com.bteamore.configswitch.client.gui.widget;


import com.bteamore.configswitch.client.gui.widget.entries.ModGroupsEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ElementListWidget;

import java.util.List;
import java.util.function.BiConsumer;

public class ModGroupsWidget extends ElementListWidget<ModGroupsEntry> {
    public ModGroupsWidget(MinecraftClient minecraftClient, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
        // 条目从顶部开始排列，不随内容高度垂直居中
        this.centerListVertically = false;
    }

    public void addGroup(String modId, List<String> fileNames, boolean selected, BiConsumer<String, Boolean> onToggle) {
        this.addEntry(new ModGroupsEntry(modId, fileNames, selected, onToggle));
    }

    @Override
    public int getRowWidth() {
        // 默认行宽 220 会居中显示，这里改为占满列表宽度（左右各留少量内边距）
        return this.width - 20;
    }

    public void clearGroups() {
        this.clearEntries();
    }
}
